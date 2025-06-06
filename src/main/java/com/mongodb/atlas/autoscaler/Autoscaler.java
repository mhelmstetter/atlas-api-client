package com.mongodb.atlas.autoscaler;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.clients.AtlasApiClient;

/**
 * MongoDB Atlas Autoscaler
 * 
 * Monitors Atlas cluster metrics and automatically scales clusters up or down
 * based on configurable rules and thresholds.
 */
public class Autoscaler {
    
    private static final Logger logger = LoggerFactory.getLogger(Autoscaler.class);
    
    // Configuration
    private final AutoscalerConfig config;
    private final AtlasApiClient apiClient;
    private final AtlasScalingClient scalingClient;
    private final ObjectMapper objectMapper;
    
    // State management
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Metrics tracking
    private final Map<String, ClusterMetrics> clusterMetricsHistory = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastScaleActions = new ConcurrentHashMap<>();
    
    // Cooldown tracking to prevent rapid scaling
    private static final Duration DEFAULT_SCALE_COOLDOWN = Duration.ofMinutes(30);
    
    public Autoscaler(AutoscalerConfig config, String apiPublicKey, String apiPrivateKey) {
        this.config = config;
        this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, 1);
        this.scalingClient = new AtlasScalingClient(apiPublicKey, apiPrivateKey);
        this.objectMapper = new ObjectMapper();
        
