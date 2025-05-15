package com.helmstetter.atlas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates charts and HTML index showing MongoDB Atlas metrics
 * This simplified version focuses on fixed scaling for CPU charts
 */
public class PatternVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(PatternVisualReporter.class);
    
    private final AtlasApiClient apiClient;
    private final String outputDirectory;
    
    // Chart sizing with default values
    private final int chartWidth;  
    private final int chartHeight;
    
    /**
     * Constructor to initialize the reporter with default chart dimensions
     */
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory) {
        this(apiClient, outputDirectory, 600, 300);
    }
    
    /**
     * Constructor to initialize the reporter with custom chart dimensions
     * 
     * @param apiClient The Atlas API client
     * @param outputDirectory The directory where charts will be saved
     * @param chartWidth The width of the generated charts in pixels
     * @param chartHeight The height of the generated charts in pixels
     */
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory, int chartWidth, int chartHeight) {
        this.apiClient = apiClient;
        this.outputDirectory = outputDirectory;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        
        // Create output directory if it doesn't exist
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * Generate a combined chart for a specific metric across all hosts in a project
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
                
                List<Map<String, Object>> measurements;
                
                if (isDiskMetric) {
                    // Get all disk partitions
                    List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
                    
                    for (Map<String, Object> disk : disks) {
                        String partitionName = (String) disk.get("partitionName");
                        
                        // Get measurements for this disk
                        measurements = apiClient.getDiskMeasurements(
                                projectId, hostname, port, partitionName,
                                List.of(metricName), granularity, period);
                        
                        if (measurements != null && !measurements.isEmpty()) {
                            // Add this data to the combined chart
                            for (Map<String, Object> measurement : measurements) {
                                if (((String) measurement.get("name")).equals(metricName)) {
                                    addTimeSeriesData(dataset, measurement, "");
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
                                addTimeSeriesData(dataset, measurement, "");
                                hasData = true;
                            }
                        }
                    }
                }
            }
            
            // Only create chart if we have data
            if (hasData) {
                // Determine if this is a CPU metric
                boolean isCpuMetric = metricName.equals("SYSTEM_NORMALIZED_CPU_USER");
                
                // Create chart
                JFreeChart chart = ChartFactory.createTimeSeriesChart(
                        metricName,          // chart title
                        null,           // x-axis label (none for compactness)
                        null,           // y-axis label (none for compactness)
                        dataset,        // data
                        false,          // no legend
                        false,          // no tooltips
                        false           // no URLs
                );
                
                // Make title more compact
                TextTitle textTitle = chart.getTitle();
                textTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
                
                // Get the plot for customization
                XYPlot plot = chart.getXYPlot();
                
                // Set background color
                plot.setBackgroundPaint(Color.white);
                plot.setDomainGridlinePaint(Color.lightGray);
                plot.setRangeGridlinePaint(Color.lightGray);
                
                // Show gridlines for better readability
                plot.setRangeGridlinesVisible(true);
                
                // Configure series appearance
                XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
                for (int i = 0; i < dataset.getSeriesCount(); i++) {
                    Color lineColor = getColorForSeriesIndex(i);
                    renderer.setSeriesPaint(i, lineColor);
                    renderer.setSeriesStroke(i, new BasicStroke(1.0f)); // Thinner lines
                    renderer.setSeriesShapesVisible(i, false);
                }
                plot.setRenderer(renderer);
                
                // Format date axis
                DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
                dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
                dateAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
                
                // Configure value axis
                NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
                valueAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
                
                // CRITICAL: Set fixed scale for CPU metrics
                if (isCpuMetric) {
                    // For CPU metrics, explicitly disable auto range and set 0-100 scale
                    valueAxis.setAutoRange(false);
                    valueAxis.setRange(0.0, 100.0);
                    logger.info("Set fixed 0-100 scale for CPU chart: {}", projectName);
                } else {
                    // For non-CPU metrics, let chart determine appropriate scale
                    valueAxis.setAutoRange(true);
                    valueAxis.setAutoRangeIncludesZero(false);
                }
                
                // Save chart to file
                String filename = String.format("%s/%s_%s_combined.png", 
                        outputDirectory, 
                        projectResult.getProjectName().replace(' ', '_'), 
                        metricName);
                
                // Write chart to file
                File chartFile = new File(filename);
                ImageIO.write(chart.createBufferedImage(chartWidth, chartHeight), "png", chartFile);
                
                // After creating the image, verify the scale was actually applied
                if (isCpuMetric) {
                    double lower = valueAxis.getLowerBound();
                    double upper = valueAxis.getUpperBound();
                    logger.info("CPU chart scale verification - Lower: {}, Upper: {}", lower, upper);
                    
                    // If scale was not applied as expected, log a warning
                    if (Math.abs(lower) > 0.1 || Math.abs(upper - 100.0) > 0.1) {
                        logger.warn("CPU chart scale was not applied correctly!");
                    }
                }
                
                logger.info("Chart saved to: {}", filename);
            } else {
                logger.warn("No data available for {} in project {}", metricName, projectName);
            }
            
        } catch (Exception e) {
            logger.error("Error creating chart for {} in project {}: {}", 
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
     * Get a color based on series index
     */
    private Color getColorForSeriesIndex(int index) {
        Color[] colors = {
            new Color(0, 114, 189),   // Blue
            new Color(217, 83, 25),   // Red
            new Color(237, 177, 32),  // Yellow
            new Color(126, 47, 142),  // Purple
            new Color(119, 172, 48),  // Green
            new Color(77, 190, 238),  // Light blue
            new Color(162, 20, 47)    // Dark red
        };
        
        return colors[index % colors.length];
    }
    
    /**
     * Comparator for natural alphanumeric sorting of project names
     * Sorts first by non-numeric characters, then by numeric portions as numbers
     */
    private static class AlphaNumericComparator implements Comparator<String> {
        private static final Pattern PATTERN = Pattern.compile("(\\D*)(\\d*)");
        
        @Override
        public int compare(String s1, String s2) {
            Matcher m1 = PATTERN.matcher(s1);
            Matcher m2 = PATTERN.matcher(s2);
            
            // Continue matching and comparing until the strings are different
            while (m1.find() && m2.find()) {
                // Compare non-numeric portions case-insensitively
                String nonDigit1 = m1.group(1);
                String nonDigit2 = m2.group(1);
                int nonDigitCompare = nonDigit1.compareToIgnoreCase(nonDigit2);
                if (nonDigitCompare != 0) {
                    return nonDigitCompare;
                }
                
                // Compare numeric portions as numbers (not as strings)
                String digit1 = m1.group(2);
                String digit2 = m2.group(2);
                if (!digit1.isEmpty() && !digit2.isEmpty()) {
                    int num1 = Integer.parseInt(digit1);
                    int num2 = Integer.parseInt(digit2);
                    if (num1 != num2) {
                        return num1 - num2;
                    }
                } else if (!digit1.isEmpty()) {
                    return 1; // s1 has a number, s2 doesn't
                } else if (!digit2.isEmpty()) {
                    return -1; // s2 has a number, s1 doesn't
                }
                
                // If we've matched the entire strings, we're done
                if (m1.end() == s1.length() && m2.end() == s2.length()) {
                    return 0;
                }
                
                // Reset the matchers to start from where we left off
                m1 = PATTERN.matcher(s1).region(m1.end(), s1.length());
                m2 = PATTERN.matcher(s2).region(m2.end(), s2.length());
            }
            
            // If one string is done and the other isn't, the shorter comes first
            return s1.length() - s2.length();
        }
    }
    
    /**
     * Create HTML index with all projects tiled in a grid layout
     * Projects will be sorted by name with natural alphanumeric sorting
     */
    public void createHtmlIndex(Map<String, ProjectMetricsResult> projectResults) {
        String indexPath = outputDirectory + "/index.html";
        
        try (FileWriter writer = new FileWriter(indexPath)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <title>MongoDB Atlas Metrics</title>\n");
            writer.write("  <style>\n");
            writer.write("    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
            writer.write("    h1 { color: #333; font-size: 22px; margin: 15px 0; }\n");
            writer.write("    h2 { color: #666; font-size: 14px; margin: 5px 0; }\n");
            writer.write("    .all-charts { display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 15px; }\n");
            writer.write("    .chart-cell { background-color: #fff; padding: 10px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
            writer.write("    .chart-img { width: 100%; height: auto; display: block; }\n");
            writer.write("    .timestamp { color: #999; font-size: 12px; margin-bottom: 20px; }\n");
            writer.write("  </style>\n");
            writer.write("</head>\n");
            writer.write("<body>\n");
            writer.write("  <h1>MongoDB Atlas Metrics</h1>\n");
            
            // Generate timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.write("  <p class='timestamp'>Generated on: " + dateFormat.format(new Date()) + "</p>\n");
            
            // Create a single grid for all projects
            writer.write("  <div class='all-charts'>\n");
            
            // Collect all chart files from all projects
            boolean hasAnyCharts = false;
            
            // Sort projects by name using the AlphaNumericComparator
            List<ProjectMetricsResult> sortedProjects = projectResults.values().stream()
                .sorted(Comparator.comparing(ProjectMetricsResult::getProjectName, new AlphaNumericComparator()))
                .collect(Collectors.toList());
            
            // For each project, collect and display charts
            for (ProjectMetricsResult projectResult : sortedProjects) {
                String projectName = projectResult.getProjectName();
                String safeProjectName = projectName.replace(' ', '_');
                
                for (String metric : projectResult.getMetrics()) {
                    String combinedChartFile = safeProjectName + "_" + metric + "_combined.png";
                    File combinedChartFileCheck = new File(outputDirectory, combinedChartFile);
                    
                    if (combinedChartFileCheck.exists()) {
                        hasAnyCharts = true;
                        
                        writer.write("    <div class='chart-cell'>\n");
                        writer.write("      <h2>" + projectName + "</h2>\n");
                        writer.write("      <img src='" + combinedChartFile + "' alt='" + metric + " for " + projectName + "' class='chart-img'>\n");
                        writer.write("    </div>\n");
                    }
                }
            }
            
            if (!hasAnyCharts) {
                writer.write("  <p>No charts available for any projects.</p>\n");
            }
            
            writer.write("  </div>\n");
            
            writer.write("</body>\n");
            writer.write("</html>\n");
            
            logger.info("HTML index created at: {}", indexPath);
        } catch (IOException e) {
            logger.error("Error creating HTML index: {}", e.getMessage());
        }
    }
}