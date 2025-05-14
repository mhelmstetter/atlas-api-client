package com.helmstetter.atlas.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helmstetter.atlas.client.PatternAnalyzer.PatternResult;
import com.helmstetter.atlas.client.PatternAnalyzer.PatternType;

/**
 * Processes metrics data from MongoDB Atlas API
 */
public class MetricsProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsProcessor.class);
    
    private final AtlasApiClient apiClient;
    private final List<String> metrics;
    private final String period;
    private final String granularity;
    private final PatternAnalyzer patternAnalyzer;
    private final boolean analyzePatterns;
    
    public MetricsProcessor(AtlasApiClient apiClient, List<String> metrics, String period, String granularity) {
        this(apiClient, metrics, period, granularity, false);
    }
    
    public MetricsProcessor(AtlasApiClient apiClient, List<String> metrics, String period, 
            String granularity, boolean analyzePatterns) {
        this.apiClient = apiClient;
        this.metrics = metrics;
        this.period = period;
        this.granularity = granularity;
        this.patternAnalyzer = new PatternAnalyzer();
        this.analyzePatterns = analyzePatterns;
    }
    
    /**
     * Process metrics for all specified projects
     * 
     * @param includeProjectNames Project names to include
     * @return Map of project names to their metric results
     */
    public Map<String, ProjectMetricsResult> processProjectMetrics(Set<String> includeProjectNames) {
        // Get projects matching the specified names
        Map<String, String> projectMap = apiClient.getProjects(includeProjectNames);
        
        logger.info("Beginning to process {} projects with metrics: {}", 
                projectMap.size(), String.join(", ", metrics));
        
        // Create results map
        Map<String, ProjectMetricsResult> results = new HashMap<>();
        
        // Process each project
        for (Map.Entry<String, String> entry : projectMap.entrySet()) {
            String projectName = entry.getKey();
            String projectId = entry.getValue();
            
            try {
                // Create a result object for this project
                ProjectMetricsResult projectResult = new ProjectMetricsResult(projectName, projectId);
                
                // Initialize metrics
                for (String metric : metrics) {
                    projectResult.initializeMetric(metric);
                }
                
                // Process the project
                processProject(projectResult);
                
                // Calculate final averages
                projectResult.calculateAverages();
                
                // Add to results map
                results.put(projectName, projectResult);
                
                // Log project summary
                logProjectSummary(projectResult);
            } catch (Exception e) {
                logger.error("Error processing project {}: {}", projectName, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Process a single project
     * 
     * @param projectResult The project result object to populate
     */
    private void processProject(ProjectMetricsResult projectResult) {
        String projectId = projectResult.getProjectId();
        String projectName = projectResult.getProjectName();
        
        try {
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            logger.info("Processing project: {} with {} processes", projectName, processes.size());
            
            // Process each MongoDB instance
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                
                try {
                    // Process system metrics
                    processSystemMetrics(projectResult, hostname, port);
                    
                    // Process disk metrics if requested
                    if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
                        processDiskMetrics(projectResult, hostname, port);
                    }
                } catch (Exception e) {
                    // Log error but continue with other processes
                    logger.error("Error getting measurements for process {}:{} in project {}: {}", 
                            hostname, port, projectName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing project {}: {}", projectName, e.getMessage());
        }
    }
    
    /**
     * Process system-level metrics (CPU, memory)
     */
    private void processSystemMetrics(
            ProjectMetricsResult projectResult, 
            String hostname, int port) {
        
        // Only include non-disk metrics
        List<String> systemMetrics = metrics.stream()
                .filter(m -> !m.startsWith("DISK_"))
                .collect(Collectors.toList());
        
        if (systemMetrics.isEmpty()) {
            return; // Skip if no system metrics requested
        }
        
        try {
            // Get measurements for this process
            List<Map<String, Object>> measurements = 
                    apiClient.getProcessMeasurements(projectResult.getProjectId(), 
                            hostname, port, systemMetrics, granularity, period);
            
            if (measurements == null || measurements.isEmpty()) {
                logger.warn("{} {} -> No measurements data found", 
                        projectResult.getProjectName(), hostname + ":" + port);
                return;
            }
            
            // Process each measurement
            String location = hostname + ":" + port;
            processMeasurements(projectResult, measurements, location);
            
            // Analyze patterns if enabled
            if (analyzePatterns) {
                for (Map<String, Object> measurement : measurements) {
                    String name = (String) measurement.get("name");
                    
                    // Skip metrics that are not in our requested metrics list
                    if (!metrics.contains(name)) {
                        continue;
                    }
                    
                    List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                    if (dataPoints != null && !dataPoints.isEmpty()) {
                        // Extract values for pattern analysis
                        List<Double> values = extractDataPointValues(dataPoints);
                        
                        // Analyze pattern
                        PatternResult patternResult = patternAnalyzer.analyzePattern(values);
                        
                        // Store pattern result
                        projectResult.addPatternResult(name, location, patternResult);
                        
                        logger.info("{} {} -> Pattern: {}", 
                                projectResult.getProjectName(), name, patternResult);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting system measurements for {}:{}: {}", 
                    hostname, port, e.getMessage());
        }
    }
    
    /**
     * Process disk-level metrics
     */
    private void processDiskMetrics(
            ProjectMetricsResult projectResult, 
            String hostname, int port) {
        
        String projectId = projectResult.getProjectId();
        
        // First get the disk partitions
        List<Map<String, Object>> disks;
        try {
            disks = apiClient.getProcessDisks(projectId, hostname, port);
            if (disks.isEmpty()) {
                logger.warn("No disk partitions found for process {}:{}", hostname, port);
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to get disk partitions for process {}:{}: {}", 
                    hostname, port, e.getMessage());
            return;
        }
        
        // Filter for disk metrics only
        List<String> diskMetrics = metrics.stream()
                .filter(m -> m.startsWith("DISK_"))
                .collect(Collectors.toList());
        
        if (diskMetrics.isEmpty()) {
            return; // Skip if no disk metrics requested
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
                            projectResult.getProjectName(), hostname, port, partitionName);
                    continue;
                }
                
                // Process the measurements
                String location = hostname + ":" + port + ", partition: " + partitionName;
                processMeasurements(projectResult, measurements, location);
                
                // Analyze patterns if enabled
                if (analyzePatterns) {
                    for (Map<String, Object> measurement : measurements) {
                        String name = (String) measurement.get("name");
                        
                        // Skip metrics that are not in our requested metrics list
                        if (!diskMetrics.contains(name)) {
                            continue;
                        }
                        
                        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                        if (dataPoints != null && !dataPoints.isEmpty()) {
                            // Extract values for pattern analysis
                            List<Double> values = extractDataPointValues(dataPoints);
                            
                            // Analyze pattern
                            PatternResult patternResult = patternAnalyzer.analyzePattern(values);
                            
                            // Store pattern result
                            projectResult.addPatternResult(name, location, patternResult);
                            
                            logger.info("{} {} {} -> Pattern: {}", 
                                    projectResult.getProjectName(), name, partitionName, patternResult);
                        }
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                        hostname, port, partitionName, e.getMessage());
            }
        }
    }
    
    /**
     * Process a set of measurements and add them to the project result
     */
    private void processMeasurements(
            ProjectMetricsResult projectResult, 
            List<Map<String, Object>> measurements, 
            String location) {
        
        for (Map<String, Object> measurement : measurements) {
            String name = (String) measurement.get("name");
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");

            if (dataPoints == null || dataPoints.isEmpty()) {
                logger.warn("{} {} -> No data points found", 
                        projectResult.getProjectName(), name);
                continue;
            }
            
            // Skip metrics that are not in our requested metrics list
            if (!metrics.contains(name)) {
                logger.debug("Skipping non-requested metric: {}", name);
                continue;
            }
            
            // Extract values from data points
            List<Double> values = extractDataPointValues(dataPoints);
            
            if (!values.isEmpty()) {
                // Calculate statistics from this batch of values
                double sum = 0.0;
                double max = Double.MIN_VALUE;
                
                for (Double value : values) {
                    sum += value;
                    if (value > max) {
                        max = value;
                    }
                }
                
                double avg = sum / values.size();
                
                // Add to project result
                projectResult.addMeasurement(name, max, location);
                
                // Convert MB to GB for memory metrics in display only
                double displayMax = max;
                if (name.equals("SYSTEM_MEMORY_USED") || name.equals("SYSTEM_MEMORY_FREE")) {
                    displayMax = max / 1024.0; // Convert MB to GB
                }
                
            } else {
                logger.warn("{} {} -> No valid data points found", 
                        projectResult.getProjectName(), name);
            }
        }
    }
    
    /**
     * Extract values from a list of data points
     */
    private List<Double> extractDataPointValues(List<Map<String, Object>> dataPoints) {
        List<Double> values = new ArrayList<>();
        
        for (Map<String, Object> dataPoint : dataPoints) {
            Object valueObj = dataPoint.get("value");
            Double value = null;
            
            // Handle different numeric types
            if (valueObj instanceof Integer) {
                value = ((Integer) valueObj).doubleValue();
            } else if (valueObj instanceof Double) {
                value = (Double) valueObj;
            } else if (valueObj instanceof Long) {
                value = ((Long) valueObj).doubleValue();
            }
            
            if (value != null) {
                values.add(value);
            }
        }
        
        return values;
    }
    
    /**
     * Log a summary of the project metrics
     */
    private void logProjectSummary(ProjectMetricsResult result) {
        logger.info("PROJECT SUMMARY - {}", result.getProjectName());
        
        for (String metric : metrics) {
            if (result.hasMetricData(metric)) {
                Double maxValue = result.getMaxValue(metric);
                Double avgValue = result.getAvgValue(metric);
                String location = result.getMaxLocation(metric);
                
                // Convert MB to GB for memory metrics in display
                Double displayMax = maxValue;
                Double displayAvg = avgValue;
                if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
                    displayMax = maxValue / 1024.0; // Convert MB to GB
                    displayAvg = avgValue / 1024.0; // Convert MB to GB
                }
                
                logger.info("  {}: avg: {}{}, max: {}{} (on {})", 
                        metric, formatValue(displayAvg), getMetricUnit(metric), 
                        formatValue(displayMax), getMetricUnit(metric), location);
                
                // Log pattern information if available
                if (analyzePatterns && result.hasPatternData(metric)) {
                    PatternType dominantPattern = result.getDominantPattern(metric);
                    Map<PatternType, Integer> patternCounts = result.countPatternTypes(metric);
                    
                    logger.info("  {} pattern: {} ({})", metric, dominantPattern.getDescription(),
                            formatPatternCounts(patternCounts));
                }
            } else {
                logger.warn("  {}: No valid measurements found", metric);
            }
        }
    }
    
    /**
     * Format pattern counts for display
     */
    private String formatPatternCounts(Map<PatternType, Integer> patternCounts) {
        return patternCounts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().name() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Returns the appropriate unit for a given metric
     */
    private String getMetricUnit(String metric) {
        if (metric.equals("SYSTEM_NORMALIZED_CPU_USER")) {
            return "%";
        } else if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
            return " GB";
        } else if (metric.equals("DISK_PARTITION_IOPS_TOTAL")) {
            return " IOPS";
        }
        return "";
    }
    
    /**
     * Format a double value for display (round to 2 decimal places)
     */
    private String formatValue(double value) {
        return String.format("%.2f", value);
    }
}