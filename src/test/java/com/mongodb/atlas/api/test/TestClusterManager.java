package com.mongodb.atlas.api.test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasClustersClient;
import com.mongodb.atlas.api.clients.AtlasFlexClustersClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

/**
 * Manages test cluster lifecycle for Atlas integration tests.
 * Provides cluster reuse, discovery, and cleanup capabilities.
 */
public class TestClusterManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TestClusterManager.class);
    
    // Naming conventions
    private static final String SHARED_CLUSTER_PREFIX = "shared-test-";
    private static final String ISOLATED_CLUSTER_PREFIX = "isolated-test-";
    private static final String FLEX_CLUSTER_PREFIX = "flex-test-";
    
    // Cluster states
    private static final String CLUSTER_STATE_IDLE = "IDLE";
    private static final String CLUSTER_STATE_CREATING = "CREATING";
    private static final String CLUSTER_STATE_UPDATING = "UPDATING";
    
    private final AtlasTestConfig config;
    private final AtlasClustersClient clustersClient;
    private final AtlasFlexClustersClient flexClustersClient;
    private final String testProjectId;
    
    // Track clusters created by this manager
    private final Map<String, ClusterInfo> managedClusters = new ConcurrentHashMap<>();
    private final Set<String> reusedClusters = new HashSet<>();
    
    public TestClusterManager(AtlasTestConfig config, AtlasClustersClient clustersClient, 
                             AtlasFlexClustersClient flexClustersClient) {
        this.config = config;
        this.clustersClient = clustersClient;
        this.flexClustersClient = flexClustersClient;
        this.testProjectId = config.getTestProjectId();
        
        if (testProjectId == null) {
            throw new IllegalStateException("Test project ID must be configured for cluster management");
        }
    }
    
    /**
     * Get or create a shared cluster for testing.
     * Shared clusters are reused across tests and not automatically cleaned up.
     */
    public String getOrCreateSharedCluster(String instanceSize, String mongoVersion) {
        String clusterKey = "shared-" + instanceSize.toLowerCase() + "-" + mongoVersion.replace(".", "");
        String clusterName = SHARED_CLUSTER_PREFIX + clusterKey;
        
        logger.info("Looking for shared cluster: {}", clusterName);
        
        // Check if we already have this cluster
        if (managedClusters.containsKey(clusterName)) {
            ClusterInfo info = managedClusters.get(clusterName);
            if (isClusterReady(info.clusterId)) {
                logger.info("Reusing existing shared cluster: {}", clusterName);
                return clusterName;
            }
        }
        
        // Look for existing cluster
        Map<String, Object> existingCluster = findClusterByName(clusterName);
        if (existingCluster != null) {
            String state = (String) existingCluster.get("stateName");
            if (CLUSTER_STATE_IDLE.equals(state)) {
                logger.info("Found existing shared cluster: {}", clusterName);
                String clusterId = (String) existingCluster.get("id");
                managedClusters.put(clusterName, new ClusterInfo(clusterId, clusterName, false, true));
                reusedClusters.add(clusterName);
                return clusterName;
            } else if (CLUSTER_STATE_CREATING.equals(state) || CLUSTER_STATE_UPDATING.equals(state)) {
                logger.info("Found shared cluster in progress: {} (state: {})", clusterName, state);
                String clusterId = (String) existingCluster.get("id");
                managedClusters.put(clusterName, new ClusterInfo(clusterId, clusterName, false, true));
                reusedClusters.add(clusterName);
                return clusterName; // Caller should wait or check status
            }
        }
        
        // Create new shared cluster
        logger.info("Creating new shared cluster: {} (size: {}, version: {})", 
                   clusterName, instanceSize, mongoVersion);
        
        try {
            Map<String, Object> cluster;
            if ("M0".equals(instanceSize)) {
                // Use Flex for M0 equivalent
                cluster = flexClustersClient.createFlexCluster(
                    testProjectId, clusterName, mongoVersion, 
                    config.getTestRegion(), config.getTestCloudProvider());
            } else {
                cluster = clustersClient.createCluster(
                    testProjectId, clusterName, instanceSize, mongoVersion, 
                    config.getTestRegion(), config.getTestCloudProvider());
            }
            
            String clusterId = (String) cluster.get("id");
            managedClusters.put(clusterName, new ClusterInfo(clusterId, clusterName, true, true));
            
            logger.info("Created shared cluster: {} (ID: {})", clusterName, clusterId);
            return clusterName;
            
        } catch (Exception e) {
            logger.error("Failed to create shared cluster: {}", clusterName, e);
            throw new RuntimeException("Failed to create shared cluster: " + clusterName, e);
        }
    }
    
    /**
     * Create an isolated cluster for a specific test.
     * Isolated clusters are automatically cleaned up after the test.
     */
    public String createIsolatedCluster(String testClass, String testMethod, 
                                       String instanceSize, String mongoVersion) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String clusterName = ISOLATED_CLUSTER_PREFIX + testClass.toLowerCase() + "-" + 
                           testMethod.toLowerCase() + "-" + timestamp;
        
        logger.info("Creating isolated cluster: {} (size: {}, version: {})", 
                   clusterName, instanceSize, mongoVersion);
        
        try {
            Map<String, Object> cluster;
            if ("M0".equals(instanceSize)) {
                // Use Flex for M0 equivalent
                cluster = flexClustersClient.createFlexCluster(
                    testProjectId, clusterName, mongoVersion, 
                    config.getTestRegion(), config.getTestCloudProvider());
            } else {
                cluster = clustersClient.createCluster(
                    testProjectId, clusterName, instanceSize, mongoVersion, 
                    config.getTestRegion(), config.getTestCloudProvider());
            }
            
            String clusterId = (String) cluster.get("id");
            managedClusters.put(clusterName, new ClusterInfo(clusterId, clusterName, true, false));
            
            logger.info("Created isolated cluster: {} (ID: {})", clusterName, clusterId);
            return clusterName;
            
        } catch (Exception e) {
            logger.error("Failed to create isolated cluster: {}", clusterName, e);
            throw new RuntimeException("Failed to create isolated cluster: " + clusterName, e);
        }
    }
    
    /**
     * Find clusters matching a name pattern
     */
    public List<Map<String, Object>> findClustersByPattern(String namePattern) {
        try {
            List<Map<String, Object>> allClusters = clustersClient.getClusters(testProjectId);
            List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
            
            // Combine regular and flex clusters
            List<Map<String, Object>> combined = new ArrayList<>(allClusters);
            combined.addAll(flexClusters);
            
            return combined.stream()
                .filter(cluster -> {
                    String name = (String) cluster.get("name");
                    return name != null && name.matches(namePattern);
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to search for clusters with pattern: {}", namePattern, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Find a specific cluster by exact name
     */
    public Map<String, Object> findClusterByName(String clusterName) {
        List<Map<String, Object>> matches = findClustersByPattern("^" + clusterName + "$");
        return matches.isEmpty() ? null : matches.get(0);
    }
    
    /**
     * Check if a cluster is ready for use
     */
    public boolean isClusterReady(String clusterName) {
        Map<String, Object> cluster = findClusterByName(clusterName);
        if (cluster == null) {
            return false;
        }
        
        String state = (String) cluster.get("stateName");
        return CLUSTER_STATE_IDLE.equals(state);
    }
    
    /**
     * Wait for a cluster to become ready
     */
    public boolean waitForClusterReady(String clusterName, Duration timeout) {
        logger.info("Waiting for cluster to be ready: {} (timeout: {})", clusterName, timeout);
        
        Instant deadline = Instant.now().plus(timeout);
        
        while (Instant.now().isBefore(deadline)) {
            if (isClusterReady(clusterName)) {
                logger.info("Cluster is ready: {}", clusterName);
                return true;
            }
            
            try {
                Thread.sleep(10000); // Wait 10 seconds between checks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for cluster: {}", clusterName);
                return false;
            }
            
            logger.debug("Still waiting for cluster: {}", clusterName);
        }
        
        logger.warn("Timeout waiting for cluster to be ready: {}", clusterName);
        return false;
    }
    
    /**
     * Clean up isolated clusters created by this manager
     */
    public void cleanupIsolatedClusters() {
        logger.info("Cleaning up isolated clusters created by this manager");
        
        List<String> toCleanup = managedClusters.entrySet().stream()
            .filter(entry -> entry.getValue().createdByManager && !entry.getValue().isShared)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        for (String clusterName : toCleanup) {
            try {
                deleteCluster(clusterName);
                managedClusters.remove(clusterName);
            } catch (Exception e) {
                logger.warn("Failed to cleanup isolated cluster: {}", clusterName, e);
            }
        }
    }
    
    /**
     * Clean up all test clusters matching a pattern
     */
    public void cleanupTestClusters(String namePattern) {
        logger.info("Cleaning up test clusters matching pattern: {}", namePattern);
        
        List<Map<String, Object>> clusters = findClustersByPattern(namePattern);
        
        for (Map<String, Object> cluster : clusters) {
            String clusterName = (String) cluster.get("name");
            try {
                deleteCluster(clusterName);
                managedClusters.remove(clusterName);
            } catch (Exception e) {
                logger.warn("Failed to cleanup test cluster: {}", clusterName, e);
            }
        }
    }
    
    /**
     * Delete a specific cluster
     */
    private void deleteCluster(String clusterName) {
        logger.info("Deleting cluster: {}", clusterName);
        
        // Try both regular and flex cluster deletion
        try {
            clustersClient.deleteCluster(testProjectId, clusterName);
            logger.info("Deleted regular cluster: {}", clusterName);
        } catch (Exception e1) {
            try {
                flexClustersClient.deleteFlexCluster(testProjectId, clusterName);
                logger.info("Deleted flex cluster: {}", clusterName);
            } catch (Exception e2) {
                logger.warn("Failed to delete cluster {} as both regular and flex: regular={}, flex={}", 
                           clusterName, e1.getMessage(), e2.getMessage());
                throw new RuntimeException("Failed to delete cluster: " + clusterName, e2);
            }
        }
    }
    
    /**
     * Get summary of managed clusters
     */
    public String getClusterSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Test Cluster Manager Summary:\n");
        summary.append("  Total managed: ").append(managedClusters.size()).append("\n");
        summary.append("  Reused existing: ").append(reusedClusters.size()).append("\n");
        
        long shared = managedClusters.values().stream().filter(c -> c.isShared).count();
        long isolated = managedClusters.values().stream().filter(c -> !c.isShared).count();
        
        summary.append("  Shared clusters: ").append(shared).append("\n");
        summary.append("  Isolated clusters: ").append(isolated).append("\n");
        
        if (!managedClusters.isEmpty()) {
            summary.append("  Managed clusters:\n");
            managedClusters.forEach((name, info) -> {
                summary.append("    - ").append(name)
                       .append(" (").append(info.isShared ? "shared" : "isolated")
                       .append(", ").append(info.createdByManager ? "created" : "reused")
                       .append(")\n");
            });
        }
        
        return summary.toString();
    }
    
    /**
     * Information about a managed cluster
     */
    private static class ClusterInfo {
        final String clusterId;
        final String clusterName;
        final boolean createdByManager;
        final boolean isShared;
        
        ClusterInfo(String clusterId, String clusterName, boolean createdByManager, boolean isShared) {
            this.clusterId = clusterId;
            this.clusterName = clusterName;
            this.createdByManager = createdByManager;
            this.isShared = isShared;
        }
    }
}