        logger.info("Initialized Autoscaler with {} projects, {} rules, monitoring interval: {} seconds",
                config.getProjectNames().size(), 
                config.getScalingRules().size(),
                config.getMonitoringIntervalSeconds());
    }
    
    /**
     * Start the autoscaler daemon
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting MongoDB Atlas Autoscaler daemon...");
            
            // Schedule the main monitoring loop
            scheduler.scheduleWithFixedDelay(
                this::monitorAndScale, 
                0, // Initial delay
                config.getMonitoringIntervalSeconds(), 
                TimeUnit.SECONDS
            );
            
            // Schedule periodic cleanup of old metrics data
            scheduler.scheduleWithFixedDelay(
                this::cleanupOldMetrics,
                Duration.ofHours(1).toSeconds(), // Initial delay
                Duration.ofHours(1).toSeconds(), // Period
                TimeUnit.SECONDS
            );
            
            logger.info("Autoscaler daemon started successfully");
        } else {
            logger.warn("Autoscaler is already running");
        }
    }
    
    /**
     * Stop the autoscaler daemon
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping MongoDB Atlas Autoscaler daemon...");
            
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Autoscaler daemon stopped");
        }
    }
    
    /**
     * Main monitoring and scaling logic
     */
    private void monitorAndScale() {
        if (!running.get()) {
            return;
        }
        
        try {
            logger.debug("Starting monitoring cycle...");
            
            // Get projects to monitor
            Map<String, String> projectMap = apiClient.clusters().getProjects(config.getProjectNames());
            
            for (Map.Entry<String, String> projectEntry : projectMap.entrySet()) {
                String projectName = projectEntry.getKey();
                String projectId = projectEntry.getValue();
                
                try {
                    monitorProject(projectName, projectId);
                } catch (Exception e) {
                    logger.error("Error monitoring project {}: {}", projectName, e.getMessage(), e);
                }
            }
            
            logger.debug("Monitoring cycle completed");
            
        } catch (Exception e) {
            logger.error("Error in monitoring cycle: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Monitor a specific project for scaling opportunities
     */
    private void monitorProject(String projectName, String projectId) {
        try {
            // Get all clusters in the project
            List<Map<String, Object>> clusters = apiClient.clusters().getClusters(projectId);
            
            for (Map<String, Object> cluster : clusters) {
                String clusterName = (String) cluster.get("name");
                String clusterKey = projectName + "/" + clusterName;
                
                try {
                    monitorCluster(projectName, projectId, cluster);
                } catch (Exception e) {
                    logger.error("Error monitoring cluster {}: {}", clusterKey, e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting clusters for project {}: {}", projectName, e.getMessage(), e);
        }
    }
    
    /**
     * Monitor a specific cluster and apply scaling rules
     */
    private void monitorCluster(String projectName, String projectId, Map<String, Object> cluster) {
        String clusterName = (String) cluster.get("name");
        String clusterKey = projectName + "/" + clusterName;
        
        // Check if cluster is in a state that allows scaling
        String stateName = (String) cluster.get("stateName");
        if (!"IDLE".equals(stateName)) {
            logger.debug("Cluster {} is not in IDLE state (current: {}), skipping monitoring", 
                    clusterKey, stateName);
            return;
        }
        
        // Get current cluster configuration including all shards
        ClusterTierInfo currentTierInfo = getCurrentClusterTier(cluster);
        if (currentTierInfo == null) {
            logger.warn("Could not determine current tier information for cluster {}", clusterKey);
            return;
        }
        
        logger.debug("Monitoring cluster {} with {} shards", 
                clusterKey, currentTierInfo.getShardCount());
        
        // Collect current metrics for this cluster
        ClusterMetrics currentMetrics = collectClusterMetrics(projectId, cluster);
        
        // Update metrics history
        clusterMetricsHistory.put(clusterKey, currentMetrics);
        
        // Check all scaling rules
        for (ScalingRule rule : config.getScalingRules()) {
            try {
                checkScalingRule(projectName, projectId, cluster, currentMetrics, rule, currentTierInfo);
            } catch (Exception e) {
                logger.error("Error checking scaling rule {} for cluster {}: {}", 
                        rule.getName(), clusterKey, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check if a scaling rule should trigger
     */
    private void checkScalingRule(String projectName, String projectId, 
                                Map<String, Object> cluster, ClusterMetrics currentMetrics, 
                                ScalingRule rule, ClusterTierInfo currentTierInfo) {
        
        String clusterName = (String) cluster.get("name");
        String clusterKey = projectName + "/" + clusterName;
        
        // Check if we're in cooldown period
        Instant lastScale = lastScaleActions.get(clusterKey);
        if (lastScale != null) {
            Duration timeSinceLastScale = Duration.between(lastScale, Instant.now());
            Duration cooldown = rule.getCooldownPeriod() != null ? rule.getCooldownPeriod() : DEFAULT_SCALE_COOLDOWN;
            
            if (timeSinceLastScale.compareTo(cooldown) < 0) {
                logger.debug("Cluster {} is in cooldown period ({}), skipping rule {}", 
                        clusterKey, timeSinceLastScale, rule.getName());
                return;
            }
        }
        
        // Evaluate the rule condition
        boolean ruleTriggered = evaluateRuleCondition(clusterKey, currentMetrics, rule);
        
        if (ruleTriggered) {
            logger.info("Scaling rule '{}' triggered for cluster {}", rule.getName(), clusterKey);
            
            // Determine which shards to scale based on rule configuration
            boolean scaleAllShards = rule.isScaleAllShards() || config.isScaleAllShardsInUnison();
            int specificShardIndex = rule.getSpecificShardIndex();
            
            // Create scaled version of the cluster configuration
            ClusterTierInfo targetTierInfo = currentTierInfo.createScaledVersion(
                    rule.getScaleDirection(), 
                    rule.getNodeType(), 
                    scaleAllShards, 
                    specificShardIndex);
            
            // Execute the scaling action
            executeScaling(projectId, clusterName, currentTierInfo, targetTierInfo, rule);
            
            // Record the scaling action
            lastScaleActions.put(clusterKey, Instant.now());
            
            logger.info("Initiated scaling of cluster {} due to rule '{}' (node type: {}, all shards: {})",
                    clusterKey, rule.getName(), rule.getNodeType(), scaleAllShards);
        }
    }
    
    /**
     * Evaluate if a rule condition is met
     */
    private boolean evaluateRuleCondition(String clusterKey, ClusterMetrics metrics, ScalingRule rule) {
        String metricName = rule.getMetricName();
        Double threshold = rule.getThreshold();
        Duration duration = rule.getDuration();
        
        // Get metric values for all hosts
        List<MetricDataPoint> metricData = metrics.getMetricData(metricName);
        
        if (metricData.isEmpty()) {
            logger.debug("No data available for metric {} on cluster {}", metricName, clusterKey);
            return false;
        }
        
        // Check threshold condition
        Instant cutoffTime = Instant.now().minus(duration);
        
        for (MetricDataPoint dataPoint : metricData) {
            if (dataPoint.getTimestamp().isAfter(cutoffTime)) {
                boolean conditionMet = rule.getCondition().evaluate(dataPoint.getValue(), threshold);
                
                if (conditionMet) {
                    logger.debug("Rule condition met: {} on host {} at {}", 
                            rule.getCondition().formatCondition(metricName, threshold),
                            dataPoint.getHostname(), dataPoint.getTimestamp());
                    return true;
                }
            }
        }
        
        return false;
    }
    

    private ClusterMetrics collectClusterMetrics(String projectId, Map<String, Object> cluster) {
        String clusterName = (String) cluster.get("name");
        ClusterMetrics metrics = new ClusterMetrics(clusterName);
        
        try {
            // Get all processes for the project
            List<Map<String, Object>> processes = apiClient.clusters().getProcesses(projectId);
            
            logger.debug("Found {} total processes in project {}", processes.size(), projectId);
            
            // Extract hostnames that belong to this cluster from connection strings
            Set<String> clusterHostnames = extractClusterHostnames(cluster);
            logger.debug("Expected hostnames for cluster {}: {}", clusterName, clusterHostnames);
            
            // Filter processes using userAlias field (which matches connection string hostnames)
            List<Map<String, Object>> clusterProcesses = processes.stream()
                    .filter(process -> {
                        String userAlias = (String) process.get("userAlias");
                        String typeName = (String) process.get("typeName");
                        
                        // First filter: exclude config servers and mongos processes
                        boolean typeMatch = !typeName.startsWith("SHARD_CONFIG") && !typeName.equals("SHARD_MONGOS");
                        
                        // Second filter: match userAlias with cluster hostnames
                        boolean hostnameMatch = userAlias != null && clusterHostnames.contains(userAlias);
                        
                        boolean include = typeMatch && hostnameMatch;
                        
                        if (include) {
                            logger.debug("Including process {} (userAlias: {}) - type: {}", 
                                    process.get("hostname"), userAlias, typeName);
                        }
                        
                        return include;
                    })
                    .collect(Collectors.toList());
            
            logger.debug("Filtered to {} processes for cluster {}", clusterProcesses.size(), clusterName);
            
            if (clusterProcesses.isEmpty()) {
                logger.warn("No matching processes found for cluster {}. Expected hostnames: {}", 
                        clusterName, clusterHostnames);
                
                // Debug: show what userAliases are actually available
                Set<String> availableUserAliases = processes.stream()
                        .map(p -> (String) p.get("userAlias"))
                        .filter(alias -> alias != null)
                        .collect(Collectors.toSet());
                logger.warn("Available userAliases in processes: {}", availableUserAliases);
            }
            
            // Collect metrics for each metric type configured
            Set<String> metricsToCollect = config.getScalingRules().stream()
                    .map(ScalingRule::getMetricName)
                    .collect(Collectors.toSet());
            
            logger.debug("Metrics to collect: {}", metricsToCollect);
            
            for (String metricName : metricsToCollect) {
                collectMetricForCluster(projectId, clusterProcesses, metricName, metrics);
            }
            
        } catch (Exception e) {
            logger.error("Error collecting metrics for cluster {}: {}", clusterName, e.getMessage(), e);
        }
        
        return metrics;
    }

    /**
     * Extract hostnames that belong to this cluster from the connection strings
     */
    private Set<String> extractClusterHostnames(Map<String, Object> cluster) {
        Set<String> hostnames = new HashSet<>();
        
        try {
            Map<String, Object> connectionStrings = (Map<String, Object>) cluster.get("connectionStrings");
            if (connectionStrings != null) {
                String standardConnectionString = (String) connectionStrings.get("standard");
                if (standardConnectionString != null) {
                    // Parse hostnames from connection string like:
                    // mongodb://cluster-shard-00-00.xxx.mongodb.net:27016,cluster-shard-00-01.xxx.mongodb.net:27016,...
                    String hostPart = standardConnectionString
                            .replaceFirst("mongodb://", "")  // Remove mongodb:// prefix
                            .split("\\?")[0];                // Remove query parameters
                    
                    String[] hosts = hostPart.split(",");
                    for (String host : hosts) {
                        String hostname = host.split(":")[0].trim(); // Remove port and whitespace
                        if (!hostname.isEmpty()) {
                            hostnames.add(hostname);
                        }
                    }
                }
            }
            
            logger.debug("Extracted {} hostnames from cluster connection string", hostnames.size());
            
        } catch (Exception e) {
            logger.error("Error extracting hostnames from cluster: {}", e.getMessage());
        }
        
        return hostnames;
    }
    
    /**
     * Collect a specific metric for all processes in a cluster
     */
    private void collectMetricForCluster(String projectId, List<Map<String, Object>> processes, 
                                       String metricName, ClusterMetrics metrics) {
        
        for (Map<String, Object> process : processes) {
            String hostname = (String) process.get("hostname");
            int port = (int) process.get("port");
            
            try {
                // Get recent measurements (last 10 minutes)
                List<Map<String, Object>> measurements = apiClient.monitoring().getProcessMeasurements(
                        projectId, hostname, port, 
                        List.of(metricName), 
                        "PT1M", // 1 minute granularity
                        "PT10M" // Last 10 minutes
                );
                
                if (measurements == null || measurements.isEmpty()) {
                	
                	logger.debug("PT1M metric data not available, trying PT10S data");
                	measurements = apiClient.monitoring().getProcessMeasurements(
                            projectId, hostname, port, 
                            List.of(metricName), 
                            "PT10S", // 1 minute granularity
                            "PT10M" // Last 10 minutes
                    );
                
                }
                
                if (measurements != null || !measurements.isEmpty()) {
                    for (Map<String, Object> measurement : measurements) {
                        String name = (String) measurement.get("name");
                        
                        if (metricName.equals(name)) {
                            List<Map<String, Object>> dataPoints = 
                                    (List<Map<String, Object>>) measurement.get("dataPoints");
                            
                            if (dataPoints != null) {
                                for (Map<String, Object> dataPoint : dataPoints) {
                                    String timestampStr = (String) dataPoint.get("timestamp");
                                    Object valueObj = dataPoint.get("value");
                                    
                                    if (timestampStr != null && valueObj != null) {
                                        try {
                                            Instant timestamp = Instant.parse(timestampStr);
                                            double value = ((Number) valueObj).doubleValue();
                                            
                                            // Convert to percentage if needed
                                            if ("SYSTEM_NORMALIZED_CPU_USER".equals(metricName)) {
                                                value = value * 100; // Convert to percentage
                                            }
                                            
                                            metrics.addDataPoint(metricName, 
                                                    new MetricDataPoint(hostname, timestamp, value));
                                        } catch (Exception e) {
                                            logger.warn("Error parsing data point: {}", e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error collecting metric {} for process {}:{}: {}", 
                        metricName, hostname, port, e.getMessage());
            }
        }
    }
    
    /**
     * Get cluster tier information including all shards and node types
     */
    private ClusterTierInfo getCurrentClusterTier(Map<String, Object> cluster) {
        try {
            String clusterName = (String) cluster.get("name");
            List<Map<String, Object>> replicationSpecs = (List<Map<String, Object>>) cluster.get("replicationSpecs");
            
            if (replicationSpecs == null || replicationSpecs.isEmpty()) {
                logger.warn("No replicationSpecs found for cluster {}", clusterName);
                return null;
            }
            
            logger.debug("Cluster {} has {} replicationSpecs", clusterName, replicationSpecs.size());
            
            ClusterTierInfo tierInfo = new ClusterTierInfo(clusterName);
            
            // Process each shard
            for (int shardIndex = 0; shardIndex < replicationSpecs.size(); shardIndex++) {
                Map<String, Object> replicationSpec = replicationSpecs.get(shardIndex);
                String shardId = "shard-" + shardIndex;
                
                ShardTierInfo shardInfo = new ShardTierInfo(shardId);
                
                // Extract regionConfigs (Atlas clusters are organized by regions)
                List<Map<String, Object>> regionConfigs = (List<Map<String, Object>>) replicationSpec.get("regionConfigs");
                
                if (regionConfigs != null && !regionConfigs.isEmpty()) {
                    // Use the first region config (most clusters have single region)
                    Map<String, Object> regionConfig = regionConfigs.get(0);
                    
                    // Extract electable specs (primary/secondary nodes)
                    Map<String, Object> electableSpecs = (Map<String, Object>) regionConfig.get("electableSpecs");
                    if (electableSpecs != null) {
                        String instanceSize = (String) electableSpecs.get("instanceSize");
                        Integer nodeCount = (Integer) electableSpecs.get("nodeCount");
                        if (instanceSize != null && nodeCount != null && nodeCount > 0) {
                            shardInfo.setElectableSpecs(instanceSize, nodeCount);
                        }
                    }
                    
                    // Extract analytics specs (analytics nodes)
                    Map<String, Object> analyticsSpecs = (Map<String, Object>) regionConfig.get("analyticsSpecs");
                    if (analyticsSpecs != null) {
                        String instanceSize = (String) analyticsSpecs.get("instanceSize");
                        Integer nodeCount = (Integer) analyticsSpecs.get("nodeCount");
                        if (instanceSize != null && nodeCount != null && nodeCount > 0) {
                            shardInfo.setAnalyticsSpecs(instanceSize, nodeCount);
                        }
                    }
                    
                    // Extract read-only specs (read-only nodes)
                    Map<String, Object> readOnlySpecs = (Map<String, Object>) regionConfig.get("readOnlySpecs");
                    if (readOnlySpecs != null) {
                        String instanceSize = (String) readOnlySpecs.get("instanceSize");
                        Integer nodeCount = (Integer) readOnlySpecs.get("nodeCount");
                        if (instanceSize != null && nodeCount != null && nodeCount > 0) {
                            shardInfo.setReadOnlySpecs(instanceSize, nodeCount);
                        }
                    }
                    
                } else {
                    logger.warn("No regionConfigs found for shard {} in cluster {}", shardId, clusterName);
                }
                
                tierInfo.addShard(shardInfo);
                
                logger.debug("Shard {} - Electable: {} ({}), Analytics: {} ({}), ReadOnly: {} ({})", 
                        shardId, 
                        shardInfo.getElectableInstanceSize(), shardInfo.getElectableNodeCount(),
                        shardInfo.getAnalyticsInstanceSize(), shardInfo.getAnalyticsNodeCount(),
                        shardInfo.getReadOnlyInstanceSize(), shardInfo.getReadOnlyNodeCount());
            }
            
            logger.info("Cluster {} has {} shards with tiers: {}", 
                    clusterName, tierInfo.getShardCount(), tierInfo.getSummary());
            
            return tierInfo;
            
        } catch (Exception e) {
            logger.error("Error extracting cluster tier information: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get the tier progression for scaling
     */
    private List<String> getTierProgression() {
        return Arrays.asList(
                "M0", "M2", "M5", "M10", "M20", "M30", "M40", "M50", "M60", "M80", 
                "M140", "M200", "M300", "M400", "M700"
        );
    }
    
    /**
     * Execute the actual scaling operation
     */
    private void executeScaling(String projectId, String clusterName, ClusterTierInfo currentTierInfo, 
                              ClusterTierInfo targetTierInfo, ScalingRule rule) {
        
        if (config.isDryRun()) {
            logger.info("DRY RUN: Would scale cluster {} due to rule '{}' - Changes:", 
                    clusterName, rule.getName());
            logScalingChanges(currentTierInfo, targetTierInfo);
            return;
        }
        
        try {
            // Check if cluster is already scaling
            if (scalingClient.isClusterScaling(projectId, clusterName)) {
                logger.warn("Cluster {} is already scaling, skipping scaling action", clusterName);
                return;
            }
            
            // Validate the scaling operation
            if (!isValidScaling(currentTierInfo, targetTierInfo)) {
                logger.error("Invalid scaling operation for cluster {}", clusterName);
                return;
            }
            
            // Execute the scaling operation with the new tier structure
            boolean success = scalingClient.scaleCluster(projectId, clusterName, targetTierInfo);
            
            if (success) {
                logger.info("Successfully initiated scaling of cluster {} due to rule '{}'", 
                        clusterName, rule.getName());
                logScalingChanges(currentTierInfo, targetTierInfo);
            } else {
                logger.error("Failed to initiate scaling of cluster {}", clusterName);
            }
            
        } catch (Exception e) {
            logger.error("Error executing scaling for cluster {}: {}", clusterName, e.getMessage(), e);
        }
    }
    
    /**
     * Log the scaling changes being made
     */
    private void logScalingChanges(ClusterTierInfo current, ClusterTierInfo target) {
        logger.info("Scaling changes for cluster {}:", current.getClusterName());
        
        for (int i = 0; i < current.getShards().size(); i++) {
            ShardTierInfo currentShard = current.getShards().get(i);
            ShardTierInfo targetShard = target.getShards().get(i);
            
            boolean hasChanges = false;
            StringBuilder changes = new StringBuilder();
            changes.append("  Shard ").append(currentShard.getShardId()).append(": ");
            
            // Check electable changes
            if (!java.util.Objects.equals(currentShard.getElectableInstanceSize(), targetShard.getElectableInstanceSize())) {
                changes.append("Electable: ").append(currentShard.getElectableInstanceSize())
                       .append(" → ").append(targetShard.getElectableInstanceSize()).append(" ");
                hasChanges = true;
            }
            
            // Check analytics changes
            if (!java.util.Objects.equals(currentShard.getAnalyticsInstanceSize(), targetShard.getAnalyticsInstanceSize())) {
                changes.append("Analytics: ").append(currentShard.getAnalyticsInstanceSize())
                       .append(" → ").append(targetShard.getAnalyticsInstanceSize()).append(" ");
                hasChanges = true;
            }
            
            // Check read-only changes
            if (!java.util.Objects.equals(currentShard.getReadOnlyInstanceSize(), targetShard.getReadOnlyInstanceSize())) {
                changes.append("ReadOnly: ").append(currentShard.getReadOnlyInstanceSize())
                       .append(" → ").append(targetShard.getReadOnlyInstanceSize()).append(" ");
                hasChanges = true;
            }
            
            if (hasChanges) {
                logger.info(changes.toString());
            } else {
                logger.info("  Shard {}: No changes", currentShard.getShardId());
            }
        }
    }
    
    /**
     * Validate that the scaling operation is valid
     */
    private boolean isValidScaling(ClusterTierInfo current, ClusterTierInfo target) {
        if (current.getShardCount() != target.getShardCount()) {
            logger.error("Shard count mismatch: {} vs {}", current.getShardCount(), target.getShardCount());
            return false;
        }
        
        List<String> validTiers = getTierProgression();
        
        for (int i = 0; i < current.getShards().size(); i++) {
            ShardTierInfo currentShard = current.getShards().get(i);
            ShardTierInfo targetShard = target.getShards().get(i);
            
            // Validate electable tier changes
            if (!isValidTierChange(currentShard.getElectableInstanceSize(), 
                                 targetShard.getElectableInstanceSize(), validTiers)) {
                return false;
            }
            
            // Validate analytics tier changes
            if (!isValidTierChange(currentShard.getAnalyticsInstanceSize(), 
                                 targetShard.getAnalyticsInstanceSize(), validTiers)) {
                return false;
            }
            
            // Validate read-only tier changes
            if (!isValidTierChange(currentShard.getReadOnlyInstanceSize(), 
                                 targetShard.getReadOnlyInstanceSize(), validTiers)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate a single tier change
     */
    private boolean isValidTierChange(String currentTier, String targetTier, List<String> validTiers) {
        // If both are null or equal, it's valid (no change)
        if (java.util.Objects.equals(currentTier, targetTier)) {
            return true;
        }
        
        // If either is null but not both, check if we're adding/removing nodes
        if (currentTier == null || targetTier == null) {
            // This could be valid if we're adding or removing node types
            return true;
        }
        
        // Both are non-null, check if they're valid tiers
        return validTiers.contains(currentTier) && validTiers.contains(targetTier);
    }
    
    /**
     * Clean up old metrics data to prevent memory leaks
     */
    private void cleanupOldMetrics() {
        try {
            Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
            
            for (ClusterMetrics metrics : clusterMetricsHistory.values()) {
                metrics.cleanupOldData(cutoff);
            }
            
            logger.debug("Cleaned up old metrics data");
        } catch (Exception e) {
            logger.error("Error cleaning up old metrics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get the current status of the autoscaler
     */
    public AutoscalerStatus getStatus() {
        return new AutoscalerStatus(
                running.get(),
                clusterMetricsHistory.size(),
                lastScaleActions.size(),
                Instant.now()
        );
    }
    
    /**
     * Status information for the autoscaler
     */
    public static class AutoscalerStatus {
        private final boolean running;
        private final int clustersMonitored;
        private final int recentScaleActions;
        private final Instant lastUpdate;
        
        public AutoscalerStatus(boolean running, int clustersMonitored, int recentScaleActions, Instant lastUpdate) {
            this.running = running;
            this.clustersMonitored = clustersMonitored;
            this.recentScaleActions = recentScaleActions;
            this.lastUpdate = lastUpdate;
        }
        
        // Getters
        public boolean isRunning() { return running; }
        public int getClustersMonitored() { return clustersMonitored; }
        public int getRecentScaleActions() { return recentScaleActions; }
        public Instant getLastUpdate() { return lastUpdate; }
    }
    
    /**
     * Container for individual shard tier information
     */
    public static class ShardTierInfo {
        private final String shardId;
        private String electableInstanceSize;
        private Integer electableNodeCount;
        private String analyticsInstanceSize;
        private Integer analyticsNodeCount;
        private String readOnlyInstanceSize;
        private Integer readOnlyNodeCount;
        
        public ShardTierInfo(String shardId) {
            this.shardId = shardId;
        }
        
        public void setElectableSpecs(String instanceSize, Integer nodeCount) {
            this.electableInstanceSize = instanceSize;
            this.electableNodeCount = nodeCount;
        }
        
        public void setAnalyticsSpecs(String instanceSize, Integer nodeCount) {
            this.analyticsInstanceSize = instanceSize;
            this.analyticsNodeCount = nodeCount;
        }
        
        public void setReadOnlySpecs(String instanceSize, Integer nodeCount) {
            this.readOnlyInstanceSize = instanceSize;
            this.readOnlyNodeCount = nodeCount;
        }
        
        public void copyFrom(ShardTierInfo other) {
            this.electableInstanceSize = other.electableInstanceSize;
            this.electableNodeCount = other.electableNodeCount;
            this.analyticsInstanceSize = other.analyticsInstanceSize;
            this.analyticsNodeCount = other.analyticsNodeCount;
            this.readOnlyInstanceSize = other.readOnlyInstanceSize;
            this.readOnlyNodeCount = other.readOnlyNodeCount;
        }
        
        public void scaleNodeType(ClusterTierInfo.NodeType nodeType, ScaleDirection scaleDirection) {
            List<String> tierProgression = Arrays.asList(
                    "M0", "M2", "M5", "M10", "M20", "M30", "M40", "M50", "M60", "M80", 
                    "M140", "M200", "M300", "M400", "M700"
            );
            
            switch (nodeType) {
                case ELECTABLE:
                    if (electableInstanceSize != null) {
                        electableInstanceSize = getNextTier(electableInstanceSize, scaleDirection, tierProgression);
                    }
                    break;
                case ANALYTICS:
                    if (analyticsInstanceSize != null) {
                        analyticsInstanceSize = getNextTier(analyticsInstanceSize, scaleDirection, tierProgression);
                    }
                    break;
                case READ_ONLY:
                    if (readOnlyInstanceSize != null) {
                        readOnlyInstanceSize = getNextTier(readOnlyInstanceSize, scaleDirection, tierProgression);
                    }
                    break;
            }
        }
        
        private String getNextTier(String currentTier, ScaleDirection direction, List<String> tierProgression) {
            int currentIndex = tierProgression.indexOf(currentTier);
            if (currentIndex == -1) return currentTier;
            
            int targetIndex = direction == ScaleDirection.UP ? currentIndex + 1 : currentIndex - 1;
            
            if (targetIndex >= 0 && targetIndex < tierProgression.size()) {
                return tierProgression.get(targetIndex);
            }
            
            return currentTier; // Can't scale further in that direction
        }
        
        // Getters
        public String getShardId() { return shardId; }
        public String getElectableInstanceSize() { return electableInstanceSize; }
        public Integer getElectableNodeCount() { return electableNodeCount; }
        public String getAnalyticsInstanceSize() { return analyticsInstanceSize; }
        public Integer getAnalyticsNodeCount() { return analyticsNodeCount; }
        public String getReadOnlyInstanceSize() { return readOnlyInstanceSize; }
        public Integer getReadOnlyNodeCount() { return readOnlyNodeCount; }
    }
    
    /**
     * Container for metric data points
     */
    public static class MetricDataPoint {
        private final String hostname;
        private final Instant timestamp;
        private final double value;
        
        public MetricDataPoint(String hostname, Instant timestamp, double value) {
            this.hostname = hostname;
            this.timestamp = timestamp;
            this.value = value;
        }
        
        // Getters
        public String getHostname() { return hostname; }
        public Instant getTimestamp() { return timestamp; }
        public double getValue() { return value; }
    }
    
    /**
     * Container for cluster metrics
     */
    public static class ClusterMetrics {
        private final String clusterName;
        private final Map<String, List<MetricDataPoint>> metricData = new HashMap<>();
        
        public ClusterMetrics(String clusterName) {
            this.clusterName = clusterName;
        }
        
        public void addDataPoint(String metricName, MetricDataPoint dataPoint) {
            metricData.computeIfAbsent(metricName, k -> new ArrayList<>()).add(dataPoint);
        }
        
        public List<MetricDataPoint> getMetricData(String metricName) {
            return metricData.getOrDefault(metricName, new ArrayList<>());
        }
        
        public void cleanupOldData(Instant cutoff) {
            for (List<MetricDataPoint> dataPoints : metricData.values()) {
                dataPoints.removeIf(dp -> dp.getTimestamp().isBefore(cutoff));
            }
        }
        
        public String getClusterName() { return clusterName; }
    }
}