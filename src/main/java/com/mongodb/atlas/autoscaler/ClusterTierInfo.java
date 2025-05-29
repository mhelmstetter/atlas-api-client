package com.mongodb.atlas.autoscaler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.atlas.autoscaler.Autoscaler.ShardTierInfo;

/**
 * Container for cluster tier information including all shards and node types
 */
public class ClusterTierInfo {
    private final String clusterName;
    private final List<ShardTierInfo> shards = new ArrayList<>();
    
    public ClusterTierInfo(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public void addShard(ShardTierInfo shardInfo) {
        shards.add(shardInfo);
    }
    
    public String getClusterName() { return clusterName; }
    public List<ShardTierInfo> getShards() { return shards; }
    public int getShardCount() { return shards.size(); }
    
    /**
     * Check if all shards have the same electable tier
     */
    public boolean hasUniformElectableTiers() {
        if (shards.isEmpty()) return true;
        
        String firstTier = shards.get(0).getElectableInstanceSize();
        return shards.stream().allMatch(s -> 
            java.util.Objects.equals(s.getElectableInstanceSize(), firstTier));
    }
    
    /**
     * Get the primary electable tier (assumes uniform or returns first shard's tier)
     */
    public String getPrimaryElectableTier() {
        return shards.isEmpty() ? null : shards.get(0).getElectableInstanceSize();
    }
    
    /**
     * Get summary of all tiers in the cluster
     */
    public String getSummary() {
        if (shards.isEmpty()) return "No shards";
        
        if (hasUniformElectableTiers()) {
            return String.format("All shards: %s", getPrimaryElectableTier());
        } else {
            return shards.stream()
                    .map(s -> s.getShardId() + ":" + s.getElectableInstanceSize())
                    .collect(Collectors.joining(", "));
        }
    }
    
    /**
     * Create a scaled version of this cluster configuration
     */
    public ClusterTierInfo createScaledVersion(ScaleDirection direction, NodeType nodeType, 
                                             boolean scaleAllShards, int specificShardIndex) {
        ClusterTierInfo scaledInfo = new ClusterTierInfo(clusterName);
        
        for (int i = 0; i < shards.size(); i++) {
            ShardTierInfo originalShard = shards.get(i);
            ShardTierInfo scaledShard = new ShardTierInfo(originalShard.getShardId());
            
            // Copy existing specs
            scaledShard.copyFrom(originalShard);
            
            // Scale the appropriate shard(s) and node type
            boolean shouldScaleThisShard = scaleAllShards || (i == specificShardIndex);
            
            if (shouldScaleThisShard) {
                scaledShard.scaleNodeType(nodeType, direction);
            }
            
            scaledInfo.addShard(scaledShard);
        }
        
        return scaledInfo;
    }
    
    /**
     * Enum for different node types in Atlas clusters
     */
    public enum NodeType {
        ELECTABLE("electable", "Primary/Secondary nodes"),
        ANALYTICS("analytics", "Analytics nodes"),
        READ_ONLY("readOnly", "Read-only nodes");
        
        private final String apiName;
        private final String description;
        
        NodeType(String apiName, String description) {
            this.apiName = apiName;
            this.description = description;
        }
        
        public String getApiName() { return apiName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Enum for scaling direction
     */
    public enum ScaleDirection {
        UP, DOWN
    }
}