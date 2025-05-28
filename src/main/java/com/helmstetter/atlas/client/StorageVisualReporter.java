package com.helmstetter.atlas.client;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visual reporter that generates charts from stored MongoDB metrics data
 * Extends BaseVisualReporter with storage-specific data collection logic
 */
public class StorageVisualReporter extends BaseVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageVisualReporter.class);
    
    private final MetricsStorage metricsStorage;
    
    public StorageVisualReporter(MetricsStorage metricsStorage, String outputDirectory) {
        this(metricsStorage, outputDirectory, 600, 300, false);
    }
    
    public StorageVisualReporter(MetricsStorage metricsStorage, String outputDirectory, 
            int chartWidth, int chartHeight, boolean darkMode) {
        super(outputDirectory, chartWidth, chartHeight, darkMode);
        this.metricsStorage = metricsStorage;
    }
    
    @Override
    public void generateCombinedMetricChart(
            ProjectMetricsResult projectResult, 
            String metricName, 
            String period, 
            String granularity) {
        
        String projectName = projectResult.getProjectName();
        
        logger.info("Generating chart from storage for metric {} in project {}", metricName, projectName);
        
        try {
            // Calculate time range
            Instant endTime = Instant.now();
            Instant startTime = calculateStartTime(endTime, period);
            
            // Create dataset from stored data
            TimeSeriesCollection dataset = createDatasetFromStorage(
                    projectName, metricName, startTime, endTime);
            
            // Debug the time range
            debugTimeRange(dataset, metricName, period);
            
            // Only create chart if we have data
            if (dataset.getSeriesCount() > 0) {
                // Determine metric type for special handling
                boolean isCpuMetric = metricName.equals("SYSTEM_NORMALIZED_CPU_USER");
                boolean isMemoryMetric = metricName.equals("SYSTEM_MEMORY_USED") || 
                                         metricName.equals("SYSTEM_MEMORY_FREE");
                boolean isBytesMetric = metricName.startsWith("CACHE_BYTES") || 
                                        metricName.contains("BYTES");
                
                // Create chart
                JFreeChart chart = createChart(dataset, metricName, isCpuMetric, isMemoryMetric, isBytesMetric);
                
                // Save chart to SVG file
                String filename = String.format("%s/%s_%s_combined.svg", 
                        outputDirectory, 
                        projectName.replace(' ', '_'), 
                        metricName);
                
                // Create and save SVG
                saveSvgChart(chart, filename);
                
                logger.info("SVG Chart saved to: {}", filename);
            } else {
                logger.warn("No stored data available for {} in project {}", metricName, projectName);
            }
            
        } catch (Exception e) {
            logger.error("Error creating chart from storage for {} in project {}: {}", 
                    metricName, projectName, e.getMessage(), e);
        }
    }
    
    /**
     * Create dataset from stored data with enhanced logging
     */
    private TimeSeriesCollection createDatasetFromStorage(
            String projectName,
            String metricName,
            Instant startTime,
            Instant endTime) {
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        
        try {
            logger.info("Querying storage for project={}, metric={}, timeRange={} to {}", 
                    projectName, metricName, startTime, endTime);
            
            // Calculate expected duration
            long hours = java.time.temporal.ChronoUnit.HOURS.between(startTime, endTime);
            long days = java.time.temporal.ChronoUnit.DAYS.between(startTime, endTime);
            logger.info("Expected data duration: {} hours ({} days)", hours, days);
            
            // Get all stored data for this project and metric
            List<Document> documents = metricsStorage.getMetrics(
                    projectName, null, metricName, startTime, endTime);
            
            logger.info("Found {} stored data points for project {} metric {}", 
                    documents.size(), projectName, metricName);
            
            if (documents.isEmpty()) {
                logger.warn("No stored data found for project={}, metric={} in timeRange={} to {}", 
                        projectName, metricName, startTime, endTime);
                return dataset;
            }
            
            // Debug: Check actual time range of retrieved data
            Date earliestTimestamp = null;
            Date latestTimestamp = null;
            
            for (Document doc : documents) {
                Date timestamp = doc.getDate("timestamp");
                if (earliestTimestamp == null || timestamp.before(earliestTimestamp)) {
                    earliestTimestamp = timestamp;
                }
                if (latestTimestamp == null || timestamp.after(latestTimestamp)) {
                    latestTimestamp = timestamp;
                }
            }
            
            if (earliestTimestamp != null && latestTimestamp != null) {
                long actualHours = (latestTimestamp.getTime() - earliestTimestamp.getTime()) / (1000 * 60 * 60);
                logger.info("Actual data time range: {} to {} ({} hours)", 
                        earliestTimestamp, latestTimestamp, actualHours);
                
                // Check if we got the expected time range
                if (actualHours < hours * 0.8) { // If we got less than 80% of expected time
                    logger.warn("Retrieved data covers only {} hours but expected {} hours", actualHours, hours);
                }
            }
            
            // Group by host and partition (if applicable)
            Map<String, List<Document>> seriesGroups = groupDocumentsForSeries(documents);
            
            logger.info("Grouped data into {} series", seriesGroups.size());
            
            // Create time series for each group
            for (Map.Entry<String, List<Document>> entry : seriesGroups.entrySet()) {
                String seriesName = entry.getKey();
                List<Document> seriesDocuments = entry.getValue();
                
                TimeSeries timeSeries = createTimeSeriesFromDocuments(seriesName, seriesDocuments);
                
                if (timeSeries.getItemCount() > 0) {
                    dataset.addSeries(timeSeries);
                    
                    // Debug individual series time range
                    if (timeSeries.getItemCount() > 0) {
                        Date seriesStart = ((Millisecond)timeSeries.getDataItem(0).getPeriod()).getStart();
                        Date seriesEnd = ((Millisecond)timeSeries.getDataItem(timeSeries.getItemCount()-1).getPeriod()).getStart();
                        long seriesHours = (seriesEnd.getTime() - seriesStart.getTime()) / (1000 * 60 * 60);
                        
                        logger.info("Added series '{}' with {} data points, time range: {} to {} ({} hours)", 
                                seriesName, timeSeries.getItemCount(), seriesStart, seriesEnd, seriesHours);
                    }
                } else {
                    logger.warn("Series '{}' has no data points", seriesName);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error creating dataset from storage: {}", e.getMessage(), e);
        }
        
        return dataset;
    }
    
    /**
     * Group documents for creating separate time series
     */
    private Map<String, List<Document>> groupDocumentsForSeries(List<Document> documents) {
        return documents.stream()
                .collect(Collectors.groupingBy(doc -> {
                    Document metadata = doc.get("metadata", Document.class);
                    String host = metadata.getString("host");
                    String partition = metadata.getString("partition");
                    
                    // Create series name
                    if (partition != null && !partition.isEmpty()) {
                        return host + ":" + partition;
                    } else {
                        return host;
                    }
                }));
    }
    
    /**
     * Create a time series from a list of documents
     */
    private TimeSeries createTimeSeriesFromDocuments(String seriesName, List<Document> documents) {
        TimeSeries timeSeries = new TimeSeries(seriesName);
        
        // Sort documents by timestamp
        documents.sort((d1, d2) -> d1.getDate("timestamp").compareTo(d2.getDate("timestamp")));
        
        // Add data points
        for (Document doc : documents) {
            Date timestamp = doc.getDate("timestamp");
            Object valueObj = doc.get("value");
            
            if (valueObj instanceof Number) {
                double value = ((Number) valueObj).doubleValue();
                timeSeries.add(new Millisecond(timestamp), value);
            }
        }
        
        return timeSeries;
    }
}