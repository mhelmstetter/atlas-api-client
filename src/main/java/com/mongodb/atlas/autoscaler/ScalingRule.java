package com.mongodb.atlas.autoscaler;

import java.time.Duration;

/**
 * Configuration class for scaling rules
 * Defines when and how clusters should be scaled based on metric thresholds
 */
public class ScalingRule {
    private final String name;
    private final String metricName;
    private final RuleCondition condition;
    private final double threshold;
    private final Duration duration;
    private final ScaleDirection scaleDirection;
    private final Duration cooldownPeriod;
    private final ClusterTierInfo.NodeType nodeType;
    private final boolean scaleAllShards;
    private final int specificShardIndex; // Only used if scaleAllShards is false
    
    /**
     * Create a scaling rule with default settings (ELECTABLE nodes, all shards)
     */
    public ScalingRule(String name, String metricName, RuleCondition condition, 
                     double threshold, Duration duration, ScaleDirection scaleDirection,
                     Duration cooldownPeriod) {
        this(name, metricName, condition, threshold, duration, scaleDirection, 
             cooldownPeriod, ClusterTierInfo.NodeType.ELECTABLE, true, 0);
    }
    
    /**
     * Create a scaling rule with full configuration options
     */
    public ScalingRule(String name, String metricName, RuleCondition condition, 
                     double threshold, Duration duration, ScaleDirection scaleDirection,
                     Duration cooldownPeriod, ClusterTierInfo.NodeType nodeType, 
                     boolean scaleAllShards, int specificShardIndex) {
        this.name = name;
        this.metricName = metricName;
        this.condition = condition;
        this.threshold = threshold;
        this.duration = duration;
        this.scaleDirection = scaleDirection;
        this.cooldownPeriod = cooldownPeriod;
        this.nodeType = nodeType;
        this.scaleAllShards = scaleAllShards;
        this.specificShardIndex = specificShardIndex;
    }
    
    // Getters
    public String getName() { return name; }
    public String getMetricName() { return metricName; }
    public RuleCondition getCondition() { return condition; }
    public double getThreshold() { return threshold; }
    public Duration getDuration() { return duration; }
    public ScaleDirection getScaleDirection() { return scaleDirection; }
    public Duration getCooldownPeriod() { return cooldownPeriod; }
    public ClusterTierInfo.NodeType getNodeType() { return nodeType; }
    public boolean isScaleAllShards() { return scaleAllShards; }
    public int getSpecificShardIndex() { return specificShardIndex; }
    
    /**
     * Create a builder for easier rule construction
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ScalingRule{")
          .append("name='").append(name).append('\'')
          .append(", metric='").append(metricName).append('\'')
          .append(", condition=").append(condition)
          .append(", threshold=").append(threshold)
          .append(", duration=").append(duration)
          .append(", direction=").append(scaleDirection)
          .append(", nodeType=").append(nodeType)
          .append(", scaleAllShards=").append(scaleAllShards);
        
        if (!scaleAllShards) {
            sb.append(", shardIndex=").append(specificShardIndex);
        }
        
        sb.append(", cooldown=").append(cooldownPeriod)
          .append('}');
        
        return sb.toString();
    }
    
    /**
     * Builder pattern for creating scaling rules
     */
    public static class Builder {
        private final String name;
        private String metricName;
        private RuleCondition condition;
        private double threshold;
        private Duration duration;
        private ScaleDirection scaleDirection;
        private Duration cooldownPeriod = Duration.ofMinutes(30); // Default cooldown
        private ClusterTierInfo.NodeType nodeType = ClusterTierInfo.NodeType.ELECTABLE; // Default node type
        private boolean scaleAllShards = true; // Default to scaling all shards
        private int specificShardIndex = 0; // Default shard index if not scaling all
        
        public Builder(String name) {
            this.name = name;
        }
        
        public Builder metric(String metricName) {
            this.metricName = metricName;
            return this;
        }
        
