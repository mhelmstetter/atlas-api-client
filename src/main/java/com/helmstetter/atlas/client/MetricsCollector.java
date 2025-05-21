package com.helmstetter.atlas.client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects metrics from MongoDB Atlas API and optionally stores them
 * Separates collection from processing
 */
public class MetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    
    private final AtlasApiClient apiClient;
    private final List<String> metrics;
    private final String period;
    private final String granularity;
    private final MetricsStorage metricsStorage;
    private final boolean storeMetrics;
    private final boolean collectOnly;
    
    // Tracking for collection statistics
    private int totalProcessesScanned = 0;
    private int totalDataPointsCollected = 0;
    private int totalDataPointsStored = 0;
    private final Map<String, Integer> projectDataPoints = new HashMap<>();
    
    /**
     * Creates a collector that doesn't store metrics
     */
    public MetricsCollector(AtlasApiClient apiClient, List<String> metrics, String period, String granularity) {
        this(apiClient, metrics, period, granularity, null, false, false);
    }
    
    /**
     * Creates a collector with storage option
     * 
     * @param apiClient The Atlas API client
     * @param metrics List of metrics to collect
     * @param period Time period to collect
     * @param granularity Data granularity
     * @param metricsStorage Optional storage for collected metrics (can be null)
     * @param storeMetrics Whether to store metrics in the provided storage
     * @param collectOnly If true, only collect metrics without processing
     */
    public MetricsCollector(AtlasApiClient apiClient, List<String> metrics, String period, 
            String granularity, MetricsStorage metricsStorage, boolean storeMetrics, boolean collectOnly) {
        this.apiClient = apiClient;
        this.metrics = metrics;
        this.period = period;
        this.granularity = granularity;
        this.metricsStorage = metricsStorage;
        this.storeMetrics = storeMetrics && metricsStorage != null;
        this.collectOnly = collectOnly;
        
        logger.info("Initialized metrics collector with {} metrics, period={}, granularity={}",
                metrics.size(), period, granularity);
        logger.info("Storage enabled: {}, Collect only mode: {}", this.storeMetrics, this.collectOnly);
    }
    
    /**
     * Collect metrics for specified projects
     * 
     * @param includeProjectNames Project names to include
     * @return Map of project names to their metric results (empty if collectOnly=true)
     */
    public Map<String, ProjectMetricsResult> collectMetrics(Set<String> includeProjectNames) {
        // Get projects matching the specified names
        Map<String, String> projectMap = apiClient.getProjects(includeProjectNames);
        
        logger.info("Beginning to collect metrics for {} projects: {}", 
                projectMap.size(), String.join(", ", projectMap.keySet()));
        
        // Reset collection statistics
        resetCollectionStats();
        
        // Create results map (will remain empty if collectOnly=true)
        Map<String, ProjectMetricsResult> results = new HashMap<>();
        
        // If not in collect-only mode, initialize result objects
        if (!collectOnly) {
            for (String projectName : projectMap.keySet()) {
                String projectId = projectMap.get(projectName);
                ProjectMetricsResult projectResult = new ProjectMetricsResult(projectName, projectId);
                
                // Initialize metrics
                metrics.forEach(projectResult::initializeMetric);
                
                results.put(projectName, projectResult);
            }
        }
        
        // Process each project
        for (String projectName : projectMap.keySet()) {
            String projectId = projectMap.get(projectName);
            
            try {
                logger.info("Starting collection for project: {} ({})", projectName, projectId);
                
                // Collect metrics for this project
                ProjectCollectionResult collectionResult = 
                        collectProjectMetrics(projectName, projectId);
                
                // Update collection statistics
                totalProcessesScanned += collectionResult.getProcessCount();
                totalDataPointsCollected += collectionResult.getDataPointsCollected();
                totalDataPointsStored += collectionResult.getDataPointsStored();
                projectDataPoints.put(projectName, collectionResult.getDataPointsCollected());
                
                // If not in collect-only mode, calculate final averages for the project
                if (!collectOnly && results.containsKey(projectName)) {
                    results.get(projectName).calculateAverages();
                }
                
                // Log project summary
                logger.info("Project {} collection complete: {} processes, {} data points collected, {} stored",
                        projectName, collectionResult.getProcessCount(), 
                        collectionResult.getDataPointsCollected(),
                        collectionResult.getDataPointsStored());
                
            } catch (Exception e) {
                logger.error("Error collecting metrics for project {}: {}", projectName, e.getMessage(), e);
            }
        }
        
        // Log final collection statistics
        logCollectionStats();
        
        return results;
    }
    
    /**
     * Collect metrics for a single project
     * 
     * @param projectName The project name
     * @param projectId The project ID
     * @return Collection statistics for this project
     */
    private ProjectCollectionResult collectProjectMetrics(String projectName, String projectId) {
        ProjectCollectionResult result = new ProjectCollectionResult(projectName, projectId);
        
        try {
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            logger.info("Collecting metrics for project: {} with {} processes", 
                    projectName, processes.size());
            
            // Filter out config servers and mongos instances
            List<Map<String, Object>> filteredProcesses = processes.stream()
                    .filter(process -> {
                        String typeName = (String) process.get("typeName");
                        return !typeName.startsWith("SHARD_CONFIG") && !typeName.equals("SHARD_MONGOS");
                    })
                    .collect(Collectors.toList());
            
            logger.info("Filtered to {} mongod processes for project {}", 
                    filteredProcesses.size(), projectName);
            
            result.setProcessCount(filteredProcesses.size());
            
            // Process each MongoDB instance
            int processedCount = 0;
            
            // Use parallel processing if we have many processes
            if (filteredProcesses.size() > 5) {
                collectProcessesInParallel(projectName, projectId, filteredProcesses, result);
            } else {
                // Sequential processing for fewer processes
                for (Map<String, Object> process : filteredProcesses) {
                    String hostname = (String) process.get("hostname");
                    int port = (int) process.get("port");
                    
                    try {
                        // Collect system metrics
                        int systemPoints = collectSystemMetrics(projectName, projectId, hostname, port, result);
                        result.addDataPointsCollected(systemPoints);
                        
                        // Collect disk metrics if requested
                        if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
                            int diskPoints = collectDiskMetrics(projectName, projectId, hostname, port, result);
                            result.addDataPointsCollected(diskPoints);
                        }
                        
                        // Log progress periodically
                        processedCount++;
                        if (processedCount % 5 == 0 || processedCount == filteredProcesses.size()) {
                            logger.info("Project {} progress: {} of {} processes complete ({}%)",
                                    projectName, processedCount, filteredProcesses.size(),
                                    Math.round((double) processedCount / filteredProcesses.size() * 100));
                        }
                    } catch (Exception e) {
                        // Log error but continue with other processes
                        logger.error("Error collecting metrics for process {}:{} in project {}: {}", 
                                hostname, port, projectName, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error collecting metrics for project {}: {}", projectName, e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Collect metrics for multiple processes in parallel
     */
    private void collectProcessesInParallel(String projectName, String projectId,
            List<Map<String, Object>> processes, ProjectCollectionResult result) {
        
        logger.info("Using parallel collection for project {} with {} processes", 
                projectName, processes.size());
        
        // Create a thread pool with a reasonable number of threads
        int threadCount = Math.min(processes.size(), 10);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        try {
            // Create collection tasks
            List<Future<Integer>> futures = new ArrayList<>();
            
            for (Map<String, Object> process : processes) {
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                
                // Create a collection task for this process
                Callable<Integer> task = () -> {
                    int totalPoints = 0;
                    try {
                        // Collect system metrics
                        int systemPoints = collectSystemMetrics(projectName, projectId, hostname, port, result);
                        totalPoints += systemPoints;
                        
                        // Collect disk metrics if requested
                        if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
                            int diskPoints = collectDiskMetrics(projectName, projectId, hostname, port, result);
                            totalPoints += diskPoints;
                        }
                    } catch (Exception e) {
                        logger.error("Error collecting metrics for process {}:{} in project {}: {}", 
                                hostname, port, projectName, e.getMessage());
                    }
                    return totalPoints;
                };
                
                // Submit the task
                futures.add(executor.submit(task));
            }
            
            // Wait for all tasks to complete and collect results
            int processedCount = 0;
            int totalPoints = 0;
            
            for (Future<Integer> future : futures) {
                try {
                    int points = future.get();
                    totalPoints += points;
                    
                    // Log progress periodically
                    processedCount++;
                    if (processedCount % 5 == 0 || processedCount == processes.size()) {
                        logger.info("Project {} parallel progress: {} of {} processes complete ({}%)",
                                projectName, processedCount, processes.size(),
                                Math.round((double) processedCount / processes.size() * 100));
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving parallel collection result: {}", e.getMessage());
                }
            }
            
            // Add the total points collected
            result.addDataPointsCollected(totalPoints);
            
        } finally {
            // Shut down the executor
            executor.shutdown();
        }
    }
    
    /**
     * Collect system-level metrics (CPU, memory)
     */
    private int collectSystemMetrics(
            String projectName, String projectId, 
            String hostname, int port,
            ProjectCollectionResult result) {
        
        // Only include non-disk metrics
        List<String> systemMetrics = metrics.stream()
                .filter(m -> !m.startsWith("DISK_"))
                .collect(Collectors.toList());
        
        if (systemMetrics.isEmpty()) {
            return 0; // Skip if no system metrics requested
        }
        
        int dataPointsCollected = 0;
        
        try {
            // Get measurements for this process
            List<Map<String, Object>> measurements = 
                    apiClient.getProcessMeasurements(projectId, hostname, port, 
                            systemMetrics, granularity, period);
            
            if (measurements == null || measurements.isEmpty()) {
                logger.warn("{} {} -> No measurements data found", 
                        projectName, hostname + ":" + port);
                return 0;
            }
            
            // Process each measurement
            for (Map<String, Object> measurement : measurements) {
                String metric = (String) measurement.get("name");
                List<Map<String, Object>> dataPoints = 
                        (List<Map<String, Object>>) measurement.get("dataPoints");
                
                if (dataPoints == null || dataPoints.isEmpty()) {
                    logger.warn("{} {} -> No data points found for metric {}", 
                            projectName, hostname + ":" + port, metric);
                    continue;
                }
                
                // Count the data points
                dataPointsCollected += dataPoints.size();
                
                // Store the metrics if storage is enabled
                if (storeMetrics && metricsStorage != null) {
                    int stored = metricsStorage.storeMetrics(
                            projectName, hostname, port, null, metric, dataPoints);
                    result.addDataPointsStored(stored);
                }
                
                // If not in collect-only mode, process the measurements for the result
                if (!collectOnly) {
                    processMetricData(metric, dataPoints, hostname + ":" + port, 
                            projectName, projectId);
                }
            }
        } catch (Exception e) {
            logger.error("Error collecting system measurements for {}:{}: {}", 
                    hostname, port, e.getMessage());
        }
        
        return dataPointsCollected;
    }
    
    /**
     * Collect disk-level metrics
     */
    private int collectDiskMetrics(
            String projectName, String projectId, 
            String hostname, int port,
            ProjectCollectionResult result) {
        
        // Filter for disk metrics only
        List<String> diskMetrics = metrics.stream()
                .filter(m -> m.startsWith("DISK_"))
                .collect(Collectors.toList());
        
        if (diskMetrics.isEmpty()) {
            return 0; // Skip if no disk metrics requested
        }
        
        int dataPointsCollected = 0;
        
        try {
            // Get all disk partitions
            List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
            
            if (disks.isEmpty()) {
                logger.warn("No disk partitions found for process {}:{}", hostname, port);
                return 0;
            }
            
            // Process each partition
            for (Map<String, Object> disk : disks) {
                String partitionName = (String) disk.get("partitionName");
                
                try {
                    // Get measurements for this disk partition
                    List<Map<String, Object>> measurements = 
                            apiClient.getDiskMeasurements(projectId, hostname, port, 
                                    partitionName, diskMetrics, granularity, period);
                    
                    if (measurements == null || measurements.isEmpty()) {
                        logger.warn("{} {}:{} partition {} -> No disk measurements found", 
                                projectName, hostname, port, partitionName);
                        continue;
                    }
                    
                    // Process each measurement
                    for (Map<String, Object> measurement : measurements) {
                        String metric = (String) measurement.get("name");
                        List<Map<String, Object>> dataPoints = 
                                (List<Map<String, Object>>) measurement.get("dataPoints");
                        
                        if (dataPoints == null || dataPoints.isEmpty()) {
                            logger.warn("{} {}:{} partition {} -> No data points found for metric {}", 
                                    projectName, hostname, port, partitionName, metric);
                            continue;
                        }
                        
                        // Count the data points
                        dataPointsCollected += dataPoints.size();
                        
                        // Store the metrics if storage is enabled
                        if (storeMetrics && metricsStorage != null) {
                            int stored = metricsStorage.storeMetrics(projectName, hostname, port, partitionName, metric, dataPoints);
                            result.addDataPointsStored(stored);
                        }
                        
                        // If not in collect-only mode, process the measurements for the result
                        if (!collectOnly) {
                            String location = hostname + ":" + port + ", partition: " + partitionName;
                            processMetricData(metric, dataPoints, location, projectName, projectId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error collecting disk measurements for {}:{} partition {}: {}", 
                            hostname, port, partitionName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get disk partitions for process {}:{}: {}", 
                    hostname, port, e.getMessage());
        }
        
        return dataPointsCollected;
    }
    
    /**
     * Process metric data and add to project results if not in collect-only mode
     */
    private void processMetricData(String metric, List<Map<String, Object>> dataPoints,
                                 String location, String projectName, String projectId) {
        if (collectOnly) {
            return; // Skip processing in collect-only mode
        }
        
        // Extract values from data points
        List<Double> values = MetricsUtils.extractDataPointValues(dataPoints);
        
        if (!values.isEmpty()) {
            // Get the project result object
            ProjectMetricsResult projectResult = null;
            
            // Calculate statistics from this batch of values
            ProcessingResult result = MetricsUtils.processValues(values);
            
            // Add to project result if we have one
            if (projectResult != null) {
                projectResult.addMeasurement(metric, result.getMaxValue(), location);
            }
        }
    }
    
    /**
     * Reset collection statistics
     */
    private void resetCollectionStats() {
        totalProcessesScanned = 0;
        totalDataPointsCollected = 0;
        totalDataPointsStored = 0;
        projectDataPoints.clear();
    }
    
    /**
     * Log collection statistics
     */
    private void logCollectionStats() {
        logger.info("Collection statistics:");
        logger.info("  Total processes scanned: {}", totalProcessesScanned);
        logger.info("  Total data points collected: {}", totalDataPointsCollected);
        
        if (storeMetrics) {
            logger.info("  Total data points stored: {}", totalDataPointsStored);
            logger.info("  Storage efficiency: {}%", 
                    totalDataPointsCollected > 0 ? 
                            Math.round((double) totalDataPointsStored / totalDataPointsCollected * 100) : 0);
        }
        
        // Log per-project stats
        for (Map.Entry<String, Integer> entry : projectDataPoints.entrySet()) {
            logger.info("  Project {}: {} data points", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Class to track collection statistics for a project
     */
    private static class ProjectCollectionResult {
        private final String projectName;
        private final String projectId;
        private int processCount;
        private int dataPointsCollected;
        private int dataPointsStored;
        
        public ProjectCollectionResult(String projectName, String projectId) {
            this.projectName = projectName;
            this.projectId = projectId;
            this.processCount = 0;
            this.dataPointsCollected = 0;
            this.dataPointsStored = 0;
        }
        
        public String getProjectName() {
            return projectName;
        }
        
        public String getProjectId() {
            return projectId;
        }
        
        public int getProcessCount() {
            return processCount;
        }
        
        public void setProcessCount(int processCount) {
            this.processCount = processCount;
        }
        
        public int getDataPointsCollected() {
            return dataPointsCollected;
        }
        
        public void addDataPointsCollected(int points) {
            this.dataPointsCollected += points;
        }
        
        public int getDataPointsStored() {
            return dataPointsStored;
        }
        
        public void addDataPointsStored(int points) {
            this.dataPointsStored += points;
        }
    }
}