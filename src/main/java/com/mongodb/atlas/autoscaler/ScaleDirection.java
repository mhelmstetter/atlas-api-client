package com.mongodb.atlas.autoscaler;

/**
 * Enum for scaling direction in autoscaling operations
 * Defines whether to scale cluster resources up or down
 */
public enum ScaleDirection {
    UP("up", "Scale Up", "Increase cluster resources", 1),
    DOWN("down", "Scale Down", "Decrease cluster resources", -1);
    
    private final String value;
    private final String displayName;
    private final String description;
    private final int multiplier;
    
    ScaleDirection(String value, String displayName, String description, int multiplier) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
        this.multiplier = multiplier;
    }
    
    /**
     * Get the string value for this direction
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Get a human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get a detailed description of this scaling direction
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the multiplier for tier progression calculations
     * UP = +1, DOWN = -1
     */
    public int getMultiplier() {
        return multiplier;
    }
    
    /**
     * Get the opposite scaling direction
     */
    public ScaleDirection getOpposite() {
        return this == UP ? DOWN : UP;
    }
    
    /**
     * Check if this is an upward scaling direction
     */
    public boolean isUp() {
        return this == UP;
    }
    
    /**
     * Check if this is a downward scaling direction
     */
    public boolean isDown() {
        return this == DOWN;
    }
    
    /**
     * Calculate the target tier index based on current index and direction
     * 
     * @param currentIndex The current tier index
     * @param steps Number of tiers to move (default 1)
     * @return The target tier index
     */
    public int calculateTargetIndex(int currentIndex, int steps) {
        if (steps < 1) {
            steps = 1; // Always move at least one tier
        }
        return currentIndex + (multiplier * steps);
    }
    
    /**
     * Calculate the target tier index (single step)
     */
    public int calculateTargetIndex(int currentIndex) {
        return calculateTargetIndex(currentIndex, 1);
    }
    
    /**
     * Format a scaling action message
     * 
     * @param resourceName The name of the resource being scaled
     * @param fromValue The current value/tier
     * @param toValue The target value/tier
     * @return A formatted message describing the scaling action
     */
    public String formatScalingMessage(String resourceName, String fromValue, String toValue) {
        return String.format("%s %s: %s → %s", displayName, resourceName, fromValue, toValue);
    }
    
    /**
     * Get an appropriate emoji/icon for this scaling direction
     */
    public String getIcon() {
        return this == UP ? "↗" : "↘";
    }
    
    /**
     * Parse a scaling direction from a string value
     * 
     * @param value The string to parse ("up", "down", case-insensitive)
     * @return The corresponding ScaleDirection
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static ScaleDirection fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Scale direction value cannot be null");
        }
        
        String normalized = value.trim().toLowerCase();
        for (ScaleDirection direction : values()) {
            if (direction.value.equals(normalized)) {
                return direction;
            }
        }
        
        throw new IllegalArgumentException("Unknown scale direction: " + value);
    }
    
    /**
     * Parse from common alternative representations
     * 
     * @param input Various string representations (up/down, increase/decrease, +/-, etc.)
     * @return The corresponding ScaleDirection
     * @throws IllegalArgumentException if the input is not recognized
     */
    public static ScaleDirection parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Scale direction input cannot be null");
        }
        
        String normalized = input.trim().toLowerCase();
        
        // Direct matches
        if (normalized.equals("up") || normalized.equals("scale_up") || 
            normalized.equals("increase") || normalized.equals("grow") ||
            normalized.equals("+") || normalized.equals("1")) {
            return UP;
        }
        
        if (normalized.equals("down") || normalized.equals("scale_down") || 
            normalized.equals("decrease") || normalized.equals("shrink") ||
            normalized.equals("-") || normalized.equals("-1")) {
            return DOWN;
        }
        
        throw new IllegalArgumentException("Cannot parse scale direction from: " + input);
    }
    
    /**
     * Create a scaling direction based on a numeric comparison
     * 
     * @param current Current value
     * @param target Target value
     * @return UP if target > current, DOWN if target < current
     * @throws IllegalArgumentException if values are equal
     */
    public static ScaleDirection fromComparison(double current, double target) {
        if (target > current) {
            return UP;
        } else if (target < current) {
            return DOWN;
        } else {
            throw new IllegalArgumentException("Cannot determine scale direction: values are equal");
        }
    }
    
    /**
     * Determine scaling direction based on resource utilization and thresholds
     * 
     * @param currentUtilization Current resource utilization (0.0 to 1.0)
     * @param scaleUpThreshold Threshold above which to scale up
     * @param scaleDownThreshold Threshold below which to scale down
     * @return The appropriate scaling direction, or null if no scaling needed
     */
    public static ScaleDirection determineFromUtilization(double currentUtilization, 
                                                        double scaleUpThreshold, 
                                                        double scaleDownThreshold) {
        if (currentUtilization >= scaleUpThreshold) {
            return UP;
        } else if (currentUtilization <= scaleDownThreshold) {
            return DOWN;
        } else {
            return null; // No scaling needed
        }
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}