package com.mongodb.atlas.api.util;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.metrics.ProcessingResult;

/**
 * Utility class for metrics processing operations
 * Enhanced with better formatting for byte values and consistent unit display
 */
public class MetricsUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(MetricsUtils.class);
    
    // Formatter for numeric values
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    
    /**
     * Extract values from a list of data points
     */
    public static List<Double> extractDataPointValues(List<Map<String, Object>> dataPoints) {
        List<Double> values = new ArrayList<>();
        
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
            }
        }
        
        return values;
    }
    
    /**
     * Process values to find min, max, avg, etc.
     */
    public static ProcessingResult processValues(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new ProcessingResult(0.0, 0.0, 0.0);
        }
        
        double sum = 0.0;
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        
        for (Double value : values) {
            sum += value;
            if (value > max) {
                max = value;
            }
            if (value < min) {
                min = value;
            }
        }
        
        double avg = sum / values.size();
        
        return new ProcessingResult(min, max, avg);
    }
    
    /**
     * Returns the appropriate unit for a given metric
     */
    public static String getMetricUnit(String metric) {
        switch (metric) {
            case "SYSTEM_NORMALIZED_CPU_USER":
                return "%";
            case "SYSTEM_MEMORY_USED":
            case "SYSTEM_MEMORY_FREE":
                return " GB";
            case "DISK_PARTITION_IOPS_TOTAL":
                return " IOPS";
            case "CACHE_BYTES_READ_INTO":
            case "CACHE_BYTES_WRITTEN_FROM":
                return " bytes";
            default:
                if (metric.contains("BYTES")) {
                    return " bytes";
                }
                return "";
        }
    }
    
    /**
     * Format a double value for display (round to 2 decimal places)
     */
    public static String formatValue(double value) {
        return DECIMAL_FORMAT.format(value);
    }
    
    /**
     * Convert memory values from MB to GB if needed, and handle other unit conversions
     */
    public static double convertToDisplayUnits(String metric, double value) {
        if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
            return value / 1024.0; // Convert MB to GB
        }
        return value;
    }
    
    /**
     * Format byte values with appropriate units (B, KB, MB, GB, TB)
     */
    public static String formatByteValue(double bytes) {
        if (bytes < 1024) {
            return formatValue(bytes) + " B";
        } else if (bytes < 1024 * 1024) {
            return formatValue(bytes / 1024) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return formatValue(bytes / (1024 * 1024)) + " MB";
        } else if (bytes < 1024L * 1024L * 1024L * 1024L) {
            return formatValue(bytes / (1024 * 1024 * 1024)) + " GB";
        } else {
            return formatValue(bytes / (1024L * 1024L * 1024L * 1024L)) + " TB";
        }
    }
    
    /**
     * Format a metric value with the appropriate unit and formatting
     */
    public static String formatMetricValue(String metric, double value) {
        // Special handling for byte metrics
        if (metric.contains("BYTES")) {
            return formatByteValue(value);
        }
        
        // Convert to appropriate display units
        double displayValue = convertToDisplayUnits(metric, value);
        
        // Format the value and add unit
        return formatValue(displayValue) + getMetricUnit(metric);
    }
    
    /**
     * Parse period to days - consistent across all reporters
     */
    public static int parsePeriodToDays(String periodStr) {
        try {
            if (periodStr == null || periodStr.isEmpty()) {
                logger.warn("No period specified, defaulting to 7 days");
                return 7;
            }
            
            // Use java.time.Duration for parsing ISO 8601 durations
            java.time.Duration duration = java.time.Duration.parse(periodStr);
            double days = duration.toHours() / 24.0;
            
            // Special case for Period format (P1D, P7D, etc.)
            if (periodStr.startsWith("P") && !periodStr.contains("T")) {
                try {
                    java.time.Period period = java.time.Period.parse(periodStr);
                    days = period.getDays();
                    
                    // Handle years and months approximately
                    days += period.getYears() * 365;
                    days += period.getMonths() * 30;
                } catch (Exception e) {
                    // If we can't parse as Period, fall back to Duration result
                    logger.debug("Couldn't parse as Period, using Duration result: {}", days);
                }
            }
            
            // Round to nearest day, minimum 1
            int roundedDays = Math.max(1, (int)Math.round(days));
            logger.debug("Parsed period {} to {} days", periodStr, roundedDays);
            return roundedDays;
            
        } catch (Exception e) {
            logger.warn("Error parsing period {}, defaulting to 7 days: {}", periodStr, e.getMessage());
            return 7;
        }
    }
    
    /**
     * Calculate start time from period string
     */
    public static Instant calculateStartTime(Instant endTime, String period) {
        if (period == null || period.isEmpty()) {
            // This shouldn't happen, but return a reasonable default
            return endTime.minus(7, ChronoUnit.DAYS);
        }
        
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
}