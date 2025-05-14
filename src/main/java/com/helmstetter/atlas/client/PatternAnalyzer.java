package com.helmstetter.atlas.client;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes time series data from MongoDB Atlas metrics to identify patterns
 */
public class PatternAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PatternAnalyzer.class);
    
    // Constants for pattern detection
    public static final double SPIKE_THRESHOLD = 0.15; // 15% change for spike detection
    public static final double TREND_THRESHOLD = 0.05; // 5% overall change for trend detection
    public static final double VOLATILITY_THRESHOLD = 0.1; // Threshold for volatility detection
    public static final int MIN_SAWTOOTH_CYCLES = 3; // Minimum number of cycles to identify sawtooth pattern
    
    /**
     * Pattern types that can be identified
     */
    public enum PatternType {
        FLAT("Flat/Stable"), 
        SPIKY("Spiky/Volatile"),
        TRENDING_UP("Trending Upward"),
        TRENDING_DOWN("Trending Downward"),
        SAWTOOTH("Sawtooth/Cyclic"),
        UNKNOWN("Unknown/Mixed");
        
        private final String description;
        
        PatternType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Result class for pattern analysis
     */
    public static class PatternResult {
        private final PatternType patternType;
        private final double volatility; // Measure of data volatility
        private final double trendSlope; // Slope of the trend line (if applicable)
        private final int spikeCount; // Number of spikes detected
        private final int sawtoothCycles; // Number of sawtooth cycles detected
        private final String details; // Additional pattern details
        
        public PatternResult(PatternType patternType, double volatility, double trendSlope, 
                int spikeCount, int sawtoothCycles, String details) {
            this.patternType = patternType;
            this.volatility = volatility;
            this.trendSlope = trendSlope;
            this.spikeCount = spikeCount;
            this.sawtoothCycles = sawtoothCycles;
            this.details = details;
        }
        
        public PatternType getPatternType() {
            return patternType;
        }
        
        public double getVolatility() {
            return volatility;
        }
        
        public double getTrendSlope() {
            return trendSlope;
        }
        
        public int getSpikeCount() {
            return spikeCount;
        }
        
        public int getSawtoothCycles() {
            return sawtoothCycles;
        }
        
        public String getDetails() {
            return details;
        }
        
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(patternType.getDescription());
            
            // Add relevant details based on pattern type
            if (patternType == PatternType.SPIKY) {
                result.append(" (").append(spikeCount).append(" spikes, volatility: ")
                      .append(String.format("%.2f%%", volatility * 100)).append(")");
            } else if (patternType == PatternType.TRENDING_UP || patternType == PatternType.TRENDING_DOWN) {
                result.append(" (slope: ").append(String.format("%.4f", trendSlope)).append(")");
            } else if (patternType == PatternType.SAWTOOTH) {
                result.append(" (").append(sawtoothCycles).append(" cycles detected)");
            } else if (patternType == PatternType.FLAT) {
                result.append(" (volatility: ").append(String.format("%.2f%%", volatility * 100)).append(")");
            }
            
            // Add any additional details if available
            if (details != null && !details.isEmpty()) {
                result.append(" - ").append(details);
            }
            
            return result.toString();
        }
    }
    
    /**
     * Analyze time series data to identify patterns
     * 
     * @param dataPoints List of data point values
     * @return Pattern analysis result
     */
    public PatternResult analyzePattern(List<Double> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 3) {
            return new PatternResult(PatternType.UNKNOWN, 0, 0, 0, 0, 
                    "Insufficient data points for analysis");
        }
        
        // Calculate basic statistics
        double mean = calculateMean(dataPoints);
        double stdDev = calculateStdDev(dataPoints, mean);
        double volatility = stdDev / mean; // Coefficient of variation
        
        // Get min and max values
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Double point : dataPoints) {
            if (point < min) min = point;
            if (point > max) max = point;
        }
        
        // Calculate overall range as percentage of mean
        double overallRange = max - min;
        double relativeRange = (mean > 0) ? overallRange / mean : 0;
        
        // Count spikes (significant deviations from previous point)
        int spikeCount = countSpikes(dataPoints, SPIKE_THRESHOLD);
        
        // Calculate trend using linear regression
        double[] trendLine = calculateTrendLine(dataPoints);
        double slope = trendLine[0]; // Slope of the trend line
        double relativeSlope = slope * dataPoints.size() / mean; // Relative change over the whole period
        
        // Check for sawtooth pattern
        int sawtoothCycles = detectSawtoothCycles(dataPoints);
        
        // Determine the primary pattern
        PatternType patternType;
        String details = "";
        
        if (sawtoothCycles >= MIN_SAWTOOTH_CYCLES) {
            patternType = PatternType.SAWTOOTH;
            details = "Regular up-and-down cycle detected";
        } else if (Math.abs(relativeSlope) >= TREND_THRESHOLD) {
            // Significant trend detected
            patternType = (slope > 0) ? PatternType.TRENDING_UP : PatternType.TRENDING_DOWN;
            details = String.format("%.1f%% %s over the period", 
                    Math.abs(relativeSlope) * 100,
                    (slope > 0) ? "increase" : "decrease");
        } else if (volatility <= VOLATILITY_THRESHOLD) {
            // Low volatility indicates flat pattern
            patternType = PatternType.FLAT;
            details = "Stable metrics with low variation";
        } else if (spikeCount > dataPoints.size() / 10) {
            // Frequent spikes indicate spiky pattern
            patternType = PatternType.SPIKY;
            details = "Frequent short-term variations detected";
        } else {
            // Default case
            patternType = PatternType.UNKNOWN;
            details = "No clear pattern identified";
        }
        
        return new PatternResult(patternType, volatility, slope, spikeCount, sawtoothCycles, details);
    }
    
    /**
     * Calculate the mean of a list of data points
     */
    private double calculateMean(List<Double> dataPoints) {
        double sum = 0;
        for (Double point : dataPoints) {
            sum += point;
        }
        return sum / dataPoints.size();
    }
    
    /**
     * Calculate standard deviation
     */
    private double calculateStdDev(List<Double> dataPoints, double mean) {
        double sumSquaredDiff = 0;
        for (Double point : dataPoints) {
            double diff = point - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / dataPoints.size());
    }
    
    /**
     * Count significant spikes in the data
     * 
     * @param dataPoints List of data points
     * @param threshold Relative change threshold for spike detection
     * @return Number of spikes detected
     */
    private int countSpikes(List<Double> dataPoints, double threshold) {
        int spikeCount = 0;
        
        for (int i = 1; i < dataPoints.size(); i++) {
            double current = dataPoints.get(i);
            double previous = dataPoints.get(i - 1);
            
            // Skip if either value is zero or close to zero
            if (Math.abs(previous) < 0.0001 || Math.abs(current) < 0.0001) {
                continue;
            }
            
            // Calculate relative change
            double relativeChange = Math.abs((current - previous) / previous);
            
            // Count as spike if change exceeds threshold
            if (relativeChange > threshold) {
                spikeCount++;
            }
        }
        
        return spikeCount;
    }
    
    /**
     * Calculate trend line using linear regression
     * 
     * @param dataPoints List of data points
     * @return Array containing [slope, intercept] of the trend line
     */
    private double[] calculateTrendLine(List<Double> dataPoints) {
        int n = dataPoints.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = dataPoints.get(i);
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        
        // Calculate slope
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        
        // Calculate intercept
        double intercept = (sumY - slope * sumX) / n;
        
        return new double[] { slope, intercept };
    }
    
    /**
     * Detect sawtooth cycles in the data
     * 
     * @param dataPoints List of data points
     * @return Number of sawtooth cycles detected
     */
    private int detectSawtoothCycles(List<Double> dataPoints) {
        List<Integer> extrema = new ArrayList<>();
        
        // Find local extrema (peaks and valleys)
        for (int i = 1; i < dataPoints.size() - 1; i++) {
            double prev = dataPoints.get(i - 1);
            double curr = dataPoints.get(i);
            double next = dataPoints.get(i + 1);
            
            if ((curr > prev && curr > next) || (curr < prev && curr < next)) {
                extrema.add(i);
            }
        }
        
        // Count alternating peaks and valleys
        int cycleCount = 0;
        boolean expectingPeak = false; // Start with expecting a valley
        
        for (int i = 0; i < extrema.size(); i++) {
            int idx = extrema.get(i);
            boolean isPeak = idx > 0 && idx < dataPoints.size() - 1 &&
                    dataPoints.get(idx) > dataPoints.get(idx - 1) &&
                    dataPoints.get(idx) > dataPoints.get(idx + 1);
            
            if (isPeak == expectingPeak) {
                if (expectingPeak) {
                    cycleCount++;
                }
                expectingPeak = !expectingPeak; // Flip for next iteration
            }
        }
        
        return cycleCount;
    }
    
    /**
     * Analyze all metrics data for a project
     * 
     * @param projectId Project ID to analyze
     * @param metricName Metric name to analyze
     * @param apiClient Atlas API client
     * @param period Time period for analysis
     * @param granularity Data granularity
     * @return Map of instance identifiers to their pattern results
     */
    public Map<String, PatternResult> analyzeProjectMetricPatterns(
            String projectId, String metricName, AtlasApiClient apiClient,
            String period, String granularity) {
        
        Map<String, PatternResult> results = new HashMap<>();
        
        try {
            // Get all processes for this project
            List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
            logger.info("Analyzing patterns for {} processes in project {}", processes.size(), projectId);
            
            for (Map<String, Object> process : processes) {
                String typeName = (String) process.get("typeName");
                if (typeName.startsWith("SHARD_CONFIG") || typeName.equals("SHARD_MONGOS")) {
                    continue; // Skip config servers and mongos instances
                }
                
                String hostname = (String) process.get("hostname");
                int port = (int) process.get("port");
                String instanceId = hostname + ":" + port;
                
                try {
                    // For system metrics
                    if (!metricName.startsWith("DISK_")) {
                        List<Map<String, Object>> measurements = apiClient.getProcessMeasurements(
                                projectId, hostname, port, List.of(metricName), granularity, period);
                        
                        if (measurements != null && !measurements.isEmpty()) {
                            for (Map<String, Object> measurement : measurements) {
                                String name = (String) measurement.get("name");
                                
                                if (!name.equals(metricName)) {
                                    continue;
                                }
                                
                                List<Map<String, Object>> dataPoints = 
                                        (List<Map<String, Object>>) measurement.get("dataPoints");
                                
                                if (dataPoints != null && !dataPoints.isEmpty()) {
                                    List<Double> values = extractDataPointValues(dataPoints);
                                    PatternResult patternResult = analyzePattern(values);
                                    results.put(instanceId, patternResult);
                                    logger.info("Pattern for {} on {}: {}", 
                                            metricName, instanceId, patternResult);
                                }
                            }
                        }
                    } 
                    // For disk metrics
                    else {
                        List<Map<String, Object>> disks = apiClient.getProcessDisks(projectId, hostname, port);
                        
                        for (Map<String, Object> disk : disks) {
                            String partitionName = (String) disk.get("partitionName");
                            String diskId = instanceId + ":" + partitionName;
                            
                            List<Map<String, Object>> measurements = apiClient.getDiskMeasurements(
                                    projectId, hostname, port, partitionName, 
                                    List.of(metricName), granularity, period);
                            
                            if (measurements != null && !measurements.isEmpty()) {
                                for (Map<String, Object> measurement : measurements) {
                                    String name = (String) measurement.get("name");
                                    
                                    if (!name.equals(metricName)) {
                                        continue;
                                    }
                                    
                                    List<Map<String, Object>> dataPoints = 
                                            (List<Map<String, Object>>) measurement.get("dataPoints");
                                    
                                    if (dataPoints != null && !dataPoints.isEmpty()) {
                                        List<Double> values = extractDataPointValues(dataPoints);
                                        PatternResult patternResult = analyzePattern(values);
                                        results.put(diskId, patternResult);
                                        logger.info("Pattern for {} on {} partition {}: {}", 
                                                metricName, instanceId, partitionName, patternResult);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error analyzing pattern for {} on {}: {}", 
                            metricName, instanceId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error analyzing project {}: {}", projectId, e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Extract values from a list of data points
     */
    private List<Double> extractDataPointValues(List<Map<String, Object>> dataPoints) {
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
}