        public Builder condition(RuleCondition condition) {
            this.condition = condition;
            return this;
        }
        
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }
        
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }
        
        public Builder scaleDirection(ScaleDirection scaleDirection) {
            this.scaleDirection = scaleDirection;
            return this;
        }
        
        public Builder cooldown(Duration cooldownPeriod) {
            this.cooldownPeriod = cooldownPeriod;
            return this;
        }
        
        public Builder nodeType(ClusterTierInfo.NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }
        
        public Builder scaleAllShards(boolean scaleAllShards) {
            this.scaleAllShards = scaleAllShards;
            return this;
        }
        
        public Builder specificShard(int shardIndex) {
            this.specificShardIndex = shardIndex;
            this.scaleAllShards = false; // Automatically set to false when specifying a shard
            return this;
        }
        
        public ScalingRule build() {
            // Validate required fields
            if (metricName == null || metricName.trim().isEmpty()) {
                throw new IllegalArgumentException("Metric name is required");
            }
            if (condition == null) {
                throw new IllegalArgumentException("Rule condition is required");
            }
            if (duration == null) {
                throw new IllegalArgumentException("Duration is required");
            }
            if (scaleDirection == null) {
                throw new IllegalArgumentException("Scale direction is required");
            }
            if (threshold < 0) {
                throw new IllegalArgumentException("Threshold must be non-negative");
            }
            
            return new ScalingRule(name, metricName, condition, threshold, duration, 
                                 scaleDirection, cooldownPeriod, nodeType, scaleAllShards, 
                                 specificShardIndex);
        }
    }
    
    /**
     * Enum for rule condition types
     */
    public enum RuleCondition {
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_THAN_OR_EQUAL(">="),
        LESS_THAN_OR_EQUAL("<=");
        
        private final String symbol;
        
        RuleCondition(String symbol) {
            this.symbol = symbol;
        }
        
        public String getSymbol() {
            return symbol;
        }
        
        @Override
        public String toString() {
            return symbol;
        }
    }
    
    /**
     * Predefined scaling rules for common scenarios
     */
    public static class CommonRules {
        
        /**
         * CPU scale-up rule: Scale up when CPU > threshold for duration
         */
        public static ScalingRule cpuScaleUp(String name, double thresholdPercent, Duration duration) {
            return builder(name)
                    .metric("SYSTEM_NORMALIZED_CPU_USER")
                    .condition(RuleCondition.GREATER_THAN)
                    .threshold(thresholdPercent)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.UP)
                    .build();
        }
        
        /**
         * CPU scale-down rule: Scale down when CPU < threshold for duration
         */
        public static ScalingRule cpuScaleDown(String name, double thresholdPercent, Duration duration) {
            return builder(name)
                    .metric("SYSTEM_NORMALIZED_CPU_USER")
                    .condition(RuleCondition.LESS_THAN)
                    .threshold(thresholdPercent)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.DOWN)
                    .cooldown(Duration.ofMinutes(60)) // Longer cooldown for scale-down
                    .build();
        }
        
        /**
         * Memory scale-up rule: Scale up when memory usage > threshold for duration
         */
        public static ScalingRule memoryScaleUp(String name, double thresholdPercent, Duration duration) {
            return builder(name)
                    .metric("SYSTEM_MEMORY_USED")
                    .condition(RuleCondition.GREATER_THAN)
                    .threshold(thresholdPercent)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.UP)
                    .build();
        }
        
        /**
         * Disk IOPS scale-up rule: Scale up when disk IOPS > threshold for duration
         */
        public static ScalingRule diskIopsScaleUp(String name, double thresholdIops, Duration duration) {
            return builder(name)
                    .metric("DISK_PARTITION_IOPS_TOTAL")
                    .condition(RuleCondition.GREATER_THAN)
                    .threshold(thresholdIops)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.UP)
                    .build();
        }
        
        /**
         * Analytics node scale-up rule: Scale analytics nodes when CPU > threshold
         */
        public static ScalingRule analyticsScaleUp(String name, double thresholdPercent, Duration duration) {
            return builder(name)
                    .metric("SYSTEM_NORMALIZED_CPU_USER")
                    .condition(RuleCondition.GREATER_THAN)
                    .threshold(thresholdPercent)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.UP)
                    .nodeType(ClusterTierInfo.NodeType.ANALYTICS)
                    .build();
        }
        
        /**
         * Per-shard scaling rule: Scale specific shard when metric exceeds threshold
         */
        public static ScalingRule perShardScale(String name, String metric, double threshold, 
                                              Duration duration, int shardIndex) {
            return builder(name)
                    .metric(metric)
                    .condition(RuleCondition.GREATER_THAN)
                    .threshold(threshold)
                    .duration(duration)
                    .scaleDirection(ScaleDirection.UP)
                    .specificShard(shardIndex)
                    .build();
        }
    }
}