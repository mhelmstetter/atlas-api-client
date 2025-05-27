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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
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
 * Generates SVG charts and HTML index from stored MongoDB metrics data
 * Similar to PatternVisualReporter but reads from storage instead of API
 */
public class StorageVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageVisualReporter.class);
    
    private final MetricsStorage metricsStorage;
    private final String outputDirectory;
    private final int chartWidth;
    private final int chartHeight;
    private final boolean darkMode;
    
    // Theme settings
    private final ChartTheme chartTheme;
    
    // Components
    private final SvgProcessor svgProcessor;
    private final HtmlIndexGenerator htmlGenerator;
    
    // Global metrics scaling maps
    private final Map<String, Double> metricMaxValues = new HashMap<>();
    private final Map<String, Double> metricMinValues = new HashMap<>();
    private final boolean useConsistentScaling = true;
    
    public StorageVisualReporter(MetricsStorage metricsStorage, String outputDirectory) {
        this(metricsStorage, outputDirectory, 600, 300, false);
    }
    
    public StorageVisualReporter(MetricsStorage metricsStorage, String outputDirectory, 
            int chartWidth, int chartHeight, boolean darkMode) {
        this.metricsStorage = metricsStorage;
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
     * Generate combined chart for a specific metric from stored data
     */
    public void generateCombinedMetricChart(
            ProjectMetricsResult projectResult,
            String metricName,
            String period) {
        
        String projectName = projectResult.getProjectName();
        
        logger.info("Generating chart from storage for metric {} in project {}", metricName, projectName);
        
        try {
            // Calculate time range
            Instant endTime = Instant.now();
            Instant startTime = calculateStartTime(endTime, period);
            
            // Create dataset from stored data
            TimeSeriesCollection dataset = createDatasetFromStorage(
                    projectName, metricName, startTime, endTime);
            
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
     * Create dataset from stored data
     */
    private TimeSeriesCollection createDatasetFromStorage(
            String projectName,
            String metricName,
            Instant startTime,
            Instant endTime) {
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        
        try {
            // Get all stored data for this project and metric
            List<Document> documents = metricsStorage.getMetrics(
                    projectName, null, metricName, startTime, endTime);
            
            logger.info("Found {} stored data points for project {} metric {}", 
                    documents.size(), projectName, metricName);
            
            if (documents.isEmpty()) {
                return dataset;
            }
            
            // Group by host and partition (if applicable)
            Map<String, List<Document>> seriesGroups = groupDocumentsForSeries(documents);
            
            // Create time series for each group
            for (Map.Entry<String, List<Document>> entry : seriesGroups.entrySet()) {
                String seriesName = entry.getKey();
                List<Document> seriesDocuments = entry.getValue();
                
                TimeSeries timeSeries = createTimeSeriesFromDocuments(seriesName, seriesDocuments);
                
                if (timeSeries.getItemCount() > 0) {
                    dataset.addSeries(timeSeries);
                    
                    logger.info("Added series '{}' with {} data points", 
                            seriesName, timeSeries.getItemCount());
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
    
    /**
     * Calculate start time from period string
     */
    private Instant calculateStartTime(Instant endTime, String period) {
        try {
            // Use java.time.Duration for parsing ISO 8601 durations
            java.time.Duration duration = java.time.Duration.parse(period);
            return endTime.minus(duration);
        } catch (Exception e) {
            // Try Period format for day-based periods
            try {
                java.time.Period periodObj = java.time.Period.parse(period);
                return endTime.minus(periodObj.getDays(), ChronoUnit.DAYS);
            } catch (Exception e2) {
                logger.warn("Could not parse period {}, defaulting to 7 days", period);
                return endTime.minus(7, ChronoUnit.DAYS);
            }
        }
    }
    
    /**
     * Calculate global scales for consistent chart scaling
     */
    public void calculateGlobalScales(Map<String, ProjectMetricsResult> projectResults) {
        // Reset maps
        metricMaxValues.clear();
        metricMinValues.clear();
        
        // Iterate through all project results
        for (ProjectMetricsResult result : projectResults.values()) {
            for (String metric : result.getMetrics()) {
                if (result.hasMetricData(metric)) {
                    // Get values for this project
                    Double maxValue = result.getMaxValue(metric);
                    
                    // Update global max if higher
                    if (maxValue != null && maxValue > Double.MIN_VALUE) {
                        double currentMax = metricMaxValues.getOrDefault(metric, Double.MIN_VALUE);
                        if (maxValue > currentMax) {
                            metricMaxValues.put(metric, maxValue);
                        }
                    }
                    
                    // For now, min value is set to 0 for most metrics
                    metricMinValues.putIfAbsent(metric, 0.0);
                }
            }
        }
        
        // Add padding to max values (20% extra headroom)
        for (String metric : metricMaxValues.keySet()) {
            double currentMax = metricMaxValues.get(metric);
            metricMaxValues.put(metric, currentMax * 1.2);
        }
        
        // Log the calculated global scales
        logger.info("Calculated global metric scales:");
        for (String metric : metricMaxValues.keySet()) {
            logger.info("  {} scale: {} to {}", metric, 
                    MetricsUtils.formatValue(metricMinValues.get(metric)), 
                    MetricsUtils.formatValue(metricMaxValues.get(metric)));
        }
    }
    
    /**
     * Create HTML index for all projects
     */
    public void createHtmlIndex(Map<String, ProjectMetricsResult> projectResults) {
        // Calculate global scales before generating any charts
        if (useConsistentScaling) {
            calculateGlobalScales(projectResults);
        }
        
        htmlGenerator.generate(projectResults);
    }
    
    /**
     * Create a chart with the specified dataset
     */
    private JFreeChart createChart(TimeSeriesCollection dataset, String metricName, 
            boolean isCpuMetric, boolean isMemoryMetric, boolean isBytesMetric) {
        
        logger.info("Creating chart for {} from storage: {} series with {} total data points", 
            metricName, 
            dataset.getSeriesCount(),
            dataset.getSeries().stream()
                .mapToInt(s -> ((TimeSeries)s).getItemCount())
                .sum());
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                metricName,          // chart title
                null,                // x-axis label
                null,                // y-axis label
                dataset,             // data
                false,               // no legend
                false,               // no tooltips
                false                // no URLs
        );
        
        // Apply chart theme
        chartTheme.applyTo(chart, metricName, isCpuMetric, isMemoryMetric, isBytesMetric);
        
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
     * Chart theme manager (reused from PatternVisualReporter)
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
        
        public void applyTo(JFreeChart chart, String metricName, boolean isCpuMetric, 
                boolean isMemoryMetric, boolean isBytesMetric) {
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
            configureXYPlot(plot, metricName, isCpuMetric, isMemoryMetric, isBytesMetric);
        }
        
        private void configureXYPlot(XYPlot plot, String metricName, boolean isCpuMetric, 
                boolean isMemoryMetric, boolean isBytesMetric) {
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
            
            // Format value axis with metric-specific settings
            configureValueAxis((NumberAxis) plot.getRangeAxis(), metricName, 
                    isCpuMetric, isMemoryMetric, isBytesMetric);
            
            // Reduce space between plot area and axes
            plot.setAxisOffset(new RectangleInsets(1, 1, 1, 1));
        }
        
        private void configureSeriesRenderer(XYPlot plot) {
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            for (int i = 0; i < plot.getDataset().getSeriesCount(); i++) {
                Color lineColor = seriesColors[i % seriesColors.length];
                renderer.setSeriesPaint(i, lineColor);
                renderer.setSeriesStroke(i, new BasicStroke(1.0f));
                renderer.setSeriesShapesVisible(i, false);
            }
            plot.setRenderer(renderer);
        }
        
        private void configureDateAxis(DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
            dateAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
            dateAxis.setTickLabelPaint(textColor);
            dateAxis.setLabelPaint(textColor);
            dateAxis.setLowerMargin(0.01);
            dateAxis.setUpperMargin(0.01);
        }
        
        private void configureValueAxis(NumberAxis valueAxis, String metricName, 
                boolean isCpuMetric, boolean isMemoryMetric, boolean isBytesMetric) {
            // Basic styling
            valueAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));
            valueAxis.setTickLabelPaint(textColor);
            valueAxis.setLabelPaint(textColor);
            
            // Margins to prevent truncation
            valueAxis.setLowerMargin(0.05);
            valueAxis.setUpperMargin(0.10);
            
            // Custom number formatter
            NumberFormat formatter;
            
            if (isBytesMetric) {
                valueAxis.setNumberFormatOverride(new ByteUnitFormatter());
            } else if (isMemoryMetric) {
                formatter = new DecimalFormat("#,##0.##");
                valueAxis.setNumberFormatOverride(formatter);
                valueAxis.setLabel("GB");
            } else {
                formatter = new DecimalFormat("#,###.##");
                valueAxis.setNumberFormatOverride(formatter);
            }
            
            // Apply consistent scale if enabled
            if (useConsistentScaling && metricMaxValues.containsKey(metricName)) {
                valueAxis.setAutoRange(false);
                double min = metricMinValues.getOrDefault(metricName, 0.0);
                double max = metricMaxValues.get(metricName);
                
                if (isCpuMetric) {
                    valueAxis.setRange(0.0, 100.0);
                } else {
                    valueAxis.setRange(min, max);
                }
            } else if (isCpuMetric) {
                valueAxis.setAutoRange(false);
                valueAxis.setRange(0.0, 100.0);
            } else {
                valueAxis.setAutoRange(true);
                valueAxis.setAutoRangeIncludesZero(true);
            }
        }
    }
    
    /**
     * Custom formatter for byte values
     */
    private static class ByteUnitFormatter extends NumberFormat {
        private static final long serialVersionUID = 1L;
        private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};
        private final DecimalFormat df = new DecimalFormat("#,##0.##");

        @Override
        public StringBuffer format(double number, StringBuffer result, java.text.FieldPosition pos) {
            if (number == 0) {
                return result.append("0 B");
            }
            
            int unitIndex = (int) Math.floor(Math.log10(Math.abs(number)) / 3);
            unitIndex = Math.min(unitIndex, UNITS.length - 1);
            unitIndex = Math.max(unitIndex, 0);
            
            double value = number / Math.pow(1000, unitIndex);
            
            df.format(value, result, pos);
            result.append(" ").append(UNITS[unitIndex]);
            
            return result;
        }

        @Override
        public StringBuffer format(long number, StringBuffer result, java.text.FieldPosition pos) {
            return format((double) number, result, pos);
        }

        @Override
        public Number parse(String source, java.text.ParsePosition pos) {
            return null;
        }
    }
}