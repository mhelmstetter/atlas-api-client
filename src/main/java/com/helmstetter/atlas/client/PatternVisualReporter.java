package com.helmstetter.atlas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
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
     * Generate a combined chart for a specific metric across all hosts in a project
     * 
     * @param projectResult The project metrics result
     * @param metricName The metric to chart
     * @param period Time period for data retrieval
     * @param granularity Data granularity
     */
    public void generateCombinedMetricChart(
            ProjectMetricsResult projectResult, 
            String metricName, 
            String period, 
            String granularity) {
        
        String projectName = projectResult.getProjectName();
        String projectId = projectResult.getProjectId();
        
        logger.info("Generating combined chart for metric {} in project {}", metricName, projectName);
        
        try {
            // Create a dataset to hold all series
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            
            // Track whether we added any data
            boolean hasData = false;
            
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            
            // For disk metrics, we'll need to handle them differently
            boolean isDiskMetric = metricName.startsWith("DISK_");
            
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                String instanceId = hostname + ":" + port;
                
                List<Map<String, Object>> measurements;
                
                if (isDiskMetric) {
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
                            // Add this data to the combined chart
                            for (Map<String, Object> measurement : measurements) {
                                if (((String) measurement.get("name")).equals(metricName)) {
                                    addTimeSeriesData(dataset, measurement, diskId);
                                    hasData = true;
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
                        // Add this data to the combined chart
                        for (Map<String, Object> measurement : measurements) {
                            if (((String) measurement.get("name")).equals(metricName)) {
                                addTimeSeriesData(dataset, measurement, instanceId);
                                hasData = true;
                            }
                        }
                    }
                }
            }
            
            // Only create chart if we have data
            if (hasData) {
                // Create chart title
                String title = String.format("%s - %s (All Hosts)", 
                        projectResult.getProjectName(), metricName);
                
                // Create chart
                JFreeChart chart = ChartFactory.createTimeSeriesChart(
                        title,
                        "Time",
                        getMetricUnit(metricName),
                        dataset,
                        true,  // Show legend
                        true,  // Show tooltips
                        false  // No URLs
                );
                
                // Customize appearance
                XYPlot plot = chart.getXYPlot();
                
                // Set background color
                plot.setBackgroundPaint(Color.white);
                plot.setDomainGridlinePaint(Color.lightGray);
                plot.setRangeGridlinePaint(Color.lightGray);
                
                // Format date axis
                DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
                dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
                
                // Format value axis
                NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
                valueAxis.setAutoRangeIncludesZero(false); // Better view of the data
                
                // Save chart to file
                String filename = String.format("%s/%s_%s_combined.png", 
                        outputDirectory, 
                        projectResult.getProjectName().replace(' ', '_'), 
                        metricName);
                
                File chartFile = new File(filename);
                ImageIO.write(chart.createBufferedImage(1200, 800), "png", chartFile);
                
                logger.info("Combined chart saved to: {}", filename);
            } else {
                logger.warn("No data available for {} in project {}", metricName, projectName);
            }
            
        } catch (Exception e) {
            logger.error("Error creating combined chart for {} in project {}: {}", 
                    metricName, projectName, e.getMessage());
        }
    }

    /**
     * Add time series data to a dataset
     */
    private void addTimeSeriesData(
            TimeSeriesCollection dataset, 
            Map<String, Object> measurement, 
            String seriesName) {
        
        List<Map<String, Object>> dataPoints = 
                (List<Map<String, Object>>) measurement.get("dataPoints");
        
        if (dataPoints == null || dataPoints.isEmpty()) {
            return;
        }
        
        // Create time series for this host
        TimeSeries series = new TimeSeries(seriesName);
        
        // Add data points
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
                // Convert timestamp
                Date timestamp = Date.from(Instant.parse((String) dataPoint.get("timestamp")));
                series.add(new Millisecond(timestamp), value);
            }
        }
        
        // Add to dataset if it has data
        if (series.getItemCount() > 0) {
            dataset.addSeries(series);
        }
    }
    
    /**
     * Generate a dashboard with all metrics for a project on a single page
     * 
     * @param projectResult The project metrics result
     * @param period Time period for data retrieval
     * @param granularity Data granularity
     */
    public void generateProjectDashboard(
            ProjectMetricsResult projectResult, 
            String period, 
            String granularity) {
        
        String projectName = projectResult.getProjectName();
        
        logger.info("Generating dashboard for project: {}", projectName);
        
        try {
            // Get all metrics for this project
            List<String> metricsToChart = new ArrayList<>(projectResult.getMetrics());
            
            if (metricsToChart.isEmpty()) {
                logger.warn("No metrics found for project: {}", projectName);
                return;
            }
            
            // Calculate grid layout
            int numCharts = metricsToChart.size();
            int cols = Math.min(2, numCharts); // 2 columns at most
            int rows = (int) Math.ceil((double) numCharts / cols);
            
            // Create list to hold all charts
            List<JFreeChart> charts = new ArrayList<>();
            
            // Generate a chart for each metric
            for (String metricName : metricsToChart) {
                JFreeChart chart = createCombinedMetricChart(projectResult, metricName, period, granularity);
                if (chart != null) {
                    charts.add(chart);
                }
            }
            
            if (charts.isEmpty()) {
                logger.warn("No charts could be created for project: {}", projectName);
                return;
            }
            
            // Create dashboard image
            int chartWidth = 800;
            int chartHeight = 400;
            int spacing = 20;
            
            int totalWidth = cols * chartWidth + (cols + 1) * spacing;
            int totalHeight = rows * chartHeight + (rows + 1) * spacing + 50; // Extra space for title
            
            // Create a buffered image for the dashboard
            BufferedImage dashboard = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = dashboard.createGraphics();
            
            // Set white background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, totalWidth, totalHeight);
            
            // Add dashboard title
            g2d.setColor(Color.BLACK);
            Font titleFont = new Font("SansSerif", Font.BOLD, 20);
            g2d.setFont(titleFont);
            String title = projectName + " - Metrics Dashboard";
            int titleWidth = g2d.getFontMetrics().stringWidth(title);
            g2d.drawString(title, (totalWidth - titleWidth) / 2, 30);
            
            // Draw each chart in the grid
            int chartIndex = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (chartIndex < charts.size()) {
                        int x = col * chartWidth + (col + 1) * spacing;
                        int y = row * chartHeight + (row + 1) * spacing + 50; // Account for title space
                        
                        // Draw the chart
                        JFreeChart chart = charts.get(chartIndex);
                        chart.draw(g2d, new Rectangle2D.Double(x, y, chartWidth, chartHeight));
                        
                        chartIndex++;
                    }
                }
            }
            
            // Clean up
            g2d.dispose();
            
            // Save dashboard image
            String filename = String.format("%s/%s_dashboard.png", 
                    outputDirectory, 
                    projectResult.getProjectName().replace(' ', '_'));
            
            ImageIO.write(dashboard, "png", new File(filename));
            
            logger.info("Project dashboard saved to: {}", filename);
            
        } catch (Exception e) {
            logger.error("Error generating dashboard for project {}: {}", 
                    projectName, e.getMessage());
        }
    }

    /**
     * Create a chart with data from all hosts for a specific metric
     * Returns the chart object instead of saving it directly
     */
    private JFreeChart createCombinedMetricChart(
            ProjectMetricsResult projectResult, 
            String metricName, 
            String period, 
            String granularity) {
        
        String projectName = projectResult.getProjectName();
        String projectId = projectResult.getProjectId();
        
        try {
            // Create a dataset to hold all series
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            
            // Track whether we added any data
            boolean hasData = false;
            
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            
            // For disk metrics, we'll need to handle them differently
            boolean isDiskMetric = metricName.startsWith("DISK_");
            
            // Limit to max 5 hosts per chart to avoid overcrowding
            int hostCount = 0;
            int MAX_HOSTS_PER_CHART = 5;
            
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                if (hostCount >= MAX_HOSTS_PER_CHART) {
                    break; // Limit number of hosts to avoid overcrowding
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                String instanceId = hostname + ":" + port;
                
                // Shorten the instance ID for better legend display
                String shortInstanceId = hostname;
                if (shortInstanceId.length() > 20) {
                    shortInstanceId = shortInstanceId.substring(0, 17) + "...";
                }
                
                List<Map<String, Object>> measurements;
                
                if (isDiskMetric) {
                    // For disk metrics, only show the first partition per host to avoid overcrowding
                    List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
                    
                    if (!disks.isEmpty()) {
                        Map<String, Object> firstDisk = disks.get(0);
                        String partitionName = (String) firstDisk.get("partitionName");
                        
                        // Get measurements for this disk
                        measurements = apiClient.getDiskMeasurements(
                                projectId, hostname, port, partitionName,
                                List.of(metricName), granularity, period);
                        
                        if (measurements != null && !measurements.isEmpty()) {
                            // Add this data to the combined chart
                            for (Map<String, Object> measurement : measurements) {
                                if (((String) measurement.get("name")).equals(metricName)) {
                                    addTimeSeriesData(dataset, measurement, shortInstanceId + " (" + partitionName + ")");
                                    hasData = true;
                                    hostCount++;
                                    break;
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
                        // Add this data to the combined chart
                        for (Map<String, Object> measurement : measurements) {
                            if (((String) measurement.get("name")).equals(metricName)) {
                                addTimeSeriesData(dataset, measurement, shortInstanceId);
                                hasData = true;
                                hostCount++;
                                break;
                            }
                        }
                    }
                }
            }
            
            // Only create chart if we have data
            if (hasData) {
                // Create chart title
                String title = metricName;
                
                // Create chart
                JFreeChart chart = ChartFactory.createTimeSeriesChart(
                        title,
                        "Time",
                        getMetricUnit(metricName),
                        dataset,
                        true,  // Show legend
                        true,  // Show tooltips
                        false  // No URLs
                );
                
                // Customize appearance
                XYPlot plot = chart.getXYPlot();
                
                // Set background color
                plot.setBackgroundPaint(Color.white);
                plot.setDomainGridlinePaint(Color.lightGray);
                plot.setRangeGridlinePaint(Color.lightGray);
                
                // Format date axis
                DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
                dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
                
                // Format value axis
                NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
                valueAxis.setAutoRangeIncludesZero(false); // Better view of the data
                
                // Make legend more compact
                chart.getLegend().setItemFont(new Font("SansSerif", Font.PLAIN, 9));
                chart.getLegend().setBorder(0, 0, 0, 0);
                
                return chart;
            }
            
        } catch (Exception e) {
            logger.error("Error creating chart for {} in project {}: {}", 
                    metricName, projectName, e.getMessage());
        }
        
        return null;
    }

    /**
     * Generate both individual charts and combined dashboards for all projects
     * 
     * @param projectResults Map of project results
     * @param period Time period for data retrieval
     * @param granularity Data granularity
     */
    public void generateCompleteVisualization(
            Map<String, ProjectMetricsResult> projectResults,
            String period,
            String granularity) {
        
        logger.info("Generating complete visualization for {} projects", projectResults.size());
        
        // Create HTML index file
        createHtmlIndex(projectResults);
        
        // Process each project
        for (ProjectMetricsResult projectResult : projectResults.values()) {
            // Generate individual per-host charts
            generatePatternCharts(projectResult, period, granularity);
            
            // Generate combined charts for each metric
            for (String metric : projectResult.getMetrics()) {
                generateCombinedMetricChart(projectResult, metric, period, granularity);
            }
            
            // Generate dashboard for the project
            generateProjectDashboard(projectResult, period, granularity);
        }
        
        logger.info("Complete visualization generated in directory: {}", outputDirectory);
    }
    
    public void createHtmlIndex(Map<String, ProjectMetricsResult> projectResults) {
        String indexPath = outputDirectory + "/index.html";
        
        try (FileWriter writer = new FileWriter(indexPath)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <title>MongoDB Atlas Metrics Visualization</title>\n");
            writer.write("  <style>\n");
            writer.write("    body { font-family: Arial, sans-serif; margin: 20px; }\n");
            writer.write("    h1, h2 { color: #1d3557; }\n");
            writer.write("    .dashboard { margin-bottom: 30px; }\n");
            writer.write("    .dashboard img { max-width: 100%; border: 1px solid #ddd; }\n");
            writer.write("    .metrics { display: flex; flex-wrap: wrap; gap: 15px; margin-top: 10px; }\n");
            writer.write("    .metric-card { border: 1px solid #ddd; padding: 10px; border-radius: 5px; width: 200px; }\n");
            writer.write("    .metric-card h3 { margin-top: 0; font-size: 16px; }\n");
            writer.write("    .metric-card a { color: #457b9d; text-decoration: none; }\n");
            writer.write("    .metric-card a:hover { text-decoration: underline; }\n");
            writer.write("  </style>\n");
            writer.write("</head>\n");
            writer.write("<body>\n");
            writer.write("  <h1>MongoDB Atlas Metrics Visualization</h1>\n");
            
            // Generate timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.write("  <p>Generated on: " + dateFormat.format(new Date()) + "</p>\n");
            
            // For each project
            for (ProjectMetricsResult projectResult : projectResults.values()) {
                String projectName = projectResult.getProjectName();
                String safeProjectName = projectName.replace(' ', '_');
                
                writer.write("  <div class='dashboard'>\n");
                writer.write("    <h2>" + projectName + "</h2>\n");
                
                // Dashboard link (if it exists)
                String dashboardFile = safeProjectName + "_dashboard.png";
                File dashboardFileCheck = new File(outputDirectory, dashboardFile);
                if (dashboardFileCheck.exists()) {
                    writer.write("    <h3>Project Dashboard</h3>\n");
                    writer.write("    <a href='" + dashboardFile + "'>\n");
                    writer.write("      <img src='" + dashboardFile + "' alt='Dashboard for " + projectName + "' style='max-height: 500px;'>\n");
                    writer.write("    </a>\n");
                }
                
                // Combined charts section (only show if they exist)
                boolean hasCombinedCharts = false;
                StringBuilder metricsHtml = new StringBuilder();
                metricsHtml.append("    <h3>Combined Metric Charts</h3>\n");
                metricsHtml.append("    <div class='metrics'>\n");
                
                for (String metric : projectResult.getMetrics()) {
                    String combinedChartFile = safeProjectName + "_" + metric + "_combined.png";
                    File combinedChartFileCheck = new File(outputDirectory, combinedChartFile);
                    
                    if (combinedChartFileCheck.exists()) {
                        hasCombinedCharts = true;
                        metricsHtml.append("      <div class='metric-card'>\n");
                        metricsHtml.append("        <h3>" + metric + "</h3>\n");
                        metricsHtml.append("        <a href='" + combinedChartFile + "'>View Combined Chart</a>\n");
                        metricsHtml.append("      </div>\n");
                    }
                }
                
                metricsHtml.append("    </div>\n");
                
                if (hasCombinedCharts) {
                    writer.write(metricsHtml.toString());
                }
                
                // Individual charts section (only if they exist)
                boolean hasIndividualCharts = false;
                for (String metric : projectResult.getMetrics()) {
                    // Check if any individual charts for this metric exist
                    String filePattern = safeProjectName + "_" + metric + "_*";
                    File dir = new File(outputDirectory);
                    File[] files = dir.listFiles((d, name) -> 
                        name.startsWith(safeProjectName + "_" + metric + "_") && 
                        !name.endsWith("_combined.png"));
                    
                    if (files != null && files.length > 0) {
                        hasIndividualCharts = true;
                        break;
                    }
                }
                
                if (hasIndividualCharts) {
                    writer.write("    <h3>Individual Host Charts</h3>\n");
                    writer.write("    <p>Individual charts are available for specific hosts.</p>\n");
                }
                
                writer.write("  </div>\n");
            }
            
            writer.write("</body>\n");
            writer.write("</html>\n");
            
            logger.info("HTML index created at: {}", indexPath);
        } catch (IOException e) {
            logger.error("Error creating HTML index: {}", e.getMessage());
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