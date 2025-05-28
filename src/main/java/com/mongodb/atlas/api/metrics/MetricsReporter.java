package com.mongodb.atlas.api.metrics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.util.MetricsUtils;

/**
 * Generates reports from stored metrics data in MongoDB
 * Provides the same functionality as the API-based reporting but reads from stored data
 */
public class MetricsReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsReporter.class);
    
    private final MetricsStorage metricsStorage;
    private final List<String> metrics;
    private final PatternAnalyzer patternAnalyzer;
    private final boolean analyzePatterns;
    
    public MetricsReporter(MetricsStorage metricsStorage, List<String> metrics) {
        this(metricsStorage, metrics, false);
    }
    
    public MetricsReporter(MetricsStorage metricsStorage, List<String> metrics, boolean analyzePatterns) {
        this.metricsStorage = metricsStorage;
        this.metrics = metrics;
        this.analyzePatterns = analyzePatterns;
        this.patternAnalyzer = analyzePatterns ? new PatternAnalyzer() : null;
    }
    
    /**
     * Generate project metrics results from stored data
     * 
     * @param projectNames Set of project names to include in the report
     * @param period Time period to analyze (e.g., "P7D", "PT24H"), or null to use all available data
     * @return Map of project names to their metric results
     */
    public Map<String, ProjectMetricsResult> generateProjectMetricsReport(
            Set<String> projectNames, String period) {
        
        // Calculate time range
        Instant endTime = Instant.now();
        Instant startTime;
        
        if (period != null) {
            startTime = MetricsUtils.calculateStartTime(endTime, period);
            logger.info("Generating metrics report for {} projects from {} to {}", 
                    projectNames.size(), startTime, endTime);
        } else {
            // Use all available data - find the earliest timestamp across all projects and metrics
            startTime = findEarliestDataTimestamp(projectNames);
            logger.info("Generating metrics report for {} projects using all available data from {} to {}", 
                    projectNames.size(), startTime, endTime);
        }
        
        Map<String, ProjectMetricsResult> results = new HashMap<>();
        
        // Process each project
        for (String projectName : projectNames) {
            try {
                logger.info("Processing project: {}", projectName);
                
                // Create project result object
                ProjectMetricsResult projectResult = new ProjectMetricsResult(projectName, "from-storage");
                
                // Initialize metrics
                metrics.forEach(projectResult::initializeMetric);
                
                // Process each metric for this project
                for (String metric : metrics) {
                    processMetricForProject(projectResult, projectName, metric, startTime, endTime);
                }
                
                // Calculate final averages
                projectResult.calculateAverages();
                
                results.put(projectName, projectResult);
                
                // Log project summary
                logProjectSummary(projectResult);
                
            } catch (Exception e) {
                logger.error("Error processing project {}: {}", projectName, e.getMessage(), e);
            }
        }
        
        return results;
    }
    
    /**
     * Find the earliest data timestamp across all specified projects and metrics
     */
    private Instant findEarliestDataTimestamp(Set<String> projectNames) {
        Instant earliestOverall = Instant.now(); // Start with current time
        
        for (String projectName : projectNames) {
            for (String metric : metrics) {
                try {
                    Instant earliest = metricsStorage.getEarliestDataTime(projectName, null, metric);
                    if (!earliest.equals(Instant.EPOCH) && earliest.isBefore(earliestOverall)) {
                        earliestOverall = earliest;
                    }
                } catch (Exception e) {
                    logger.warn("Error finding earliest timestamp for project {} metric {}: {}", 
                            projectName, metric, e.getMessage());
                }
            }
        }
        
        logger.info("Earliest data timestamp found: {}", earliestOverall);
        return earliestOverall;
    }
    
    /**
     * Process a specific metric for a project
     */
    private void processMetricForProject(
            ProjectMetricsResult projectResult, 
            String projectName, 
            String metric,
            Instant startTime, 
            Instant endTime) {
        
        try {
            // Get all metrics data for this project and metric
            List<Document> documents = metricsStorage.getMetrics(
                    projectName, null, metric, startTime, endTime);
            
            if (documents.isEmpty()) {
                logger.debug("No data found for project {} metric {}", projectName, metric);
                return;
            }
            
            logger.info("Found {} data points for project {} metric {}", 
                    documents.size(), projectName, metric);
            
            // Group by host for processing
            Map<String, List<Document>> hostGroups = groupDocumentsByHost(documents);
            
            // Process each host's data
            for (Map.Entry<String, List<Document>> entry : hostGroups.entrySet()) {
                String host = entry.getKey();
                List<Document> hostDocuments = entry.getValue();
                
                // Check if this is disk data (has partition info)
                boolean isDiskData = hostDocuments.stream()
                        .anyMatch(doc -> doc.get("metadata", Document.class).containsKey("partition"));
                
                if (isDiskData) {
                    processDiskDataForHost(projectResult, host, metric, hostDocuments);
                } else {
                    processSystemDataForHost(projectResult, host, metric, hostDocuments);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing metric {} for project {}: {}", 
                    metric, projectName, e.getMessage(), e);
        }
    }
    
    /**
     * Process system-level data for a host
     */
    private void processSystemDataForHost(
            ProjectMetricsResult projectResult,
            String host,
            String metric,
            List<Document> documents) {
        
        // Extract values
        List<Double> values = extractValuesFromDocuments(documents);
        
        if (!values.isEmpty()) {
            // Calculate statistics
            ProcessingResult result = MetricsUtils.processValues(values);
            
            // Add to project result
            projectResult.addMeasurement(metric, result.getMaxValue(), host);
            
            // Analyze patterns if enabled
            if (analyzePatterns && patternAnalyzer != null) {
                PatternAnalyzer.PatternResult patternResult = patternAnalyzer.analyzePattern(values);
                projectResult.addPatternResult(metric, host, patternResult);
            }
            
            logger.debug("Processed {} values for host {} metric {}: max={}, avg={}", 
                    values.size(), host, metric, result.getMaxValue(), result.getAvgValue());
        }
    }
    
    /**
     * Process disk-level data for a host
     */
    private void processDiskDataForHost(
            ProjectMetricsResult projectResult,
            String host,
            String metric,
            List<Document> documents) {
        
        // Group by partition
        Map<String, List<Document>> partitionGroups = groupDocumentsByPartition(documents);
        
        // Process each partition
        for (Map.Entry<String, List<Document>> entry : partitionGroups.entrySet()) {
            String partition = entry.getKey();
            List<Document> partitionDocuments = entry.getValue();
            
            // Extract values
            List<Double> values = extractValuesFromDocuments(partitionDocuments);
            
            if (!values.isEmpty()) {
                // Calculate statistics
                ProcessingResult result = MetricsUtils.processValues(values);
                
                // Create location string
                String location = host + ", partition: " + partition;
                
                // Add to project result
                projectResult.addMeasurement(metric, result.getMaxValue(), location);
                
                // Analyze patterns if enabled
                if (analyzePatterns && patternAnalyzer != null) {
                    PatternAnalyzer.PatternResult patternResult = patternAnalyzer.analyzePattern(values);
                    projectResult.addPatternResult(metric, location, patternResult);
                }
                
                logger.debug("Processed {} values for host {} partition {} metric {}: max={}, avg={}", 
                        values.size(), host, partition, metric, result.getMaxValue(), result.getAvgValue());
            }
        }
    }
    
    /**
     * Group documents by host
     */
    private Map<String, List<Document>> groupDocumentsByHost(List<Document> documents) {
        return documents.stream()
                .collect(Collectors.groupingBy(doc -> 
                    doc.get("metadata", Document.class).getString("host")));
    }
    
    /**
     * Group documents by partition
     */
    private Map<String, List<Document>> groupDocumentsByPartition(List<Document> documents) {
        return documents.stream()
                .collect(Collectors.groupingBy(doc -> {
                    Document metadata = doc.get("metadata", Document.class);
                    return metadata.getString("partition");
                }));
    }
    
    /**
     * Extract numeric values from documents
     */
    private List<Double> extractValuesFromDocuments(List<Document> documents) {
        List<Double> values = new ArrayList<>();
        
        for (Document doc : documents) {
            Object valueObj = doc.get("value");
            
            if (valueObj instanceof Number) {
                values.add(((Number) valueObj).doubleValue());
            }
        }
        
        return values;
    }
    
    /**
     * Log a summary of the project metrics
     */
    private void logProjectSummary(ProjectMetricsResult result) {
        logger.info("PROJECT SUMMARY (from storage) - {}", result.getProjectName());
        
        for (String metric : metrics) {
            if (result.hasMetricData(metric)) {
                Double maxValue = result.getMaxValue(metric);
                Double avgValue = result.getAvgValue(metric);
                String location = result.getMaxLocation(metric);
                
                // Convert to display units
                double displayMax = MetricsUtils.convertToDisplayUnits(metric, maxValue);
                double displayAvg = MetricsUtils.convertToDisplayUnits(metric, avgValue);
                
                logger.info("  {}: avg: {}{}, max: {}{} (on {})", 
                        metric, 
                        MetricsUtils.formatValue(displayAvg), MetricsUtils.getMetricUnit(metric), 
                        MetricsUtils.formatValue(displayMax), MetricsUtils.getMetricUnit(metric), 
                        location);
                
                // Log pattern information if available
                if (analyzePatterns && result.hasPatternData(metric)) {
                    PatternAnalyzer.PatternType dominantPattern = result.getDominantPattern(metric);
                    Map<PatternAnalyzer.PatternType, Integer> patternCounts = result.countPatternTypes(metric);
                    
                    logger.info("  {} pattern: {} ({})", metric, dominantPattern.getDescription(),
                            formatPatternCounts(patternCounts));
                }
            } else {
                logger.warn("  {}: No data found in storage", metric);
            }
        }
    }
    
    /**
     * Format pattern counts for display
     */
    private String formatPatternCounts(Map<PatternAnalyzer.PatternType, Integer> patternCounts) {
        return patternCounts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().name() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Generate a data availability report
     * Shows what data is available in storage for each project and metric
     */
    public void generateDataAvailabilityReport(Set<String> projectNames) {
        logger.info("=== DATA AVAILABILITY REPORT ===");
        
        for (String projectName : projectNames) {
            logger.info("Project: {}", projectName);
            
            for (String metric : metrics) {
                try {
                    // Get earliest and latest data times
                    Instant earliest = metricsStorage.getEarliestDataTime(projectName, null, metric);
                    Instant latest = metricsStorage.getLatestDataTime(projectName, null, metric);
                    
                    if (!earliest.equals(Instant.EPOCH) && !latest.equals(Instant.EPOCH)) {
                        long daysBetween = ChronoUnit.DAYS.between(earliest, latest);
                        
                        // Get total data points
                        List<Document> allData = metricsStorage.getMetrics(
                                projectName, null, metric, earliest, latest);
                        
                        // Group by host to show per-host data
                        Map<String, List<Document>> hostGroups = groupDocumentsByHost(allData);
                        
                        logger.info("  {}: {} days of data ({} to {}), {} hosts, {} total points", 
                                metric, daysBetween, earliest, latest, 
                                hostGroups.size(), allData.size());
                    } else {
                        logger.info("  {}: No data available", metric);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error checking data availability for {} {}: {}", 
                            projectName, metric, e.getMessage());
                }
            }
        }
    }
}