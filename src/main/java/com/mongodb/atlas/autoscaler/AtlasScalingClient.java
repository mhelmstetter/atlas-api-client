package com.mongodb.atlas.autoscaler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.AtlasApiClient;
import com.mongodb.atlas.autoscaler.Autoscaler.ShardTierInfo;

/**
 * Client for executing scaling operations on MongoDB Atlas clusters
 */
public class AtlasScalingClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasScalingClient.class);
    private static final String ATLAS_BASE_URL = "https://cloud.mongodb.com/api/atlas/v1.0";
    
    private final String publicKey;
    private final String privateKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtlasApiClient apiClient;
    
    public AtlasScalingClient(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiClient = new AtlasApiClient(publicKey, privateKey, 1);
    }
    
    /**
     * Scale a cluster to the specified tier configuration
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster to scale
     * @param targetTierInfo The target tier configuration
     * @return true if scaling was initiated successfully, false otherwise
     */
    public boolean scaleCluster(String projectId, String clusterName, ClusterTierInfo targetTierInfo) {
        try {
            logger.info("Initiating scaling operation for cluster {} in project {}", clusterName, projectId);
            
            // Get current cluster configuration
            Map<String, Object> currentCluster = getCurrentCluster(projectId, clusterName);
            if (currentCluster == null) {
                logger.error("Could not retrieve current cluster configuration for {}", clusterName);
                return false;
            }
            
            // Build the patch request body with new replication specs
            Map<String, Object> patchRequest = buildScalingRequest(currentCluster, targetTierInfo);
            
            // Execute the scaling request
            String url = String.format("%s/groups/%s/clusters/%s", ATLAS_BASE_URL, projectId, clusterName);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(patchRequest)))
                    .build();
            
            // Add authentication
            request = addDigestAuthentication(request);
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                logger.info("Successfully initiated scaling for cluster {}", clusterName);
                return true;
            } else {
                logger.error("Failed to scale cluster {}: HTTP {} - {}", 
                        clusterName, response.statusCode(), response.body());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error scaling cluster {}: {}", clusterName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if a cluster is currently in a scaling state
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster
     * @return true if the cluster is currently scaling, false otherwise
     */
    public boolean isClusterScaling(String projectId, String clusterName) {
        try {
            Map<String, Object> cluster = getCurrentCluster(projectId, clusterName);
            if (cluster == null) {
                return false;
            }
            
            String stateName = (String) cluster.get("stateName");
            return !"IDLE".equals(stateName);
            
        } catch (Exception e) {
            logger.error("Error checking cluster scaling status for {}: {}", clusterName, e.getMessage());
            return true; // Assume scaling to be safe
        }
    }
    
    /**
     * Get current cluster configuration
     */
    private Map<String, Object> getCurrentCluster(String projectId, String clusterName) {
        try {
            List<Map<String, Object>> clusters = apiClient.getClusters(projectId);
            
            for (Map<String, Object> cluster : clusters) {
                if (clusterName.equals(cluster.get("name"))) {
                    return cluster;
                }
            }
            
            logger.warn("Cluster {} not found in project {}", clusterName, projectId);
            return null;
            
        } catch (Exception e) {
            logger.error("Error retrieving cluster {}: {}", clusterName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Build the scaling request body with updated replication specs
     */
    private Map<String, Object> buildScalingRequest(Map<String, Object> currentCluster, ClusterTierInfo targetTierInfo) {
        Map<String, Object> request = new HashMap<>();
        
        // Build new replication specs based on target tier info
        List<Map<String, Object>> newReplicationSpecs = new ArrayList<>();
        
        for (ShardTierInfo shardInfo : targetTierInfo.getShards()) {
            Map<String, Object> replicationSpec = new HashMap<>();
            
            // Add electable specs (primary/secondary nodes)
            if (shardInfo.getElectableInstanceSize() != null && shardInfo.getElectableNodeCount() != null) {
                Map<String, Object> electableSpecs = new HashMap<>();
                electableSpecs.put("instanceSize", shardInfo.getElectableInstanceSize());
                electableSpecs.put("nodeCount", shardInfo.getElectableNodeCount());
                replicationSpec.put("electableSpecs", electableSpecs);
            }
            
            // Add analytics specs if present
            if (shardInfo.getAnalyticsInstanceSize() != null && shardInfo.getAnalyticsNodeCount() != null) {
                Map<String, Object> analyticsSpecs = new HashMap<>();
                analyticsSpecs.put("instanceSize", shardInfo.getAnalyticsInstanceSize());
                analyticsSpecs.put("nodeCount", shardInfo.getAnalyticsNodeCount());
                replicationSpec.put("analyticsSpecs", analyticsSpecs);
            }
            
            // Add read-only specs if present
            if (shardInfo.getReadOnlyInstanceSize() != null && shardInfo.getReadOnlyNodeCount() != null) {
                Map<String, Object> readOnlySpecs = new HashMap<>();
                readOnlySpecs.put("instanceSize", shardInfo.getReadOnlyInstanceSize());
                readOnlySpecs.put("nodeCount", shardInfo.getReadOnlyNodeCount());
                replicationSpec.put("readOnlySpecs", readOnlySpecs);
            }
            
            // Add region configurations (copy from current cluster)
            List<Map<String, Object>> currentReplicationSpecs = 
                    (List<Map<String, Object>>) currentCluster.get("replicationSpecs");
            
            if (currentReplicationSpecs != null && !currentReplicationSpecs.isEmpty()) {
                // Use the region config from the first shard (assuming uniform regions)
                Map<String, Object> firstShard = currentReplicationSpecs.get(0);
                if (firstShard.containsKey("regionConfigs")) {
                    replicationSpec.put("regionConfigs", firstShard.get("regionConfigs"));
                }
                if (firstShard.containsKey("zoneName")) {
                    replicationSpec.put("zoneName", firstShard.get("zoneName"));
                }
            }
            
            newReplicationSpecs.add(replicationSpec);
        }
        
        request.put("replicationSpecs", newReplicationSpecs);
        
        // Copy other important fields from current cluster that shouldn't change
        String[] preserveFields = {
            "name", "clusterType", "mongoDBMajorVersion", "backupEnabled",
            "providerBackupEnabled", "autoScaling", "mongoDBVersion",
            "diskSizeGB", "encryptionAtRestProvider", "labels", "mongoURI",
            "mongoURIUpdated", "mongoURIWithOptions", "paused", "pitEnabled",
            "rootCertType", "srvAddress", "stateName", "connectionStrings"
        };
        
        for (String field : preserveFields) {
            if (currentCluster.containsKey(field)) {
                request.put(field, currentCluster.get(field));
            }
        }
        
        return request;
    }
    
    /**
     * Add digest authentication to the HTTP request
     * Note: This is a simplified implementation. In production, you would use a proper
     * HTTP client that handles digest authentication automatically.
     */
    private HttpRequest addDigestAuthentication(HttpRequest request) {
        // For simplicity, we'll use basic auth here
        // In production, Atlas API uses digest authentication
        String credentials = publicKey + ":" + privateKey;
        String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        
        return HttpRequest.newBuilder(request, (name, value) -> true)
                .header("Authorization", "Basic " + encodedCredentials)
                .build();
    }
    
    /**
     * Validate that the target tier configuration is valid for scaling
     */
    public boolean validateScalingOperation(String projectId, String clusterName, ClusterTierInfo targetTierInfo) {
        try {
            Map<String, Object> currentCluster = getCurrentCluster(projectId, clusterName);
            if (currentCluster == null) {
                return false;
            }
            
            // Check cluster state
            String stateName = (String) currentCluster.get("stateName");
            if (!"IDLE".equals(stateName)) {
                logger.warn("Cluster {} is not in IDLE state (current: {}), cannot scale", clusterName, stateName);
                return false;
            }
            
            // Validate tier progression
            List<String> validTiers = List.of(
                    "M0", "M2", "M5", "M10", "M20", "M30", "M40", "M50", "M60", "M80", 
                    "M140", "M200", "M300", "M400", "M700"
            );
            
            for (ShardTierInfo shardInfo : targetTierInfo.getShards()) {
                // Validate electable tier
                if (shardInfo.getElectableInstanceSize() != null && 
                    !validTiers.contains(shardInfo.getElectableInstanceSize())) {
                    logger.error("Invalid electable tier: {}", shardInfo.getElectableInstanceSize());
                    return false;
                }
                
                // Validate analytics tier
                if (shardInfo.getAnalyticsInstanceSize() != null && 
                    !validTiers.contains(shardInfo.getAnalyticsInstanceSize())) {
                    logger.error("Invalid analytics tier: {}", shardInfo.getAnalyticsInstanceSize());
                    return false;
                }
                
                // Validate read-only tier
                if (shardInfo.getReadOnlyInstanceSize() != null && 
                    !validTiers.contains(shardInfo.getReadOnlyInstanceSize())) {
                    logger.error("Invalid read-only tier: {}", shardInfo.getReadOnlyInstanceSize());
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating scaling operation: {}", e.getMessage());
            return false;
        }
    }
}