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
 * Base class for visual reporters that generate SVG charts and HTML indexes
 * Contains common functionality shared between API-based and storage-based reporters
 */
public abstract class BaseVisualReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseVisualReporter.class);
    
    protected final String outputDirectory;
    protected final int chartWidth;
    protected final int chartHeight;
    protected final boolean darkMode;
    
    // Theme settings
    protected final ChartTheme chartTheme;
    
    // Components
    protected final SvgProcessor svgProcessor;
    protected final HtmlIndexGenerator htmlGenerator;
    
    // Global metrics scaling maps
    protected final Map<String, Double> metricMaxValues = new HashMap<>();
    protected final Map<String, Double> metricMinValues = new HashMap<>();
    protected final boolean useConsistentScaling = true;
    
    public BaseVisualReporter(String outputDirectory, int chartWidth, int chartHeight, boolean darkMode) {
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
    protected JFreeChart createChart(TimeSeriesCollection dataset, String metricName, 
            boolean isCpuMetric, boolean isMemoryMetric, boolean isBytesMetric) {
        
        logger.info("Creating chart for {}: {} series with {} total data points", 
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
    protected void saveSvgChart(JFreeChart chart, String filename) throws IOException {
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
    protected void addTimeSeriesData(
            TimeSeriesCollection dataset, 
            Map<String, Object> measurement, 
            String seriesName) {
        
        @SuppressWarnings("unchecked")
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
            
            // Debug logging
            if (series.getItemCount() > 0) {
                Date firstDate = ((Millisecond)series.getDataItem(0).getPeriod()).getStart();
                Date lastDate = ((Millisecond)series.getDataItem(series.getItemCount()-1).getPeriod()).getStart();
                logger.info("Chart series data: {} points from {} to {} ({})",
                    series.getItemCount(), firstDate, lastDate, seriesName);
            }
        }
    }
    
    /**
     * Extract numeric value from a data point
     */
    protected Double extractValue(Map<String, Object> dataPoint) {
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
     * Debug the actual time range in the dataset
     */
    protected void debugTimeRange(TimeSeriesCollection dataset, String metricName, String period) {
        if (dataset.getSeriesCount() > 0) {
            Date overallMinDate = null;
            Date overallMaxDate = null;
            int totalDataPoints = 0;
            
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                TimeSeries series = dataset.getSeries(i);
                if (series.getItemCount() > 0) {
                    Date seriesMinDate = ((Millisecond)series.getDataItem(0).getPeriod()).getStart();
                    Date seriesMaxDate = ((Millisecond)series.getDataItem(series.getItemCount()-1).getPeriod()).getStart();
                    totalDataPoints += series.getItemCount();
                    
                    if (overallMinDate == null || seriesMinDate.before(overallMinDate)) {
                        overallMinDate = seriesMinDate;
                    }
                    if (overallMaxDate == null || seriesMaxDate.after(overallMaxDate)) {
                        overallMaxDate = seriesMaxDate;
                    }
                    
                    logger.info("Series '{}' time range: {} to {} ({} points)", 
                            series.getKey(), seriesMinDate, seriesMaxDate, series.getItemCount());
                }
            }
            
            if (overallMinDate != null && overallMaxDate != null) {
                long diffHours = (overallMaxDate.getTime() - overallMinDate.getTime()) / (1000 * 60 * 60);
                long diffDays = diffHours / 24;
                
                logger.info("Chart for {} overall time range: {} to {} ({} hours, {} days, {} total points, requested period: {})", 
                        metricName, overallMinDate, overallMaxDate, diffHours, diffDays, totalDataPoints, period);
                
                // Parse expected duration for comparison
                validateExpectedTimeRange(diffHours, period);
            }
        } else {
            logger.warn("No data series found for chart {}", metricName);
        }
    }
    
    /**
     * Validate that the actual time range matches the expected period
     */
    private void validateExpectedTimeRange(long actualHours, String period) {
        try {
            java.time.Duration expectedDuration = java.time.Duration.parse(period);
            long expectedHours = expectedDuration.toHours();
            
            if (actualHours < expectedHours * 0.8) {
                logger.warn("Chart shows {} hours but expected {} hours from period {}", 
                        actualHours, expectedHours, period);
            }
        } catch (Exception e) {
            // Try as Period
            try {
                java.time.Period periodObj = java.time.Period.parse(period);
                long expectedHours = (periodObj.getDays() + periodObj.getMonths() * 30L + periodObj.getYears() * 365L) * 24;
                
                if (actualHours < expectedHours * 0.8) {
                    logger.warn("Chart shows {} hours but expected {} hours from period {}", 
                            actualHours, expectedHours, period);
                }
            } catch (Exception e2) {
                logger.debug("Could not parse period {} for comparison", period);
            }
        }
    }
    
    /**
     * Determine if explicit time range should be used based on period
     */
    protected boolean shouldUseExplicitTimeRange(String period) {
        try {
            // Parse period and determine whether to use explicit timerange
            int days = MetricsUtils.parsePeriodToDays(period);
            
            // Use explicit timerange if period is longer than 48 hours
            return days * 24 > 48;
        } catch (Exception e) {
            logger.warn("Error parsing period {}, defaulting to period-based approach", period);
            return false;
        }
    }
    
    /**
     * Abstract method to be implemented by subclasses for generating charts
     */
    public abstract void generateCombinedMetricChart(
            ProjectMetricsResult projectResult, 
            String metricName, 
            String period, 
            String granularity);
    
    /**
     * Chart theme manager for consistent styling
     */
    protected class ChartTheme {
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
    protected static class ByteUnitFormatter extends NumberFormat {
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