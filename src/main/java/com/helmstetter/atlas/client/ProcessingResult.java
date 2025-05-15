package com.helmstetter.atlas.client;

/**
 * Contains the result of processing a set of metric values
 */
public class ProcessingResult {
    private final double minValue;
    private final double maxValue;
    private final double avgValue;
    
    public ProcessingResult(double minValue, double maxValue, double avgValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.avgValue = avgValue;
    }
    
    public double getMinValue() {
        return minValue;
    }
    
    public double getMaxValue() {
        return maxValue;
    }
    
    public double getAvgValue() {
        return avgValue;
    }
}