package com.helmstetter.atlas.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for project metrics results
 * Stores maximum and average values for each metric in a project
 */
public class ProjectMetricsResult {
    
    private final String projectName;
    private final String projectId;
    
    // Maps to store metric values
    private final Map<String, Double> maxValues = new HashMap<>();
    private final Map<String, Double> avgValues = new HashMap<>(); 
    private final Map<String, String> maxLocations = new HashMap<>();
    
    // Maps to track totals for average calculation
    private final Map<String, Double> totalValues = new HashMap<>();
    private final Map<String, Integer> measurementCounts = new HashMap<>();
    
    public ProjectMetricsResult(String projectName, String projectId) {
        this.projectName = projectName;
        this.projectId = projectId;
    }
    
    /**
     * Initialize tracking for a new metric
     */
    public void initializeMetric(String metric) {
        maxValues.put(metric, Double.MIN_VALUE);
        maxLocations.put(metric, "");
        avgValues.put(metric, 0.0);
        totalValues.put(metric, 0.0);
        measurementCounts.put(metric, 0);
    }
    
    /**
     * Add a measurement value for a metric
     */
    public void addMeasurement(String metric, double value, String location) {
        // Make sure the metric is initialized
        if (!maxValues.containsKey(metric)) {
            initializeMetric(metric);
        }
        
        // Update max if this value is higher
        if (value > maxValues.get(metric)) {
            maxValues.put(metric, value);
            maxLocations.put(metric, location);
        }
        
        // Add to totals for average calculation
        totalValues.put(metric, totalValues.get(metric) + value);
        measurementCounts.put(metric, measurementCounts.get(metric) + 1);
    }
    
    /**
     * Calculate final averages for all metrics
     */
    public void calculateAverages() {
        for (String metric : maxValues.keySet()) {
            int count = measurementCounts.get(metric);
            if (count > 0) {
                double total = totalValues.get(metric);
                avgValues.put(metric, total / count);
            }
        }
    }
    
    // Getters
    
    public String getProjectName() {
        return projectName;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public Double getMaxValue(String metric) {
        return maxValues.get(metric);
    }
    
    public Double getAvgValue(String metric) {
        return avgValues.get(metric);
    }
    
    public String getMaxLocation(String metric) {
        return maxLocations.get(metric);
    }
    
    public boolean hasMetricData(String metric) {
        return maxValues.containsKey(metric) && maxValues.get(metric) > Double.MIN_VALUE;
    }
    
    /**
     * Add a batch of data point values for a metric
     * 
     * @param metric The metric name
     * @param values List of values 
     * @param location Location identifier for the data points
     * @return The maximum value found
     */
    public double addDataPoints(String metric, List<Double> values, String location) {
        double max = Double.MIN_VALUE;
        double total = 0.0;
        
        if (values.isEmpty()) {
            return max;
        }
        
        for (Double value : values) {
            if (value > max) {
                max = value;
            }
            total += value;
        }
        
        // Add this batch of measurements
        addMeasurement(metric, max, location);
        
        // Return the max value from this batch
        return max;
    }
}