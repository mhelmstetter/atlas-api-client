package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for MongoDB Atlas Flex Clusters API
 * Handles Flex cluster operations - a cost-effective cluster tier for development and low-throughput applications
 * 
 * Flex Clusters Features:
 * - Pay-as-you-go pricing ($8-$30/month based on usage)
 * - Scales from 0-500 operations per second
 * - Shared infrastructure (cost-effective)
 * - Suitable for development, learning, and MVP applications
 * - Can be upgraded to dedicated clusters
 */
public class AtlasFlexClustersClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasFlexClustersClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasFlexClustersClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all Flex clusters in a project
     * 
     * @param projectId The Atlas project ID
     * @return List of Flex clusters
     */
    public List<Map<String, Object>> getFlexClusters(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters";
        logger.info("Fetching Flex (serverless) clusters for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific Flex cluster by name
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @return Map containing Flex cluster information
     */
    public Map<String, Object> getFlexCluster(String projectId, String clusterName) {
        logger.info("Getting Flex cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters/" + clusterName;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> cluster = objectMapper.readValue(responseBody, Map.class);
            logger.debug("Flex cluster '{}' status: {}", clusterName, cluster.get("stateName"));
            
            return cluster;
            
        } catch (Exception e) {
            logger.error("Failed to get Flex cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get Flex cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Create a new Flex cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name for the new Flex cluster
     * @param mongoVersion The MongoDB version (e.g., "7.0", "6.0")
     * @param region The cloud region (e.g., "US_EAST_1")
     * @param cloudProvider The cloud provider (e.g., "AWS", "GCP", "AZURE")
     * @return Map containing Flex cluster creation response
     */
    public Map<String, Object> createFlexCluster(String projectId, String clusterName, 
                                                String mongoVersion, String region, String cloudProvider) {
        logger.info("Creating Flex cluster '{}' in project {}", clusterName, projectId);
        
        try {
            Map<String, Object> clusterSpec = buildFlexClusterSpec(clusterName, mongoVersion, region, cloudProvider);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters";
            String requestBody = objectMapper.writeValueAsString(clusterSpec);
            
            logger.debug("Flex cluster creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Flex cluster '{}' creation initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create Flex cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create Flex cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Update an existing Flex cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @param updateSpec The update specification (e.g., terminationProtectionEnabled)
     * @return Map containing update response
     */
    public Map<String, Object> updateFlexCluster(String projectId, String clusterName, 
                                                Map<String, Object> updateSpec) {
        logger.info("Updating Flex cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters/" + clusterName;
            String requestBody = objectMapper.writeValueAsString(updateSpec);
            
            logger.debug("Flex cluster update payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Flex cluster '{}' updated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to update Flex cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update Flex cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Delete a Flex cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name to delete
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteFlexCluster(String projectId, String clusterName) {
        logger.info("Deleting Flex cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters/" + clusterName;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response;
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                response = objectMapper.readValue(responseBody, Map.class);
            } else {
                // DELETE often returns empty body - create a simple success response
                response = new HashMap<>();
                response.put("status", "DELETING");
                response.put("message", "Flex cluster deletion initiated successfully");
            }
            logger.info("Flex cluster '{}' deletion initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete Flex cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete Flex cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Upgrade a Flex cluster to a dedicated cluster (M10+)
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name to upgrade
     * @param targetInstanceSize The target dedicated instance size (e.g., "M10", "M20")
     * @return Map containing upgrade response
     */
    public Map<String, Object> upgradeFlexCluster(String projectId, String clusterName, String targetInstanceSize) {
        logger.info("Upgrading Flex cluster '{}' to dedicated cluster {} in project {}", 
                   clusterName, targetInstanceSize, projectId);
        
        try {
            Map<String, Object> upgradeSpec = new HashMap<>();
            upgradeSpec.put("instanceSize", targetInstanceSize);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/flexClusters/" + clusterName + "/upgrade";
            String requestBody = objectMapper.writeValueAsString(upgradeSpec);
            
            logger.debug("Flex cluster upgrade payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Flex cluster '{}' upgrade to {} initiated successfully", clusterName, targetInstanceSize);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to upgrade Flex cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to upgrade Flex cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Enable termination protection for a Flex cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @return Map containing update response
     */
    public Map<String, Object> enableTerminationProtection(String projectId, String clusterName) {
        logger.info("Enabling termination protection for Flex cluster '{}' in project {}", clusterName, projectId);
        
        Map<String, Object> updateSpec = new HashMap<>();
        updateSpec.put("terminationProtectionEnabled", true);
        
        return updateFlexCluster(projectId, clusterName, updateSpec);
    }

    /**
     * Disable termination protection for a Flex cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @return Map containing update response
     */
    public Map<String, Object> disableTerminationProtection(String projectId, String clusterName) {
        logger.info("Disabling termination protection for Flex cluster '{}' in project {}", clusterName, projectId);
        
        Map<String, Object> updateSpec = new HashMap<>();
        updateSpec.put("terminationProtectionEnabled", false);
        
        return updateFlexCluster(projectId, clusterName, updateSpec);
    }

    /**
     * Wait for Flex cluster to reach a target state
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @param targetState The desired state (e.g., "IDLE", "CREATING")
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return true if cluster reached target state, false if timeout
     */
    public boolean waitForFlexClusterState(String projectId, String clusterName, 
                                         String targetState, int timeoutSeconds) {
        logger.info("Waiting for Flex cluster '{}' to reach state '{}'", clusterName, targetState);
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Map<String, Object> cluster = getFlexCluster(projectId, clusterName);
                String currentState = (String) cluster.get("stateName");
                
                if (targetState.equals(currentState)) {
                    logger.info("Flex cluster '{}' reached target state '{}'", clusterName, targetState);
                    return true;
                }
                
                logger.debug("Flex cluster '{}' current state: {}, waiting for: {}", 
                           clusterName, currentState, targetState);
                
                Thread.sleep(15000); // Wait 15 seconds between checks (Flex clusters are faster to provision)
                
            } catch (Exception e) {
                logger.warn("Error checking Flex cluster state: {}", e.getMessage());
                try {
                    Thread.sleep(5000); // Wait 5 seconds on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        logger.warn("Timeout waiting for Flex cluster '{}' to reach state '{}'", clusterName, targetState);
        return false;
    }

    /**
     * Build Flex cluster specification for Atlas API
     */
    private Map<String, Object> buildFlexClusterSpec(String clusterName, String mongoVersion, 
                                                    String region, String cloudProvider) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", clusterName);
        
        // Flex cluster provider settings - according to Atlas API docs
        Map<String, Object> providerSettings = new HashMap<>();
        providerSettings.put("backingProviderName", cloudProvider.toUpperCase());
        providerSettings.put("regionName", region.toUpperCase());
        
        spec.put("providerSettings", providerSettings);
        spec.put("terminationProtectionEnabled", false);
        
        // MongoDB version is optional for Flex clusters - they use latest available
        // if (mongoVersion != null) {
        //     spec.put("mongoDBMajorVersion", mongoVersion);
        // }
        
        return spec;
    }

    /**
     * Get Flex cluster connection information
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The Flex cluster name
     * @return Map containing connection strings and endpoints
     */
    public Map<String, Object> getFlexClusterConnectionInfo(String projectId, String clusterName) {
        logger.info("Getting connection info for Flex cluster '{}' in project {}", clusterName, projectId);
        
        Map<String, Object> cluster = getFlexCluster(projectId, clusterName);
        Map<String, Object> connectionInfo = new HashMap<>();
        
        // Extract connection strings if available
        if (cluster.containsKey("connectionStrings")) {
            connectionInfo.put("connectionStrings", cluster.get("connectionStrings"));
        }
        
        // Extract basic cluster info
        connectionInfo.put("clusterName", cluster.get("name"));
        connectionInfo.put("stateName", cluster.get("stateName"));
        connectionInfo.put("mongoDBVersion", cluster.get("mongoDBVersion"));
        
        return connectionInfo;
    }
}