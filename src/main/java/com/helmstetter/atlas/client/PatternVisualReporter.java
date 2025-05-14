package com.helmstetter.atlas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helmstetter.atlas.client.PatternAnalyzer.PatternResult;
import com.helmstetter.atlas.client.PatternAnalyzer.PatternType;

/**
 * Generates visual reports and time series charts for metric patterns
 */
public class PatternVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(PatternVisualReporter.class);
    
    private final AtlasApiClient apiClient;
    private final PatternAnalyzer patternAnalyzer;
    private final String outputDirectory;
    
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory) {
        this.apiClient = apiClient;
        this.patternAnalyzer = new PatternAnalyzer();
        this.outputDirectory = outputDirectory;
        
        // Create output directory if it doesn't exist
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * Generate time series charts for a project's metrics
     * 
     * @param projectResult The project metrics result
     * @param period Time period for data retrieval
     * @param granularity Data granularity
     */
    public void generatePatternCharts(ProjectMetricsResult projectResult, String period, String granularity) {
        String projectName = projectResult.getProjectName();
        String projectId = projectResult.getProjectId();
        
        logger.info("Generating pattern charts for project: {}", projectName);
        
        try {
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                String instanceId = hostname + ":" + port;
                
                // Process each metric for this instance
                for (String metricName : projectResult.getMetrics()) {
                    generateChartForMetric(projectResult, hostname, port, metricName, period, granularity);
                }
            }
        } catch (Exception e) {
            logger.error("Error generating charts for project {}: {}", projectName, e.getMessage());
        }
    }
    
    /**
     * Generate a chart for a specific metric on a specific host
     */
    private void generateChartForMetric(
            ProjectMetricsResult projectResult, 
            String hostname, 
            int port, 
            String metricName, 
            String period, 
            String granularity) {
        
        String projectId = projectResult.getProjectId();
        String instanceId = hostname + ":" + port;
        
        try {
            List<Map<String, Object>> measurements;
            
            // Handle disk metrics vs system metrics differently
            if (metricName.startsWith("DISK_")) {
                // Get all disk partitions
                List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
                
                for (Map<String, Object> disk : disks) {
                    String partitionName = (String) disk.get("partitionName");
                    String diskId = instanceId + ":" + partitionName;
                    
                    // Get measurements for this disk
                    measurements = apiClient.getDiskMeasurements(
                            projectId, hostname, port, partitionName,
                            List.of(metricName), granularity, period);
                    
                    if (measurements != null && !measurements.isEmpty()) {
                        // Create chart for this partition
                        for (Map<String, Object> measurement : measurements) {
                            if (((String) measurement.get("name")).equals(metricName)) {
                                createTimeSeriesChart(
                                        projectResult, 
                                        metricName, 
                                        diskId,
                                        measurement);
                            }
                        }
                    }
                }
            } else {
                // Get measurements for this process
                measurements = apiClient.getProcessMeasurements(
                        projectId, hostname, port,
                        List.of(metricName), granularity, period);
                
                if (measurements != null && !measurements.isEmpty()) {
                    // Create chart
                    for (Map<String, Object> measurement : measurements) {
                        if (((String) measurement.get("name")).equals(metricName)) {
                            createTimeSeriesChart(
                                    projectResult, 
                                    metricName, 
                                    instanceId,
                                    measurement);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error generating chart for {} on {}: {}", 
                    metricName, instanceId, e.getMessage());
        }
    }
    
    /**
     * Create and save a time series chart for a measurement
     */
    private void createTimeSeriesChart(
            ProjectMetricsResult projectResult,
            String metricName,
            String instanceId,
            Map<String, Object> measurement) {
        
        try {
            List<Map<String, Object>> dataPoints = 
                    (List<Map<String, Object>>) measurement.get("dataPoints");
            
            if (dataPoints == null || dataPoints.isEmpty()) {
                logger.warn("No data points for {} on {}", metricName, instanceId);
                return;
            }
            
            // Extract values and timestamps
            List<Double> values = new ArrayList<>();
            List<Date> timestamps = new ArrayList<>();
            
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
                    
                    // Convert timestamp
                    Date timestamp = Date.from(Instant.parse((String) dataPoint.get("timestamp")));
                    timestamps.add(timestamp);
                }
            }
            
            // Create time series for chart
            TimeSeries series = new TimeSeries(metricName);
            
            for (int i = 0; i < values.size(); i++) {
                series.add(new Millisecond(timestamps.get(i)), values.get(i));
            }
            
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            
            // Analyze pattern
            PatternResult patternResult = patternAnalyzer.analyzePattern(values);
            String patternDescription = patternResult.getPatternType().getDescription();
            
            // Create chart title with pattern info
            String title = String.format("%s - %s on %s\nPattern: %s", 
                    projectResult.getProjectName(), metricName, instanceId, patternDescription);
            
            // Create chart
            JFreeChart chart = ChartFactory.createTimeSeriesChart(
                    title,
                    "Time",
                    getMetricUnit(metricName),
                    dataset,
                    true,
                    true,
                    false);
            
            // Customize appearance
            XYPlot plot = chart.getXYPlot();
            
            // Set background color
            plot.setBackgroundPaint(Color.white);
            plot.setDomainGridlinePaint(Color.lightGray);
            plot.setRangeGridlinePaint(Color.lightGray);
            
            // Set line style based on pattern type
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            
            // Set color based on pattern type
            Color lineColor = getColorForPattern(patternResult.getPatternType());
            renderer.setSeriesPaint(0, lineColor);
            renderer.setSeriesStroke(0, new BasicStroke(2.0f));
            renderer.setSeriesShapesVisible(0, false);
            plot.setRenderer(renderer);
            
            // Format date axis
            DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
            dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
            
            // Format value axis
            NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
            valueAxis.setAutoRangeIncludesZero(false); // Better view of the data
            
            // Save chart to file
            String sanitizedInstanceId = instanceId.replace(':', '_').replace(',', '_').replace(' ', '_');
            String filename = String.format("%s/%s_%s_%s.png", 
                    outputDirectory, 
                    projectResult.getProjectName().replace(' ', '_'), 
                    metricName, 
                    sanitizedInstanceId);
            
            File chartFile = new File(filename);
            ImageIO.write(chart.createBufferedImage(800, 600), "png", chartFile);
            
            logger.info("Chart saved to: {}", filename);
            
        } catch (IOException e) {
            logger.error("Error creating chart for {} on {}: {}", 
                    metricName, instanceId, e.getMessage());
        }
    }
    
    /**
     * Get color based on pattern type
     */
    private Color getColorForPattern(PatternType patternType) {
        switch (patternType) {
            case FLAT:
                return new Color(0, 128, 0); // Green
            case SPIKY:
                return new Color(255, 0, 0); // Red
            case TRENDING_UP:
                return new Color(0, 0, 255); // Blue
            case TRENDING_DOWN:
                return new Color(128, 0, 128); // Purple
            case SAWTOOTH:
                return new Color(255, 165, 0); // Orange
            default:
                return new Color(100, 100, 100); // Gray
        }
    }
    
    /**
     * Returns the appropriate unit for a given metric
     */
    private String getMetricUnit(String metric) {
        if (metric.equals("SYSTEM_NORMALIZED_CPU_USER")) {
            return "CPU (%)";
        } else if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
            return "Memory (MB)";
        } else if (metric.equals("DISK_PARTITION_IOPS_TOTAL")) {
            return "IOPS";
        }
        return "Value";
    }
    
    /**
     * Get all metric names tracked in a project result
     */
    public List<String> getMetrics(ProjectMetricsResult projectResult) {
        return new ArrayList<>(projectResult.getMetrics());
    }
}