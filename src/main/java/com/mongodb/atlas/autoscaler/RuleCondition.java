package com.mongodb.atlas.autoscaler;

/**
 * Enum for rule condition types used in scaling rules
 * Defines how metric values should be compared against thresholds
 */
public enum RuleCondition {
    GREATER_THAN(">", "greater than"),
    LESS_THAN("<", "less than"),
    GREATER_THAN_OR_EQUAL(">=", "greater than or equal to"),
    LESS_THAN_OR_EQUAL("<=", "less than or equal to");
    
    private final String symbol;
    private final String description;
    
    RuleCondition(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }
    
    /**
     * Get the mathematical symbol for this condition
     */
    public String getSymbol() {
        return symbol;
    }
    
    /**
     * Get a human-readable description of this condition
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Evaluate the condition against two values
     * 
     * @param actualValue The actual metric value
     * @param threshold The threshold value to compare against
     * @return true if the condition is met, false otherwise
     */
    public boolean evaluate(double actualValue, double threshold) {
        switch (this) {
            case GREATER_THAN:
                return actualValue > threshold;
            case LESS_THAN:
                return actualValue < threshold;
            case GREATER_THAN_OR_EQUAL:
                return actualValue >= threshold;
            case LESS_THAN_OR_EQUAL:
                return actualValue <= threshold;
            default:
                return false;
        }
    }
    
    /**
     * Create a human-readable condition string
     * 
     * @param metricName The name of the metric
     * @param threshold The threshold value
     * @return A formatted condition string (e.g., "CPU > 90.0")
     */
    public String formatCondition(String metricName, double threshold) {
        return String.format("%s %s %.1f", metricName, symbol, threshold);
    }
    
    /**
     * Get the inverse condition (opposite)
     * Useful for creating complementary rules
     */
    public RuleCondition getInverse() {
        switch (this) {
            case GREATER_THAN:
                return LESS_THAN_OR_EQUAL;
            case LESS_THAN:
                return GREATER_THAN_OR_EQUAL;
            case GREATER_THAN_OR_EQUAL:
                return LESS_THAN;
            case LESS_THAN_OR_EQUAL:
                return GREATER_THAN;
            default:
                return this;
        }
    }
    
    /**
     * Check if this is a "high threshold" condition (greater than variants)
     */
    public boolean isHighThreshold() {
        return this == GREATER_THAN || this == GREATER_THAN_OR_EQUAL;
    }
    
    /**
     * Check if this is a "low threshold" condition (less than variants)
     */
    public boolean isLowThreshold() {
        return this == LESS_THAN || this == LESS_THAN_OR_EQUAL;
    }
    
    /**
     * Parse a condition from a string symbol
     * 
     * @param symbol The symbol to parse (">", "<", ">=", "<=")
     * @return The corresponding RuleCondition
     * @throws IllegalArgumentException if the symbol is not recognized
     */
    public static RuleCondition fromSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        
        for (RuleCondition condition : values()) {
            if (condition.symbol.equals(symbol.trim())) {
                return condition;
            }
        }
        
        throw new IllegalArgumentException("Unknown condition symbol: " + symbol);
    }
    
    @Override
    public String toString() {
        return symbol;
    }
}