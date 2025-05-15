package com.helmstetter.atlas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private final int chartWidth;  
    private final int chartHeight;
    private final boolean darkMode;
    
    // Theme settings
    private final ChartTheme chartTheme;
    
    // Components
    private final SvgProcessor svgProcessor;
    private final HtmlIndexGenerator htmlGenerator;

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
        
        // Initialize theme, processors and generators
        this.chartTheme = new ChartTheme(darkMode);
        this.svgProcessor = new SvgProcessor(chartWidth, chartHeight);
        this.htmlGenerator = new HtmlIndexGenerator(outputDirectory, chartWidth, chartHeight, darkMode);
        
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
            // Create dataset for the chart
            TimeSeriesCollection dataset = createDatasetForMetric(projectId, metricName, period, granularity);
            
            // Only create chart if we have data
            if (dataset.getSeriesCount() > 0) {
                // Determine if this is a CPU metric
                boolean isCpuMetric = metricName.equals("SYSTEM_NORMALIZED_CPU_USER");
                
                // Create chart
                JFreeChart chart = createChart(dataset, metricName, isCpuMetric);
                
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
     * Create a dataset for a specific metric
     */
    private TimeSeriesCollection createDatasetForMetric(
            String projectId, 
            String metricName, 
            String period, 
            String granularity) {
        
        // Create a dataset to hold all series
        TimeSeriesCollection dataset = new TimeSeriesCollection();
            
        try {
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
     * Process system-level metrics
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
            // Get measurements for this process
            List<Map<String, Object>> measurements = 
                    apiClient.getProcessMeasurements(projectId, hostname, port,
                            List.of(metricName), granularity, period);
            
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
     * Process disk-level metrics
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
            List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
            
            for (Map<String, Object> disk : disks) {
                String partitionName = (String) disk.get("partitionName");
                
                // Get measurements for this disk
                List<Map<String, Object>> measurements = 
                        apiClient.getDiskMeasurements(projectId, hostname, port, partitionName,
                                List.of(metricName), granularity, period);
                
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
    
    /**
     * Create a chart with the specified dataset
     */
    private JFreeChart createChart(TimeSeriesCollection dataset, String metricName, boolean isCpuMetric) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                metricName,          // chart title
                null,                // x-axis label (none for compactness)
                null,                // y-axis label (none for compactness)
                dataset,             // data
                false,               // no legend
                false,               // no tooltips
                false                // no URLs
        );
        
        // Apply chart theme
        chartTheme.applyTo(chart, isCpuMetric);
        
        return chart;
    }
    
    /**
     * Save a chart to an SVG file
     */
    private void saveSvgChart(JFreeChart chart, String filename) throws IOException {
        // Create SVG graphics context
        SVGGraphics2D g2 = new SVGGraphics2D(chartWidth, chartHeight);
        
        // Draw chart using full available area
        Rectangle drawArea = new Rectangle(0, 0, chartWidth, chartHeight);
        chart.draw(g2, drawArea);
        
        // Get the SVG element
        String svgElement = g2.getSVGElement();
        
        // Process the SVG to optimize it
        String optimizedSvg = svgProcessor.optimize(svgElement);
        
        // Write to file
        try (FileOutputStream fos = new FileOutputStream(filename);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write(optimizedSvg);
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
            Double value = extractValue(dataPoint);
            
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
     * Extract numeric value from a data point
     */
    private Double extractValue(Map<String, Object> dataPoint) {
        Object valueObj = dataPoint.get("value");
        
        // Handle different numeric types
        if (valueObj instanceof Integer) {
            return ((Integer) valueObj).doubleValue();
        } else if (valueObj instanceof Double) {
            return (Double) valueObj;
        } else if (valueObj instanceof Long) {
            return ((Long) valueObj).doubleValue();
        }
        
        return null;
    }
    
    /**
     * Create HTML index with all projects tiled in a grid layout
     */
    public void createHtmlIndex(Map<String, ProjectMetricsResult> projectResults) {
        htmlGenerator.generate(projectResults);
    }
    
    /**
     * Chart theme manager for consistent styling
     */
    private class ChartTheme {
        private final Color backgroundColor;
        private final Color textColor;
        private final Color gridLineColor;
        private final Color[] seriesColors;
        
        public ChartTheme(boolean darkMode) {
            if (darkMode) {
                this.backgroundColor = new Color(30, 30, 30);
                this.textColor = new Color(230, 230, 230);
                this.gridLineColor = new Color(60, 60, 60);
                this.seriesColors = new Color[] {
                    new Color(0, 149, 255),     // Brighter blue
                    new Color(255, 98, 37),     // Brighter red
                    new Color(255, 200, 47),    // Brighter yellow
                    new Color(177, 70, 194),    // Brighter purple
                    new Color(132, 235, 52),    // Brighter green
                    new Color(99, 217, 255),    // Brighter light blue
                    new Color(255, 61, 61)      // Brighter dark red
                };
            } else {
                this.backgroundColor = Color.white;
                this.textColor = Color.black;
                this.gridLineColor = Color.lightGray;
                this.seriesColors = new Color[] {
                    new Color(0, 114, 189),   // Blue
                    new Color(217, 83, 25),   // Red
                    new Color(237, 177, 32),  // Yellow
                    new Color(126, 47, 142),  // Purple
                    new Color(119, 172, 48),  // Green
                    new Color(77, 190, 238),  // Light blue
                    new Color(162, 20, 47)    // Dark red
                };
            }
        }
        
        /**
         * Apply theme to a chart
         */
        public void applyTo(JFreeChart chart, boolean isCpuMetric) {
            // Completely eliminate chart padding
            chart.setPadding(new RectangleInsets(0, 0, 0, 0));
            
            // Style the title
            TextTitle title = chart.getTitle();
            title.setFont(new Font("SansSerif", Font.BOLD, 11));
            title.setPaint(textColor);
            title.setMargin(new RectangleInsets(1, 0, 1, 0));
            
            // Set chart background
            chart.setBackgroundPaint(backgroundColor);
            
            // Get the plot for customization
            XYPlot plot = chart.getXYPlot();
            
            // Configure plot
            configureXYPlot(plot, isCpuMetric);
        }
        
        /**
         * Configure XYPlot settings
         */
        private void configureXYPlot(XYPlot plot, boolean isCpuMetric) {
            // Minimize plot insets
            plot.setInsets(new RectangleInsets(1, 1, 1, 1));
            
            // Set colors
            plot.setBackgroundPaint(backgroundColor);
            plot.setDomainGridlinePaint(gridLineColor);
            plot.setRangeGridlinePaint(gridLineColor);
            
            // Show gridlines
            plot.setDomainGridlinesVisible(true);
            plot.setRangeGridlinesVisible(true);
            
            // Configure series appearance
            configureSeriesRenderer(plot);
            
            // Format date axis
            configureDateAxis((DateAxis) plot.getDomainAxis());
            
            // Format value axis
            configureValueAxis((NumberAxis) plot.getRangeAxis(), isCpuMetric);
            
            // Reduce space between plot area and axes
            plot.setAxisOffset(new RectangleInsets(1, 1, 1, 1));
        }
        
        /**
         * Configure the series renderer
         */
        private void configureSeriesRenderer(XYPlot plot) {
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            for (int i = 0; i < plot.getDataset().getSeriesCount(); i++) {
                Color lineColor = seriesColors[i % seriesColors.length];
                renderer.setSeriesPaint(i, lineColor);
                renderer.setSeriesStroke(i, new BasicStroke(1.0f)); // Thinner lines
                renderer.setSeriesShapesVisible(i, false);
            }
            plot.setRenderer(renderer);
        }
        
        /**
         * Configure the date (domain) axis
         */
        private void configureDateAxis(DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
            dateAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
            dateAxis.setTickLabelPaint(textColor);
            dateAxis.setLabelPaint(textColor);
            dateAxis.setLowerMargin(0.01); // Very small left margin
            dateAxis.setUpperMargin(0.01); // Very small right margin
        }
        
        /**
         * Configure the value (range) axis
         */
        private void configureValueAxis(NumberAxis valueAxis, boolean isCpuMetric) {
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
        }
    }
}