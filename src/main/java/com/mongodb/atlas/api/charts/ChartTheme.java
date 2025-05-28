package com.mongodb.atlas.api.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chart theme manager for consistent styling
 * Enhanced with better date axis formatting
 */
public class ChartTheme {

	private static final Logger logger = LoggerFactory.getLogger(ChartTheme.class);
	
	private final BaseVisualReporter baseVisualReporter;
	private final Color backgroundColor;
    private final Color textColor;
    private final Color gridLineColor;
    private final Color[] seriesColors;
    
    public ChartTheme(BaseVisualReporter baseVisualReporter, boolean darkMode) {
        this.baseVisualReporter = baseVisualReporter;
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
        
        // Format date axis with intelligent date/time formatting
        configureDateAxis((DateAxis) plot.getDomainAxis(), plot);
        
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
    
    private void configureDateAxis(DateAxis dateAxis, XYPlot plot) {
        // Determine the time span of the data
        TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset();
        Date minDate = null;
        Date maxDate = null;
        
        // Find the overall time range
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            TimeSeries series = dataset.getSeries(i);
            if (series.getItemCount() > 0) {
                Date seriesMin = ((Millisecond)series.getDataItem(0).getPeriod()).getStart();
                Date seriesMax = ((Millisecond)series.getDataItem(series.getItemCount()-1).getPeriod()).getStart();
                
                if (minDate == null || seriesMin.before(minDate)) {
                    minDate = seriesMin;
                }
                if (maxDate == null || seriesMax.after(maxDate)) {
                    maxDate = seriesMax;
                }
            }
        }
        
        // Choose appropriate date format based on time span
        SimpleDateFormat dateFormat;
        if (minDate != null && maxDate != null) {
            long timeSpanHours = (maxDate.getTime() - minDate.getTime()) / (1000 * 60 * 60);
            
            if (timeSpanHours <= 24) {
                // Less than 24 hours: show time only
                dateFormat = new SimpleDateFormat("HH:mm");
                logger.debug("Using time-only format for {} hour span", timeSpanHours);
            } else if (timeSpanHours <= 7 * 24) {
                // 1-7 days: show date and time
                dateFormat = new SimpleDateFormat("MM/dd HH:mm");
                logger.debug("Using date+time format for {} hour span", timeSpanHours);
            } else {
                // More than 7 days: show date only
                dateFormat = new SimpleDateFormat("MM/dd");
                logger.debug("Using date-only format for {} hour span", timeSpanHours);
            }
        } else {
            // Fallback to time only
            dateFormat = new SimpleDateFormat("HH:mm");
            logger.debug("Using fallback time-only format");
        }
        
        dateAxis.setDateFormatOverride(dateFormat);
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
            valueAxis.setNumberFormatOverride(new BaseVisualReporter.ByteUnitFormatter());
        } else if (isMemoryMetric) {
            formatter = new DecimalFormat("#,##0.##");
            valueAxis.setNumberFormatOverride(formatter);
            valueAxis.setLabel("GB");
        } else {
            formatter = new DecimalFormat("#,###.##");
            valueAxis.setNumberFormatOverride(formatter);
        }
        
        // Apply consistent scale if enabled
        if (this.baseVisualReporter.useConsistentScaling && this.baseVisualReporter.metricMaxValues.containsKey(metricName)) {
            valueAxis.setAutoRange(false);
            double min = this.baseVisualReporter.metricMinValues.getOrDefault(metricName, 0.0);
            double max = this.baseVisualReporter.metricMaxValues.get(metricName);
            
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