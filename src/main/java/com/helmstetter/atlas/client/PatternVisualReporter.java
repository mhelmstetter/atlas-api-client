package com.helmstetter.atlas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates SVG charts and HTML index showing MongoDB Atlas metrics
 * Uses JFreeSVG for vector graphics output with dark mode support
 * Optimized for maximum space utilization with minimum margins
 */
public class PatternVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(PatternVisualReporter.class);
    
    private final AtlasApiClient apiClient;
    private final String outputDirectory;
    
    // Chart sizing with default values
    private final int chartWidth;  
    private final int chartHeight;
    
    // Dark mode flag
    private final boolean darkMode;
    
    // Theme colors
    private final Color backgroundColor;
    private final Color textColor;
    private final Color gridLineColor;
    
    /**
     * Constructor to initialize the reporter with default chart dimensions and light mode
     */
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory) {
        this(apiClient, outputDirectory, 600, 300, false);
    }
    
    /**
     * Constructor to initialize the reporter with custom chart dimensions
     */
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory, int chartWidth, int chartHeight) {
        this(apiClient, outputDirectory, chartWidth, chartHeight, false);
    }
    
    /**
     * Constructor with dark mode option
     */
    public PatternVisualReporter(AtlasApiClient apiClient, String outputDirectory, 
            int chartWidth, int chartHeight, boolean darkMode) {
        this.apiClient = apiClient;
        this.outputDirectory = outputDirectory;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.darkMode = darkMode;
        
        // Set theme colors based on dark mode setting
        if (darkMode) {
            this.backgroundColor = new Color(30, 30, 30);
            this.textColor = new Color(230, 230, 230);
            this.gridLineColor = new Color(60, 60, 60);
        } else {
            this.backgroundColor = Color.white;
            this.textColor = Color.black;
            this.gridLineColor = Color.lightGray;
        }
        
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
                        null,                // x-axis label (none for compactness)
                        null,                // y-axis label (none for compactness)
                        dataset,             // data
                        false,               // no legend
                        false,               // no tooltips
                        false                // no URLs
                );
                
                // Apply optimized styling
                applyOptimizedChartTheme(chart, isCpuMetric);
                
                // Save chart to SVG file
                String filename = String.format("%s/%s_%s_combined.svg", 
                        outputDirectory, 
                        projectResult.getProjectName().replace(' ', '_'), 
                        metricName);
                
                // Create SVG using JFreeSVG
                saveOptimizedSvg(chart, filename);
                
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
     * Apply optimized theme settings to maximize chart area usage
     */
    private void applyOptimizedChartTheme(JFreeChart chart, boolean isCpuMetric) {
        // Completely eliminate chart padding
        chart.setPadding(new RectangleInsets(0, 0, 0, 0));
        
        // Make title more compact
        TextTitle title = chart.getTitle();
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setPaint(textColor);
        title.setMargin(new RectangleInsets(1, 0, 1, 0));
        
        // Set chart background
        chart.setBackgroundPaint(backgroundColor);
        
        // Get the plot for customization
        XYPlot plot = chart.getXYPlot();
        
        // Minimize plot insets - this is critical for space utilization
        plot.setInsets(new RectangleInsets(1, 1, 1, 1));
        
        // Set background color
        plot.setBackgroundPaint(backgroundColor);
        plot.setDomainGridlinePaint(gridLineColor);
        plot.setRangeGridlinePaint(gridLineColor);
        
        // Show gridlines
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Configure series appearance
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        for (int i = 0; i < plot.getDataset().getSeriesCount(); i++) {
            Color lineColor = getColorForSeriesIndex(i, darkMode);
            renderer.setSeriesPaint(i, lineColor);
            renderer.setSeriesStroke(i, new BasicStroke(1.0f)); // Thinner lines
            renderer.setSeriesShapesVisible(i, false);
        }
        plot.setRenderer(renderer);
        
        // Format date axis with minimal margins
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
        dateAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
        dateAxis.setTickLabelPaint(textColor);
        dateAxis.setLabelPaint(textColor);
        dateAxis.setLowerMargin(0.01); // Very small left margin
        dateAxis.setUpperMargin(0.01); // Very small right margin
        
        // Configure value axis
        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
        valueAxis.setTickLabelPaint(textColor);
        valueAxis.setLabelPaint(textColor);
        valueAxis.setLowerMargin(0.01); // Very small bottom margin
        valueAxis.setUpperMargin(0.01); // Very small top margin
        
        // CRITICAL: Set fixed scale for CPU metrics
        if (isCpuMetric) {
            valueAxis.setAutoRange(false);
            valueAxis.setRange(0.0, 100.0);
            logger.info("Set fixed 0-100 scale for CPU chart");
        } else {
            valueAxis.setAutoRange(true);
            valueAxis.setAutoRangeIncludesZero(false);
        }
        
        // Reduce space between plot area and axes
        plot.setAxisOffset(new RectangleInsets(1, 1, 1, 1));
    }
    
    /**
     * Save optimized SVG with proper viewBox and preserveAspectRatio for maximum space usage
     */
    private void saveOptimizedSvg(JFreeChart chart, String filename) throws IOException {
        // Create SVG graphics context with exact dimensions
        SVGGraphics2D g2 = new SVGGraphics2D(chartWidth, chartHeight);
        
        // Draw chart using full available area
        Rectangle drawArea = new Rectangle(0, 0, chartWidth, chartHeight);
        chart.draw(g2, drawArea);
        
        // Get the SVG element
        String svgElement = g2.getSVGElement();
        
        // Post-process the SVG to fix viewBox and other attributes
        String optimizedSvg = postProcessSvg(svgElement);
        
        // Write to file
        try (FileOutputStream fos = new FileOutputStream(filename);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write(optimizedSvg);
        }
    }
    
    /**
     * Post-process SVG to add viewBox and preserveAspectRatio attributes,
     * and optimize clip paths and transformations
     */
    private String postProcessSvg(String svgElement) {
        // First, ensure we have proper viewBox and preserveAspectRatio attributes
        if (!svgElement.contains("viewBox=")) {
            svgElement = svgElement.replace("<svg ", 
                    String.format("<svg viewBox=\"0 0 %d %d\" ", chartWidth, chartHeight));
        }
        
        if (!svgElement.contains("preserveAspectRatio=")) {
            svgElement = svgElement.replace("<svg ", "<svg preserveAspectRatio=\"none\" ");
        }
        
        // Optimize clip paths to allow more chart area
        // This regex finds clip path rectangles and increases their dimensions
        Pattern clipPattern = Pattern.compile("<clipPath id=\"([^\"]+)\"><rect ([^>]+)/></clipPath>");
        Matcher clipMatcher = clipPattern.matcher(svgElement);
        
        StringBuffer sb = new StringBuffer();
        while (clipMatcher.find()) {
            String clipId = clipMatcher.group(1);
            String rectAttrs = clipMatcher.group(2);
            
            // Extract width and height
            Pattern dimPattern = Pattern.compile("width=\"([^\"]+)\" height=\"([^\"]+)\"");
            Matcher dimMatcher = dimPattern.matcher(rectAttrs);
            
            if (dimMatcher.find()) {
                try {
                    double width = Double.parseDouble(dimMatcher.group(1));
                    double height = Double.parseDouble(dimMatcher.group(2));
                    
                    // Increase dimensions by 10%
                    double newWidth = width * 1.10;
                    double newHeight = height * 1.10;
                    
                    // Replace with new dimensions
                    String newRectAttrs = rectAttrs.replace(
                            "width=\"" + dimMatcher.group(1) + "\" height=\"" + dimMatcher.group(2) + "\"",
                            String.format("width=\"%.1f\" height=\"%.1f\"", newWidth, newHeight)
                    );
                    
                    // Rebuild the clip path with new dimensions
                    clipMatcher.appendReplacement(sb, "<clipPath id=\"" + clipId + "\"><rect " + newRectAttrs + "/></clipPath>");
                    
                    logger.debug("Expanded clip path {} from {}x{} to {:.1f}x{:.1f}", 
                            clipId, width, height, newWidth, newHeight);
                } catch (NumberFormatException e) {
                    // If we can't parse the numbers, keep original
                    clipMatcher.appendReplacement(sb, clipMatcher.group(0));
                }
            } else {
                // No width/height found, keep original
                clipMatcher.appendReplacement(sb, clipMatcher.group(0));
            }
        }
        clipMatcher.appendTail(sb);
        svgElement = sb.toString();
        
        // Optimize transform attributes to reduce margins
        // This is more complex and somewhat risky, so we'll be conservative
        Pattern transformPattern = Pattern.compile("<g class=\"(plot|axis)\"([^>]*)transform=\"translate\\(([^,]+),([^)]+)\\)([^\"]*)\">"); 
        Matcher transformMatcher = transformPattern.matcher(svgElement);
        
        sb = new StringBuffer();
        while (transformMatcher.find()) {
            String elementClass = transformMatcher.group(1);
            String extraAttrs = transformMatcher.group(2);
            String translateX = transformMatcher.group(3);
            String translateY = transformMatcher.group(4);
            String restOfTransform = transformMatcher.group(5);
            
            try {
                double x = Double.parseDouble(translateX);
                double y = Double.parseDouble(translateY);
                
                // Different optimization depending on element type
                if ("plot".equals(elementClass)) {
                    // For plot elements, reduce margins but keep some space for axes
                    // Only reduce if they seem excessively large
                    if (x > 40) {
                        x = Math.max(35, x * 0.85); // Reduce but keep some margin
                    }
                    if (y > 20) {
                        y = Math.max(15, y * 0.85); // Reduce but keep some margin
                    }
                }
                
                // Rebuild transform with adjusted values
                transformMatcher.appendReplacement(sb, 
                    String.format("<g class=\"%s\"%stransform=\"translate(%.1f,%.1f)%s\">", 
                            elementClass, extraAttrs, x, y, restOfTransform));
                
            } catch (NumberFormatException e) {
                // Keep original if we can't parse the numbers
                transformMatcher.appendReplacement(sb, transformMatcher.group(0));
            }
        }
        transformMatcher.appendTail(sb);
        
        return sb.toString();
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
     * Get a color based on series index, adjusted for dark mode if needed
     */
    private Color getColorForSeriesIndex(int index, boolean darkMode) {
        // Light mode colors
        Color[] lightColors = {
            new Color(0, 114, 189),   // Blue
            new Color(217, 83, 25),   // Red
            new Color(237, 177, 32),  // Yellow
            new Color(126, 47, 142),  // Purple
            new Color(119, 172, 48),  // Green
            new Color(77, 190, 238),  // Light blue
            new Color(162, 20, 47)    // Dark red
        };
        
        // Dark mode colors - brighter for better contrast against dark background
        Color[] darkColors = {
            new Color(0, 149, 255),     // Brighter blue
            new Color(255, 98, 37),     // Brighter red
            new Color(255, 200, 47),    // Brighter yellow
            new Color(177, 70, 194),    // Brighter purple
            new Color(132, 235, 52),    // Brighter green
            new Color(99, 217, 255),    // Brighter light blue
            new Color(255, 61, 61)      // Brighter dark red
        };
        
        Color[] colors = darkMode ? darkColors : lightColors;
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
     */
    public void createHtmlIndex(Map<String, ProjectMetricsResult> projectResults) {
        String indexPath = outputDirectory + "/index.html";
        
        try (FileWriter writer = new FileWriter(indexPath)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <title>MongoDB Atlas Metrics</title>\n");
            writer.write("  <style>\n");
            writer.write("    body { font-family: Arial, sans-serif; margin: 20px; }\n");
            
            // Apply styles based on dark mode setting directly
            if (darkMode) {
                writer.write("    body { background-color: #1e1e1e; color: #e6e6e6; }\n");
                writer.write("    h1 { color: #e6e6e6; font-size: 22px; margin: 15px 0; }\n");
                writer.write("    h2 { color: #aaaaaa; font-size: 14px; margin: 5px 0; }\n");
                writer.write("    .chart-cell { background-color: #2d2d2d; padding: 10px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); }\n");
                writer.write("    .timestamp { color: #888888; font-size: 12px; margin-bottom: 20px; }\n");
            } else {
                writer.write("    body { background-color: #f5f5f5; color: #333; }\n");
                writer.write("    h1 { color: #333; font-size: 22px; margin: 15px 0; }\n");
                writer.write("    h2 { color: #666; font-size: 14px; margin: 5px 0; }\n");
                writer.write("    .chart-cell { background-color: #fff; padding: 10px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
                writer.write("    .timestamp { color: #999; font-size: 12px; margin-bottom: 20px; }\n");
            }
            
            writer.write("    .all-charts { display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 15px; }\n");
            writer.write("    .chart-img { width: 100%; height: auto; min-height: " + chartHeight + "px; }\n");
            writer.write("    .chart-container { display: flex; justify-content: center; align-items: center; }\n");
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
                    String combinedChartFile = safeProjectName + "_" + metric + "_combined.svg";
                    File combinedChartFileCheck = new File(outputDirectory, combinedChartFile);
                    
                    if (combinedChartFileCheck.exists()) {
                        hasAnyCharts = true;
                        
                        writer.write("    <div class='chart-cell'>\n");
                        writer.write("      <h2>" + projectName + "</h2>\n");
                        writer.write("      <div class='chart-container'>\n");
                        writer.write("        <object type='image/svg+xml' data='" + combinedChartFile + 
                                "' alt='" + metric + " for " + projectName + "' class='chart-img' width='" + 
                                chartWidth + "' height='" + chartHeight + "'>Your browser does not support SVG</object>\n");
                        writer.write("      </div>\n");
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