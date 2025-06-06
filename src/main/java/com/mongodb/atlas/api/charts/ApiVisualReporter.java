package com.mongodb.atlas.api.charts;

import java.util.List;
import java.util.Map;

import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.metrics.ProjectMetricsResult;

/**
 * Visual reporter that generates charts from MongoDB Atlas API data
 * Extends BaseVisualReporter with API-specific data collection logic
 */
public class ApiVisualReporter extends BaseVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiVisualReporter.class);
    
    private final AtlasApiClient apiClient;
    
    public ApiVisualReporter(AtlasApiClient apiClient, String outputDirectory) {
        this(apiClient, outputDirectory, 600, 300, false);
    }
    
    public ApiVisualReporter(AtlasApiClient apiClient, String outputDirectory, 
            int chartWidth, int chartHeight, boolean darkMode) {
        super(outputDirectory, chartWidth, chartHeight, darkMode);
        this.apiClient = apiClient;
    }
    
    @Override
    public void generateCombinedMetricChart(
            ProjectMetricsResult projectResult, 
            String metricName, 
            String period, 
            String granularity) {
        
        String projectName = projectResult.getProjectName();
        String projectId = projectResult.getProjectId();
        
        logger.info("Generating combined chart for metric {} in project {}", metricName, projectName);
        
        try {
            // Create dataset for the chart
            TimeSeriesCollection dataset = createDatasetForMetric(projectId, metricName, period, granularity);
            
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
                        projectResult.getProjectName().replace(' ', '_'), 
                        metricName);
                
                // Create and save SVG
                saveSvgChart(chart, filename);
                
                logger.info("SVG Chart saved to: {}", filename);
            } else {
                logger.warn("No data available for {} in project {}", metricName, projectName);
            }
            
        } catch (Exception e) {
            logger.error("Error creating chart for {} in project {}: {}", 
                    metricName, projectName, e.getMessage(), e);
        }
    }
    
    /**
     * Create a dataset for a specific metric using Atlas API
     */
    private TimeSeriesCollection createDatasetForMetric(
            String projectId, 
            String metricName, 
            String period, 
            String granularity) {
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
            
        try {
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.clusters().getProcesses(projectId);
            
            // For disk metrics, we'll need to handle them differently
            boolean isDiskMetric = metricName.startsWith("DISK_");
            
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                
                if (isDiskMetric) {
                    processDiskMetric(dataset, projectId, hostname, port, metricName, granularity, period);
                } else {
                    processSystemMetric(dataset, projectId, hostname, port, metricName, granularity, period);
                }
            }
        } catch (Exception e) {
            logger.error("Error creating dataset: {}", e.getMessage());
        }
        
        return dataset;
    }
    
    /**
     * Process system-level metrics with proper API method selection
     */
    private void processSystemMetric(
            TimeSeriesCollection dataset,
            String projectId,
            String hostname,
            int port,
            String metricName,
            String granularity,
            String period) {
        
        try {
            // Apply same logic as MetricsProcessor for method selection
            boolean useExplicitTimeRange = shouldUseExplicitTimeRange(period);
            
            List<Map<String, Object>> measurements;
            
            if (useExplicitTimeRange) {
                // Use explicit timerange method for longer periods
                measurements = apiClient.monitoring().getProcessMeasurementsWithTimeRange(
                        projectId, hostname, port,
                        List.of(metricName), granularity, period);
            } else {
                // Use standard period-based method for shorter periods
                measurements = apiClient.monitoring().getProcessMeasurements(
                        projectId, hostname, port,
                        List.of(metricName), granularity, period);
            }
            
            if (measurements != null && !measurements.isEmpty()) {
                // Add this data to the combined chart
                for (Map<String, Object> measurement : measurements) {
                    if (((String) measurement.get("name")).equals(metricName)) {
                        addTimeSeriesData(dataset, measurement, "");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing system metric data: {}", e.getMessage());
        }
    }
    
    /**
     * Process disk-level metrics with proper API method selection
     */
    private void processDiskMetric(
            TimeSeriesCollection dataset,
            String projectId,
            String hostname,
            int port,
            String metricName,
            String granularity,
            String period) {
        
        try {
            // Get all disk partitions
            List<Map<String, Object>> disks = apiClient.monitoring().getProcessDisks(projectId, hostname, port);
            
            // Apply same logic as MetricsProcessor for method selection
            boolean useExplicitTimeRange = shouldUseExplicitTimeRange(period);
            
            for (Map<String, Object> disk : disks) {
                String partitionName = (String) disk.get("partitionName");
                
                List<Map<String, Object>> measurements;
                
                if (useExplicitTimeRange) {
                    // Use explicit timerange method for longer periods (string period version)
                    measurements = apiClient.monitoring().getDiskMeasurementsWithTimeRange(
                            projectId, hostname, port, partitionName,
                            List.of(metricName), granularity, period);
                } else {
                    // Use standard period-based method for shorter periods  
                    measurements = apiClient.monitoring().getDiskMeasurements(
                            projectId, hostname, port, partitionName,
                            List.of(metricName), granularity, period);
                }
                
                if (measurements != null && !measurements.isEmpty()) {
                    // Add this data to the combined chart
                    for (Map<String, Object> measurement : measurements) {
                        if (((String) measurement.get("name")).equals(metricName)) {
                            addTimeSeriesData(dataset, measurement, "");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing disk metric data: {}", e.getMessage());
        }
    }
}