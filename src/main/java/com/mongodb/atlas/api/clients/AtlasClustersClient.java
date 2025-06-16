package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AtlasClustersClient {

	private static final Logger logger = LoggerFactory.getLogger(AtlasClustersClient.class);

	private final AtlasApiBase apiBase;
	private final ObjectMapper objectMapper;

	public AtlasClustersClient(AtlasApiBase apiBase) {
		this.apiBase = apiBase;
		this.objectMapper = new ObjectMapper();
	}
	
    /**
     * Get all clusters in a project
     */
    public List<Map<String, Object>> getClusters(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters";
        logger.info("Fetching clusters for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }
    
    /**
     * Get all projects matching the specified names
     */
    public Map<String, String> getProjects(Set<String> includeProjectNames) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups";
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2);

        try {
            List<Map<String, Object>> projects = apiBase.extractResults(responseBody);
            
            return projects.stream()
                    .filter(p -> includeProjectNames.isEmpty() || includeProjectNames.contains(p.get("name")))
                    .collect(Collectors.toMap(
                            p -> (String) p.get("name"), 
                            p -> (String) p.get("id")));
        } catch (Exception e) {
            logger.error("Failed to retrieve projects: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to retrieve projects", e);
        }
    }
    
    /**
     * Get all processes for a project
     */
    public List<Map<String, Object>> getProcesses(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/processes";
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get processes for a specific cluster by cluster name
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster
     * @return List of process objects for the specified cluster
     */
    // TODO this is busted, does not filter clusterName
    public List<Map<String, Object>> getProcessesForCluster(String projectId, String clusterName) {
        logger.info("Fetching processes for cluster '{}' in project {}", clusterName, projectId);
        
        try {
            // Get all processes for the project
            List<Map<String, Object>> allProcesses = getProcesses(projectId);
            
            // Filter processes that belong to the specified cluster
//            List<Map<String, Object>> clusterProcesses = allProcesses.stream()
//                    .filter(process -> {
//                        String processClusterName = (String) process.get("clusterName");
//                        return clusterName.equals(processClusterName);
//                    })
//                    .collect(Collectors.toList());
            
            logger.info("Found {} processes in project {}", 
            		allProcesses.size(), projectId);
            
            
            return allProcesses;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve processes for cluster '{}' in project {}: {}", 
                    clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to retrieve processes for cluster '" + clusterName + "'", e);
        }
    }
    
    /**
     * Create a new Atlas cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name for the new cluster
     * @param instanceSize The instance size (e.g., "M10", "M20", etc.)
     * @param mongoVersion The MongoDB version (e.g., "7.0", "6.0")
     * @param region The cloud region (e.g., "US_EAST_1")
     * @param cloudProvider The cloud provider (e.g., "AWS", "GCP", "AZURE")
     * @return Map containing cluster creation response
     */
    public Map<String, Object> createCluster(String projectId, String clusterName, 
                                           String instanceSize, String mongoVersion, 
                                           String region, String cloudProvider) {
        logger.info("Creating Atlas cluster '{}' in project {}", clusterName, projectId);
        
        try {
            Map<String, Object> clusterSpec = buildClusterSpec(clusterName, instanceSize, 
                                                             mongoVersion, region, cloudProvider);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters";
            String requestBody = objectMapper.writeValueAsString(clusterSpec);
            
            logger.debug("Cluster creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Cluster '{}' creation initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create cluster '" + clusterName + "'", e);
        }
    }
    
    /**
     * Get cluster status by name
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @return Map containing cluster information including status
     */
    public Map<String, Object> getCluster(String projectId, String clusterName) {
        logger.info("Getting cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> cluster = objectMapper.readValue(responseBody, Map.class);
            logger.debug("Cluster '{}' status: {}", clusterName, cluster.get("stateName"));
            
            return cluster;
            
        } catch (Exception e) {
            logger.error("Failed to get cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get cluster '" + clusterName + "'", e);
        }
    }
    
    /**
     * Wait for cluster to reach a target state
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param targetState The desired state (e.g., "IDLE", "CREATING")
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return true if cluster reached target state, false if timeout
     */
    public boolean waitForClusterState(String projectId, String clusterName, 
                                     String targetState, int timeoutSeconds) {
        logger.info("Waiting for cluster '{}' to reach state '{}'", clusterName, targetState);
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Map<String, Object> cluster = getCluster(projectId, clusterName);
                String currentState = (String) cluster.get("stateName");
                
                if (targetState.equals(currentState)) {
                    logger.info("Cluster '{}' reached target state '{}'", clusterName, targetState);
                    return true;
                }
                
                logger.debug("Cluster '{}' current state: {}, waiting for: {}", 
                           clusterName, currentState, targetState);
                
                Thread.sleep(30000); // Wait 30 seconds between checks
                
            } catch (Exception e) {
                logger.warn("Error checking cluster state: {}", e.getMessage());
                try {
                    Thread.sleep(10000); // Wait 10 seconds on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        logger.warn("Timeout waiting for cluster '{}' to reach state '{}'", clusterName, targetState);
        return false;
    }
    
    /**
     * Build cluster specification for Atlas API
     */
    private Map<String, Object> buildClusterSpec(String clusterName, String instanceSize, 
                                                String mongoVersion, String region, 
                                                String cloudProvider) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", clusterName);
        spec.put("mongoDBMajorVersion", mongoVersion);
        spec.put("clusterType", "REPLICASET");
        
        // Build replication specs following Atlas SDK format
        Map<String, Object> replicationSpec = new HashMap<>();
        
        // Region configs array
        Map<String, Object> regionConfig = new HashMap<>();
        regionConfig.put("providerName", cloudProvider.toUpperCase());
        regionConfig.put("priority", 7);
        regionConfig.put("regionName", region.toUpperCase());
        
        // Electable specs for dedicated clusters
        Map<String, Object> electableSpecs = new HashMap<>();
        electableSpecs.put("instanceSize", instanceSize);
        electableSpecs.put("nodeCount", 3);
        regionConfig.put("electableSpecs", electableSpecs);
        
        replicationSpec.put("regionConfigs", List.of(regionConfig));
        spec.put("replicationSpecs", List.of(replicationSpec));
        
        return spec;
    }
    
    /**
     * Delete an Atlas cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster to delete
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteCluster(String projectId, String clusterName) {
        logger.info("Deleting Atlas cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Cluster '{}' deletion initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete cluster '" + clusterName + "'", e);
        }
    }
}