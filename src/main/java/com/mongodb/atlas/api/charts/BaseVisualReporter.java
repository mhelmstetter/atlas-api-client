package com.mongodb.atlas.api.charts;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.metrics.ProjectMetricsResult;
import com.mongodb.atlas.api.util.MetricsUtils;

/**
 * Base class for visual reporters that generate SVG charts and HTML indexes
 * Contains common functionality shared between API-based and storage-based reporters
 * Enhanced with data optimization and better date axis handling
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
    
    // Data optimization settings
    private static final int MAX_POINTS_PER_SERIES = 2000; // Maximum points per time series
    private static final int MIN_INTERVAL_SECONDS = 30; // Minimum interval between points in seconds
    
    public BaseVisualReporter(String outputDirectory, int chartWidth, int chartHeight, boolean darkMode) {
        this.outputDirectory = outputDirectory;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.darkMode = darkMode;
        
        // Initialize theme, processors and generators
        this.chartTheme = new ChartTheme(this, darkMode);
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
        
        // Calculate total data points before optimization
        int totalPointsBefore = dataset.getSeries().stream()
                .mapToInt(s -> ((TimeSeries)s).getItemCount())
                .sum();
        
        // Optimize the dataset to reduce visual clutter
        TimeSeriesCollection optimizedDataset = optimizeDataset(dataset);
        
        int totalPointsAfter = optimizedDataset.getSeries().stream()
                .mapToInt(s -> ((TimeSeries)s).getItemCount())
                .sum();
        
        logger.info("Creating chart for {}: {} series, {} data points (optimized from {})", 
            metricName, 
            optimizedDataset.getSeriesCount(),
            totalPointsAfter,
            totalPointsBefore);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                metricName,          // chart title
                null,                // x-axis label
                null,                // y-axis label
                optimizedDataset,    // data
                false,               // no legend
                false,               // no tooltips
                false                // no URLs
        );
        
        // Apply chart theme
        chartTheme.applyTo(chart, metricName, isCpuMetric, isMemoryMetric, isBytesMetric);
        
        return chart;
    }
    
    /**
     * Optimize dataset by reducing the number of data points while preserving visual fidelity
     */
    protected TimeSeriesCollection optimizeDataset(TimeSeriesCollection originalDataset) {
        TimeSeriesCollection optimizedDataset = new TimeSeriesCollection();
        
        for (int i = 0; i < originalDataset.getSeriesCount(); i++) {
            TimeSeries originalSeries = originalDataset.getSeries(i);
            TimeSeries optimizedSeries = optimizeTimeSeries(originalSeries);
            
            if (optimizedSeries.getItemCount() > 0) {
                optimizedDataset.addSeries(optimizedSeries);
            }
        }
        
        return optimizedDataset;
    }
    
    /**
     * Optimize a single time series by intelligently reducing data points
     */
    protected TimeSeries optimizeTimeSeries(TimeSeries originalSeries) {
        if (originalSeries.getItemCount() <= MAX_POINTS_PER_SERIES) {
            return originalSeries; // No optimization needed
        }
        
        TimeSeries optimizedSeries = new TimeSeries(originalSeries.getKey());
        
        // Calculate the sampling interval to achieve target point count
        int originalCount = originalSeries.getItemCount();
        int targetInterval = Math.max(1, originalCount / MAX_POINTS_PER_SERIES);
        
        // Always include the first point
        if (originalCount > 0) {
            optimizedSeries.add(originalSeries.getDataItem(0));
        }
        
        // Sample points at calculated intervals, but also preserve peaks and valleys
        for (int i = targetInterval; i < originalCount - 1; i += targetInterval) {
            // Check if this point is a local peak or valley
            boolean isSignificant = isSignificantPoint(originalSeries, i);
            
            if (isSignificant || i % targetInterval == 0) {
                optimizedSeries.add(originalSeries.getDataItem(i));
            }
        }
        
        // Always include the last point
        if (originalCount > 1) {
            optimizedSeries.add(originalSeries.getDataItem(originalCount - 1));
        }
        
        logger.debug("Optimized series '{}' from {} to {} points ({}% reduction)", 
                originalSeries.getKey(), 
                originalCount, 
                optimizedSeries.getItemCount(),
                Math.round((1.0 - (double)optimizedSeries.getItemCount() / originalCount) * 100));
        
        return optimizedSeries;
    }
    
    /**
     * Determine if a point is significant (local peak, valley, or large change)
     */
    protected boolean isSignificantPoint(TimeSeries series, int index) {
        if (index <= 0 || index >= series.getItemCount() - 1) {
            return true; // End points are always significant
        }
        
        double prevValue = series.getValue(index - 1).doubleValue();
        double currentValue = series.getValue(index).doubleValue();
        double nextValue = series.getValue(index + 1).doubleValue();
        
        // Check if it's a local peak or valley
        boolean isPeak = currentValue > prevValue && currentValue > nextValue;
        boolean isValley = currentValue < prevValue && currentValue < nextValue;
        
        // Check if there's a significant change (>10% from previous)
        boolean isSignificantChange = Math.abs(currentValue - prevValue) / Math.max(Math.abs(prevValue), 1.0) > 0.1;
        
        return isPeak || isValley || isSignificantChange;
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
                logger.debug("Chart series data: {} points from {} to {} ({})",
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
                    
                    logger.debug("Series '{}' time range: {} to {} ({} points)", 
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