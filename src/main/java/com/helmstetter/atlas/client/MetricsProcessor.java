package com.helmstetter.atlas.client;

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
 * Enhanced with API usage tracking
 */
public class MetricsProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsProcessor.class);
    
    private final AtlasApiClient apiClient;
    private final List<String> metrics;
    private final String period;
    private final String granularity;
    private final PatternAnalyzer patternAnalyzer;
    private final boolean analyzePatterns;
    
    // Automatically determined - no need for explicit flag
    private final boolean useExplicitTimeRange;
    private final int periodDays;
    
    // Threshold for using explicit timerange (in hours)
    private static final int TIMERANGE_THRESHOLD_HOURS = 48;
    
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
        
        // Parse period and determine whether to use explicit timerange
        int days = parsePeriodToDays(period);
        this.periodDays = days;
        
        // Use explicit timerange if period is longer than threshold
        boolean useExplicit = days * 24 > TIMERANGE_THRESHOLD_HOURS;
        this.useExplicitTimeRange = useExplicit;
        
        // Log which approach we're using
        if (useExplicitTimeRange) {
            logger.info("Using explicit timerange approach with {} days lookback (period: {})", 
                    periodDays, period);
        } else {
            logger.info("Using period-based approach with period {}", period);
        }
    }
    
    /**
     * Parse an ISO 8601 duration string to days using standard Java libraries
     * 
     * @param periodStr ISO 8601 duration string (e.g., P7D, PT24H)
     * @return Number of days (or fractional days for sub-day periods)
     */
    private int parsePeriodToDays(String periodStr) {
        try {
            if (periodStr == null || periodStr.isEmpty()) {
                logger.warn("No period specified, defaulting to 7 days");
                return 7;
            }
            
            // Use java.time.Duration for parsing ISO 8601 durations
            java.time.Duration duration = java.time.Duration.parse(periodStr);
            double days = duration.toHours() / 24.0;
            
            // Special case for Period format (P1D, P7D, etc.)
            if (periodStr.startsWith("P") && !periodStr.contains("T")) {
                try {
                    java.time.Period period = java.time.Period.parse(periodStr);
                    days = period.getDays();
                    
                    // Handle years and months approximately
                    days += period.getYears() * 365;
                    days += period.getMonths() * 30;
                } catch (Exception e) {
                    // If we can't parse as Period, fall back to Duration result
                    logger.debug("Couldn't parse as Period, using Duration result: {}", days);
                }
            }
            
            // Round to nearest day, minimum 1
            int roundedDays = Math.max(1, (int)Math.round(days));
            logger.info("Parsed period {} to {} days", periodStr, roundedDays);
            return roundedDays;
            
        } catch (Exception e) {
            logger.warn("Error parsing period {}, defaulting to 7 days: {}", periodStr, e.getMessage());
            return 7;
        }
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
        
        // Log initial API request stats before processing projects
        logApiStats("Before project processing");
        
        // Create results map
        Map<String, ProjectMetricsResult> results = new HashMap<>();
        
        // Process each project
        projectMap.forEach((projectName, projectId) -> {
            try {
                logger.info("Starting processing for project: {} ({})", projectName, projectId);
                
                // Create a result object for this project
                ProjectMetricsResult projectResult = new ProjectMetricsResult(projectName, projectId);
                
                // Initialize metrics
                metrics.forEach(projectResult::initializeMetric);
                
                // Process the project
                processProject(projectResult);
                
                // Log API usage after each project
                logApiStats("After processing project " + projectName);
                
                // Calculate final averages
                projectResult.calculateAverages();
                
                // Add to results map
                results.put(projectName, projectResult);
                
                // Log project summary
                logProjectSummary(projectResult);
            } catch (Exception e) {
                logger.error("Error processing project {}: {}", projectName, e.getMessage());
            }
        });
        
        // Log final API usage stats
        logApiStats("Final statistics after all projects");
        
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
            
            // Filter out config servers and mongos instances
            List<Map<String, Object>> filteredProcesses = processes.stream()
                    .filter(process -> {
                        String typeName = (String) process.get("typeName");
                        return !typeName.startsWith("SHARD_CONFIG") && !typeName.equals("SHARD_MONGOS");
                    })
                    .collect(Collectors.toList());
            
            logger.info("Filtered to {} mongod processes for project {}", 
                    filteredProcesses.size(), projectName);
            
            // Estimate API requests for this project
            int estimatedRequests = estimateApiRequests(filteredProcesses);
            logger.info("Estimated {} API requests for project {} ({})", 
                    estimatedRequests, projectName, projectId);
            
            // Process each MongoDB instance
            int processedCount = 0;
            for (Map<String, Object> process : filteredProcesses) {
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                
                try {
                    // Process system metrics
                    processSystemMetrics(projectResult, hostname, port);
                    
                    // Process disk metrics if requested
                    if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
                        processDiskMetrics(projectResult, hostname, port);
                    }
                    
                    // Log progress periodically
                    processedCount++;
                    if (processedCount % 5 == 0 || processedCount == filteredProcesses.size()) {
                        logger.info("Project {} progress: {} of {} processes complete ({}%)",
                                projectName, processedCount, filteredProcesses.size(),
                                Math.round((double) processedCount / filteredProcesses.size() * 100));
                        // Check API stats periodically
                        logApiStats("During processing of " + projectName);
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
     * Estimate the number of API requests needed for a project
     */
    private int estimateApiRequests(List<Map<String, Object>> processes) {
        // Base request for process list
        int estimatedRequests = 1;
        
        // Each process will need at least one request for system metrics
        estimatedRequests += processes.size();
        
        // If we have disk metrics, we'll need additional requests per process
        boolean hasDiskMetrics = metrics.stream().anyMatch(m -> m.startsWith("DISK_"));
        if (hasDiskMetrics) {
            // Each process needs one request to get disk list
            estimatedRequests += processes.size();
            
            // Roughly estimate 2 disk partitions per process on average
            estimatedRequests += processes.size() * 2;
        }
        
        // Return the estimate
        return estimatedRequests;
    }
    
    /**
     * Log current API stats
     */
    private void logApiStats(String context) {
        Map<String, Object> stats = apiClient.getApiStats();
        int totalRequests = (int) stats.get("totalRequests");
        long requestsInLastMinute = (long) stats.get("requestsInLastMinute");
        
        logger.info("API STATS ({}): {} total requests, {} in last minute", 
                context, totalRequests, requestsInLastMinute);
        
        // Log project-specific counts if available
        Map<String, Integer> projectStats = (Map<String, Integer>) stats.get("projectStats");
        if (projectStats != null && !projectStats.isEmpty()) {
            for (Map.Entry<String, Integer> entry : projectStats.entrySet()) {
                logger.info("  Project {}: {} requests", entry.getKey(), entry.getValue());
            }
        }
    }
    

    /**
     * Process system-level metrics, automatically selecting the appropriate method
     * based on the period duration
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
            // Use either explicit timerange or period-based approach based on duration
            List<Map<String, Object>> measurements;
            
            if (useExplicitTimeRange) {
                // Use explicit timerange for longer periods
                measurements = apiClient.getProcessMeasurementsWithTimeRange(
                        projectResult.getProjectId(), 
                        hostname, port, 
                        systemMetrics, granularity, period);
            } else {
                // Use standard period-based approach for shorter periods
                measurements = apiClient.getProcessMeasurements(
                        projectResult.getProjectId(), 
                        hostname, port, 
                        systemMetrics, granularity, period);
            }
            
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
                analyzePatterns(projectResult, measurements, location);
            }
            
        } catch (Exception e) {
            logger.error("Error getting system measurements for {}:{}: {}", 
                    hostname, port, e.getMessage());
        }
    }
    
    /**
     * Process disk-level metrics, automatically selecting the appropriate method
     * based on the period duration
     */
    private void processDiskMetrics(
            ProjectMetricsResult projectResult, 
            String hostname, int port) {
        
        String projectId = projectResult.getProjectId();
        
        // Filter for disk metrics only
        List<String> diskMetrics = metrics.stream()
                .filter(m -> m.startsWith("DISK_"))
                .collect(Collectors.toList());
        
        if (diskMetrics.isEmpty()) {
            return; // Skip if no disk metrics requested
        }
        
        try {
            // Get all disk partitions
            List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
            
            if (disks.isEmpty()) {
                logger.warn("No disk partitions found for process {}:{}", hostname, port);
                return;
            }
            
            // Process each partition
            for (Map<String, Object> disk : disks) {
                String partitionName = (String) disk.get("partitionName");
                
                try {
                    // Use either explicit timerange or period-based approach based on duration
                    List<Map<String, Object>> measurements;
                    
                    if (useExplicitTimeRange) {
                        // Use explicit timerange for longer periods
                        measurements = apiClient.getDiskMeasurementsWithTimeRange(
                                projectId, hostname, port, partitionName,
                                diskMetrics, granularity, periodDays);
                    } else {
                        // Use standard period-based approach for shorter periods
                        measurements = apiClient.getDiskMeasurements(
                                projectId, hostname, port, partitionName,
                                diskMetrics, granularity, period);
                    }
                    
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
                        analyzePatterns(projectResult, measurements, location);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                            hostname, port, partitionName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get disk partitions for process {}:{}: {}", 
                    hostname, port, e.getMessage());
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
            List<Double> values = MetricsUtils.extractDataPointValues(dataPoints);
            
            if (!values.isEmpty()) {
                // Calculate statistics from this batch of values
                ProcessingResult result = MetricsUtils.processValues(values);
                
                // Add to project result
                projectResult.addMeasurement(name, result.getMaxValue(), location);
            } else {
                logger.warn("{} {} -> No valid data points found", 
                        projectResult.getProjectName(), name);
            }
        }
    }
    
    /**
     * Analyze patterns in measurements data
     */
    private void analyzePatterns(
            ProjectMetricsResult projectResult,
            List<Map<String, Object>> measurements,
            String location) {
        
        for (Map<String, Object> measurement : measurements) {
            String name = (String) measurement.get("name");
            
            // Skip metrics that are not in our requested metrics list
            if (!metrics.contains(name)) {
                continue;
            }
            
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            if (dataPoints != null && !dataPoints.isEmpty()) {
                // Extract values for pattern analysis
                List<Double> values = MetricsUtils.extractDataPointValues(dataPoints);
                
                // Analyze pattern
                PatternResult patternResult = patternAnalyzer.analyzePattern(values);
                
                // Store pattern result
                projectResult.addPatternResult(name, location, patternResult);
                
                logger.info("{} {} -> Pattern: {}", 
                        projectResult.getProjectName(), name, patternResult);
            }
        }
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
                        metric, MetricsUtils.formatValue(displayAvg), MetricsUtils.getMetricUnit(metric), 
                        MetricsUtils.formatValue(displayMax), MetricsUtils.getMetricUnit(metric), location);
                
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
}