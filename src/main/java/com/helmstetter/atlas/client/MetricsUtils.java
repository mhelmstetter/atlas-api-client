package com.helmstetter.atlas.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for metrics processing operations
 */
public class MetricsUtils {
    
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
            default:
                return "";
        }
    }
    
    /**
     * Format a double value for display (round to 2 decimal places)
     */
    public static String formatValue(double value) {
        return String.format("%.2f", value);
    }
    
    /**
     * Convert memory values from MB to GB if needed
     */
    public static double convertToDisplayUnits(String metric, double value) {
        if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
            return value / 1024.0; // Convert MB to GB
        }
        return value;
    }
}