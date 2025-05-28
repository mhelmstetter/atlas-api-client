package com.mongodb.atlas.api.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mongodb.atlas.api.metrics.PatternAnalyzer.PatternResult;
import com.mongodb.atlas.api.metrics.PatternAnalyzer.PatternType;

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
    
    // Maps to store pattern analysis results
    private final Map<String, Map<String, PatternResult>> patternResults = new HashMap<>();
    
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
        patternResults.put(metric, new HashMap<>());
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
     * Add a pattern analysis result for a specific metric and location
     * 
     * @param metric The metric name
     * @param location The location identifier (e.g. hostname:port)
     * @param result The pattern analysis result
     */
    public void addPatternResult(String metric, String location, PatternResult result) {
        // Make sure the metric is initialized
        if (!patternResults.containsKey(metric)) {
            patternResults.put(metric, new HashMap<>());
        }
        
        // Add the pattern result
        patternResults.get(metric).put(location, result);
    }
    
    /**
     * Get the pattern result for a specific metric and location
     */
    public PatternResult getPatternResult(String metric, String location) {
        if (patternResults.containsKey(metric)) {
            return patternResults.get(metric).get(location);
        }
        return null;
    }
    
    /**
     * Get all pattern results for a specific metric
     */
    public Map<String, PatternResult> getPatternResults(String metric) {
        return patternResults.getOrDefault(metric, new HashMap<>());
    }
    
    /**
     * Check if pattern data exists for a specific metric
     */
    public boolean hasPatternData(String metric) {
        return patternResults.containsKey(metric) && !patternResults.get(metric).isEmpty();
    }
    
    /**
     * Count pattern types across all locations for a specific metric
     * 
     * @param metric The metric name
     * @return Map of pattern types to their counts
     */
    public Map<PatternType, Integer> countPatternTypes(String metric) {
        Map<PatternType, Integer> counts = new HashMap<>();
        
        // Initialize counts for all pattern types
        for (PatternType type : PatternType.values()) {
            counts.put(type, 0);
        }
        
        // Count each pattern type
        if (patternResults.containsKey(metric)) {
            for (PatternResult result : patternResults.get(metric).values()) {
                PatternType type = result.getPatternType();
                counts.put(type, counts.get(type) + 1);
            }
        }
        
        return counts;
    }
    
    /**
     * Get the dominant pattern for a metric (the most common pattern type)
     * 
     * @param metric The metric name
     * @return The most common pattern type, or UNKNOWN if no pattern data
     */
    public PatternType getDominantPattern(String metric) {
        Map<PatternType, Integer> counts = countPatternTypes(metric);
        
        PatternType dominant = PatternType.UNKNOWN;
        int maxCount = 0;
        
        for (Map.Entry<PatternType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        
        return dominant;
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
     * Get all metrics tracked in this result
     */
    public Set<String> getMetrics() {
        return maxValues.keySet();
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