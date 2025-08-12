package com.mongodb.atlas.api.clients;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                    .filter(p -> includeProjectNames == null || includeProjectNames.isEmpty() || includeProjectNames.contains(p.get("name")))
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
     * Create a new Atlas replica set cluster
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
     * Create a new Atlas sharded cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name for the new cluster
     * @param instanceSize The instance size (must be M30+ for sharded clusters)
     * @param mongoVersion The MongoDB version (e.g., "7.0", "6.0")
     * @param region The cloud region (e.g., "US_EAST_1")
     * @param cloudProvider The cloud provider (e.g., "AWS", "GCP", "AZURE")
     * @param numShards Number of shards (1-70)
     * @return Map containing cluster creation response
     */
    public Map<String, Object> createShardedCluster(String projectId, String clusterName, 
                                                   String instanceSize, String mongoVersion, 
                                                   String region, String cloudProvider, int numShards) {
        logger.info("Creating Atlas sharded cluster '{}' with {} shards in project {}", 
                   clusterName, numShards, projectId);
        
        // Validate minimum instance size for sharded clusters
        if (!isValidShardedInstanceSize(instanceSize)) {
            throw new IllegalArgumentException(
                "Sharded clusters require instance size M30 or larger. Provided: " + instanceSize);
        }
        
        // Validate number of shards
        if (numShards < 1 || numShards > 70) {
            throw new IllegalArgumentException(
                "Number of shards must be between 1 and 70. Provided: " + numShards);
        }
        
        try {
            Map<String, Object> clusterSpec = buildShardedClusterSpec(clusterName, instanceSize, 
                                                                     mongoVersion, region, cloudProvider, numShards);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters";
            String requestBody = objectMapper.writeValueAsString(clusterSpec);
            
            logger.debug("Sharded cluster creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Sharded cluster '{}' creation initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create sharded cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create sharded cluster '" + clusterName + "'", e);
        }
    }
    
    /**
     * Update an existing Atlas cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster to update
     * @param updateSpec Map containing the fields to update
     * @return Map containing cluster update response
     */
    public Map<String, Object> modifyCluster(String projectId, String clusterName, 
                                           Map<String, Object> updateSpec) {
        logger.info("Updating cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName;
            String requestBody = objectMapper.writeValueAsString(updateSpec);
            
            logger.debug("Cluster update payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Cluster '{}' updated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to update cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update cluster '" + clusterName + "'", e);
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
            
            Map<String, Object> response;
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                response = objectMapper.readValue(responseBody, Map.class);
            } else {
                // DELETE often returns empty body - create a simple success response
                response = new HashMap<>();
                response.put("status", "DELETING");
                response.put("message", "Cluster deletion initiated successfully");
            }
            logger.info("Cluster '{}' deletion initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete cluster '" + clusterName + "'", e);
        }
    }
    
    // ========================================================================
    // Cluster Discovery and Management Utilities
    // ========================================================================
    
    /**
     * Find clusters matching a name pattern (regex) - case insensitive
     * 
     * @param projectId The Atlas project ID
     * @param namePattern Regular expression pattern to match cluster names (case-insensitive)
     * @return List of clusters matching the pattern
     */
    public List<Map<String, Object>> findClustersByPattern(String projectId, String namePattern) {
        logger.debug("Searching for clusters matching pattern: {} in project {}", namePattern, projectId);
        
        try {
            List<Map<String, Object>> allClusters = getClusters(projectId);
            
            // Make the pattern case-insensitive by adding (?i) flag if not already present
            String caseInsensitivePattern = namePattern.startsWith("(?i)") ? namePattern : "(?i)" + namePattern;
            
            List<Map<String, Object>> matches = allClusters.stream()
                .filter(cluster -> {
                    String name = (String) cluster.get("name");
                    return name != null && name.matches(caseInsensitivePattern);
                })
                .collect(Collectors.toList());
            
            logger.debug("Found {} clusters matching pattern '{}'", matches.size(), namePattern);
            return matches;
            
        } catch (Exception e) {
            logger.warn("Failed to search for clusters with pattern '{}' in project {}: {}", 
                       namePattern, projectId, e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    /**
     * Find clusters with names starting with a prefix
     * 
     * @param projectId The Atlas project ID
     * @param namePrefix Prefix to match cluster names
     * @return List of clusters with names starting with the prefix
     */
    public List<Map<String, Object>> findClustersByPrefix(String projectId, String namePrefix) {
        return findClustersByPattern(projectId, "^" + namePrefix + ".*");
    }
    
    /**
     * Find clusters containing specific tags (case-insensitive)
     * 
     * @param projectId The Atlas project ID
     * @param tagKey Tag key to search for (case-insensitive)
     * @param tagValue Tag value to search for (case-insensitive, null to match any value for the key)
     * @return List of clusters containing the specified tag
     */
    public List<Map<String, Object>> findClustersByTag(String projectId, String tagKey, String tagValue) {
        logger.debug("Searching for clusters with tag: {}={} in project {}", tagKey, tagValue, projectId);
        
        try {
            List<Map<String, Object>> allClusters = getClusters(projectId);
            
            List<Map<String, Object>> matches = allClusters.stream()
                .filter(cluster -> {
                    Object tagsObj = cluster.get("tags");
                    if (!(tagsObj instanceof List)) {
                        return false;
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tags = (List<Map<String, Object>>) tagsObj;
                    
                    for (Map<String, Object> tag : tags) {
                        String key = (String) tag.get("key");
                        String value = (String) tag.get("value");
                        
                        // Case-insensitive comparison for keys
                        if (key != null && key.equalsIgnoreCase(tagKey)) {
                            // If tagValue is null, match any value for the key
                            if (tagValue == null || (value != null && value.equalsIgnoreCase(tagValue))) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
            
            logger.debug("Found {} clusters with tag '{}={}'", matches.size(), tagKey, tagValue);
            return matches;
            
        } catch (Exception e) {
            logger.warn("Failed to search for clusters with tag '{}={}' in project {}: {}", 
                       tagKey, tagValue, projectId, e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    /**
     * Find clusters containing any of the specified tags (case-insensitive)
     * 
     * @param projectId The Atlas project ID
     * @param tags Map of tag key-value pairs to search for (case-insensitive matching)
     * @return List of clusters containing any of the specified tags
     */
    public List<Map<String, Object>> findClustersByTags(String projectId, Map<String, String> tags) {
        logger.debug("Searching for clusters with any of tags: {} in project {}", tags, projectId);
        
        try {
            List<Map<String, Object>> allClusters = getClusters(projectId);
            
            List<Map<String, Object>> matches = allClusters.stream()
                .filter(cluster -> {
                    Object tagsObj = cluster.get("tags");
                    if (!(tagsObj instanceof List)) {
                        return false;
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> clusterTags = (List<Map<String, Object>>) tagsObj;
                    
                    for (Map<String, Object> clusterTag : clusterTags) {
                        String key = (String) clusterTag.get("key");
                        String value = (String) clusterTag.get("value");
                        
                        for (Map.Entry<String, String> searchTag : tags.entrySet()) {
                            // Case-insensitive comparison for keys
                            if (key != null && searchTag.getKey().equalsIgnoreCase(key)) {
                                // If search value is null, match any value for the key
                                String searchValue = searchTag.getValue();
                                if (searchValue == null || (value != null && value.equalsIgnoreCase(searchValue))) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
            
            logger.debug("Found {} clusters with any of the specified tags", matches.size());
            return matches;
            
        } catch (Exception e) {
            logger.warn("Failed to search for clusters with tags {} in project {}: {}", 
                       tags, projectId, e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    /**
     * Find a cluster by exact name
     * 
     * @param projectId The Atlas project ID
     * @param clusterName Exact cluster name to find
     * @return Optional containing the cluster if found, empty otherwise
     */
    public Optional<Map<String, Object>> findClusterByName(String projectId, String clusterName) {
        logger.debug("Looking for cluster with exact name: {} in project {}", clusterName, projectId);
        
        try {
            // Use the existing getCluster method which is more efficient for exact matches
            Map<String, Object> cluster = getCluster(projectId, clusterName);
            return Optional.of(cluster);
        } catch (Exception e) {
            logger.debug("Cluster '{}' not found in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Check if a cluster exists and is in a specific state
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name to check
     * @param expectedState The expected state (e.g., "IDLE", "CREATING")
     * @return true if cluster exists and is in the expected state
     */
    public boolean isClusterInState(String projectId, String clusterName, String expectedState) {
        Optional<Map<String, Object>> cluster = findClusterByName(projectId, clusterName);
        
        if (cluster.isPresent()) {
            String currentState = (String) cluster.get().get("stateName");
            boolean matches = expectedState.equals(currentState);
            logger.debug("Cluster '{}' state check: current='{}', expected='{}', matches={}",
                        clusterName, currentState, expectedState, matches);
            return matches;
        }
        
        logger.debug("Cluster '{}' not found for state check", clusterName);
        return false;
    }
    
    /**
     * Check if a cluster exists and is ready for use (IDLE state)
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name to check
     * @return true if cluster exists and is in IDLE state
     */
    public boolean isClusterReady(String projectId, String clusterName) {
        return isClusterInState(projectId, clusterName, "IDLE");
    }
    
    /**
     * Find or create a cluster with the specified configuration
     * This method will look for an existing cluster first, and only create a new one if not found
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The desired cluster name
     * @param instanceSize The instance size (e.g., "M10", "M20")
     * @param mongoVersion The MongoDB version (e.g., "7.0")
     * @param region The cloud region
     * @param cloudProvider The cloud provider
     * @return Map containing cluster information (existing or newly created)
     */
    public Map<String, Object> findOrCreateCluster(String projectId, String clusterName,
                                                   String instanceSize, String mongoVersion,
                                                   String region, String cloudProvider) {
        logger.info("Finding or creating cluster '{}' in project {}", clusterName, projectId);
        
        // First, try to find existing cluster
        Optional<Map<String, Object>> existingCluster = findClusterByName(projectId, clusterName);
        
        if (existingCluster.isPresent()) {
            Map<String, Object> cluster = existingCluster.get();
            String state = (String) cluster.get("stateName");
            
            logger.info("Found existing cluster '{}' in state '{}'", clusterName, state);
            
            // Return existing cluster regardless of state - caller can decide if they want to wait
            return cluster;
        }
        
        // Create new cluster if not found
        logger.info("Cluster '{}' not found, creating new cluster", clusterName);
        return createCluster(projectId, clusterName, instanceSize, mongoVersion, region, cloudProvider);
    }
    
    /**
     * Delete clusters matching a name pattern
     * Use with caution - this will delete multiple clusters!
     * 
     * @param projectId The Atlas project ID
     * @param namePattern Regular expression pattern to match cluster names for deletion
     * @return List of cluster names that were deleted (or attempted to be deleted)
     */
    public List<String> deleteClustersByPattern(String projectId, String namePattern) {
        logger.warn("Deleting clusters matching pattern: {} in project {}", namePattern, projectId);
        
        List<Map<String, Object>> clustersToDelete = findClustersByPattern(projectId, namePattern);
        List<String> deletedClusters = new ArrayList<>();
        
        for (Map<String, Object> cluster : clustersToDelete) {
            String clusterName = (String) cluster.get("name");
            try {
                deleteCluster(projectId, clusterName);
                deletedClusters.add(clusterName);
                logger.info("Initiated deletion of cluster: {}", clusterName);
            } catch (Exception e) {
                logger.error("Failed to delete cluster '{}': {}", clusterName, e.getMessage());
                // Continue with other clusters even if one fails
            }
        }
        
        logger.info("Initiated deletion of {} clusters matching pattern '{}'", 
                   deletedClusters.size(), namePattern);
        return deletedClusters;
    }
    
    /**
     * Get cluster information in a simplified format for testing
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @return Map with essential cluster info (name, state, type, version, etc.)
     */
    public Map<String, Object> getClusterSummary(String projectId, String clusterName) {
        try {
            Map<String, Object> cluster = getCluster(projectId, clusterName);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("name", cluster.get("name"));
            summary.put("state", cluster.get("stateName"));
            summary.put("clusterType", cluster.get("clusterType"));
            summary.put("mongoVersion", cluster.get("mongoDBVersion"));
            summary.put("id", cluster.get("id"));
            
            // Extract connection info if available
            Map<String, Object> connectionStrings = (Map<String, Object>) cluster.get("connectionStrings");
            if (connectionStrings != null) {
                summary.put("connectionString", connectionStrings.get("standardSrv"));
            }
            
            logger.debug("Generated cluster summary for '{}'", clusterName);
            return summary;
            
        } catch (Exception e) {
            logger.error("Failed to get cluster summary for '{}': {}", clusterName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get cluster summary for '" + clusterName + "'", e);
        }
    }
    
    // ========================================================================
    // Sharded Cluster Helper Methods
    // ========================================================================
    
    /**
     * Validate if an instance size is valid for sharded clusters
     * Sharded clusters require M30 or larger instance sizes
     * 
     * @param instanceSize The instance size to validate
     * @return true if valid for sharded clusters
     */
    private boolean isValidShardedInstanceSize(String instanceSize) {
        if (instanceSize == null) {
            return false;
        }
        
        // Valid sharded cluster instance sizes (M30 and above)
        String[] validSizes = {"M30", "M40", "M50", "M60", "M80", "M140", "M200", "M300"};
        
        for (String validSize : validSizes) {
            if (validSize.equalsIgnoreCase(instanceSize)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Build cluster specification for sharded clusters
     * Updated to use new API format with separate replicationSpecs per shard for compatibility
     */
    private Map<String, Object> buildShardedClusterSpec(String clusterName, String instanceSize, 
                                                       String mongoVersion, String region, 
                                                       String cloudProvider, int numShards) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", clusterName);
        spec.put("mongoDBMajorVersion", mongoVersion);
        spec.put("clusterType", "SHARDED");
        
        // Build separate replicationSpec for each shard (new API format)
        // Even for symmetric clusters, each shard must be specified separately
        List<Map<String, Object>> replicationSpecs = new ArrayList<>();
        
        for (int shardIndex = 0; shardIndex < numShards; shardIndex++) {
            Map<String, Object> replicationSpec = new HashMap<>();
            replicationSpec.put("zoneName", "Zone " + (shardIndex + 1));
            
            // Configure region for this shard (same config for all shards in symmetric cluster)
            Map<String, Object> regionConfig = new HashMap<>();
            regionConfig.put("providerName", cloudProvider.toUpperCase());
            regionConfig.put("priority", 7);
            regionConfig.put("regionName", region.toUpperCase());
            
            // Configure shard nodes (same instance size for all shards in symmetric cluster)
            Map<String, Object> electableSpecs = new HashMap<>();
            electableSpecs.put("instanceSize", instanceSize);
            electableSpecs.put("nodeCount", 3); // 3 nodes per shard (standard)
            regionConfig.put("electableSpecs", electableSpecs);
            
            // Optional: Configure read-only nodes
            Map<String, Object> readOnlySpecs = new HashMap<>();
            readOnlySpecs.put("instanceSize", instanceSize);
            readOnlySpecs.put("nodeCount", 0); // No read-only nodes by default
            regionConfig.put("readOnlySpecs", readOnlySpecs);
            
            // Optional: Configure analytics nodes
            Map<String, Object> analyticsSpecs = new HashMap<>();
            analyticsSpecs.put("instanceSize", instanceSize);
            analyticsSpecs.put("nodeCount", 0); // No analytics nodes by default
            regionConfig.put("analyticsSpecs", analyticsSpecs);
            
            replicationSpec.put("regionConfigs", List.of(regionConfig));
            replicationSpecs.add(replicationSpec);
        }
        
        spec.put("replicationSpecs", replicationSpecs);
        
        // Additional sharded cluster settings
        spec.put("backupEnabled", false); // Can be enabled later
        spec.put("providerBackupEnabled", false);
        spec.put("autoScaling", buildAutoScalingSpec(false)); // Disabled by default
        
        return spec;
    }
    
    /**
     * Build auto-scaling specification
     */
    private Map<String, Object> buildAutoScalingSpec(boolean enabled) {
        Map<String, Object> autoScaling = new HashMap<>();
        
        Map<String, Object> diskGBEnabled = new HashMap<>();
        diskGBEnabled.put("enabled", enabled);
        autoScaling.put("diskGBEnabled", diskGBEnabled);
        
        Map<String, Object> compute = new HashMap<>();
        compute.put("enabled", enabled);
        compute.put("scaleDownEnabled", enabled);
        if (enabled) {
            compute.put("minInstanceSize", "M30");
            compute.put("maxInstanceSize", "M80");
        }
        autoScaling.put("compute", compute);
        
        return autoScaling;
    }
    
    /**
     * Get recommended instance sizes for sharded clusters
     * 
     * @return Array of valid instance sizes for sharded clusters
     */
    public static String[] getShardedClusterInstanceSizes() {
        return new String[]{"M30", "M40", "M50", "M60", "M80", "M140", "M200", "M300"};
    }
    
    /**
     * Get maximum number of shards allowed
     * 
     * @return Maximum number of shards (70 for Atlas)
     */
    public static int getMaxShards() {
        return 70;
    }
    
    // ========================================================================
    // Asymmetric Sharding Support (API v2025-03-12+)
    // ========================================================================
    
    /**
     * Configuration for a single shard in an asymmetric sharded cluster
     */
    public static class ShardConfig {
        private final String instanceSize;
        private final String region;
        private final String cloudProvider;
        private final int nodeCount;
        
        public ShardConfig(String instanceSize, String region, String cloudProvider) {
            this(instanceSize, region, cloudProvider, 3);
        }
        
        public ShardConfig(String instanceSize, String region, String cloudProvider, int nodeCount) {
            this.instanceSize = instanceSize;
            this.region = region;
            this.cloudProvider = cloudProvider;
            this.nodeCount = nodeCount;
        }
        
        public String getInstanceSize() { return instanceSize; }
        public String getRegion() { return region; }
        public String getCloudProvider() { return cloudProvider; }
        public int getNodeCount() { return nodeCount; }
    }
    
    /**
     * Create an asymmetric sharded cluster where each shard can have different configurations
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name for the new cluster
     * @param mongoVersion The MongoDB version (e.g., "7.0", "6.0")
     * @param shardConfigs List of shard configurations - each shard can have different instance size, region, etc.
     * @return Map containing cluster creation response
     * @throws IllegalArgumentException if shard configurations are invalid
     */
    public Map<String, Object> createAsymmetricShardedCluster(String projectId, String clusterName,
                                                             String mongoVersion, List<ShardConfig> shardConfigs) {
        logger.info("Creating asymmetric sharded cluster '{}' with {} shards in project {}", 
                   clusterName, shardConfigs.size(), projectId);
        
        // Validate shard configurations
        validateAsymmetricShardConfigs(shardConfigs);
        
        try {
            Map<String, Object> clusterSpec = buildAsymmetricShardedClusterSpec(clusterName, mongoVersion, shardConfigs);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters";
            String requestBody = objectMapper.writeValueAsString(clusterSpec);
            
            logger.debug("Asymmetric sharded cluster creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Asymmetric sharded cluster '{}' creation initiated successfully", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create asymmetric sharded cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create asymmetric sharded cluster '" + clusterName + "'", e);
        }
    }
    
    /**
     * Update an existing sharded cluster to use asymmetric configuration
     * This converts a symmetric cluster to asymmetric or modifies existing asymmetric configuration
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster to update
     * @param shardConfigs New shard configurations
     * @return Map containing cluster update response
     */
    public Map<String, Object> updateToAsymmetricSharding(String projectId, String clusterName,
                                                         List<ShardConfig> shardConfigs) {
        logger.info("Updating cluster '{}' to asymmetric sharding with {} shards in project {}", 
                   clusterName, shardConfigs.size(), projectId);
        
        validateAsymmetricShardConfigs(shardConfigs);
        
        try {
            // Get current cluster to preserve settings not related to sharding
            Map<String, Object> currentCluster = getCluster(projectId, clusterName);
            String mongoVersion = (String) currentCluster.get("mongoDBVersion");
            
            Map<String, Object> updateSpec = buildAsymmetricShardingUpdateSpec(mongoVersion, shardConfigs);
            
            return modifyCluster(projectId, clusterName, updateSpec);
            
        } catch (Exception e) {
            logger.error("Failed to update cluster '{}' to asymmetric sharding in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update cluster '" + clusterName + "' to asymmetric sharding", e);
        }
    }
    
    /**
     * Validate asymmetric shard configurations
     */
    private void validateAsymmetricShardConfigs(List<ShardConfig> shardConfigs) {
        if (shardConfigs == null || shardConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one shard configuration is required");
        }
        
        if (shardConfigs.size() > getMaxShards()) {
            throw new IllegalArgumentException(
                "Number of shards cannot exceed " + getMaxShards() + ". Provided: " + shardConfigs.size());
        }
        
        for (int i = 0; i < shardConfigs.size(); i++) {
            ShardConfig config = shardConfigs.get(i);
            
            if (!isValidShardedInstanceSize(config.getInstanceSize())) {
                throw new IllegalArgumentException(
                    "Shard " + i + " has invalid instance size for sharded clusters: " + config.getInstanceSize() + 
                    ". Must be M30 or larger.");
            }
            
            if (config.getNodeCount() < 1) {
                throw new IllegalArgumentException(
                    "Shard " + i + " must have at least 1 node. Provided: " + config.getNodeCount());
            }
        }
    }
    
    /**
     * Build cluster specification for asymmetric sharded clusters
     * Uses the new API format where each shard is specified as a separate replicationSpec
     */
    private Map<String, Object> buildAsymmetricShardedClusterSpec(String clusterName, String mongoVersion,
                                                                 List<ShardConfig> shardConfigs) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", clusterName);
        spec.put("mongoDBMajorVersion", mongoVersion);
        spec.put("clusterType", "SHARDED");
        
        // Build separate replicationSpec for each shard (new asymmetric API format)
        List<Map<String, Object>> replicationSpecs = new ArrayList<>();
        
        for (int shardIndex = 0; shardIndex < shardConfigs.size(); shardIndex++) {
            ShardConfig shardConfig = shardConfigs.get(shardIndex);
            
            Map<String, Object> replicationSpec = new HashMap<>();
            replicationSpec.put("zoneName", "Zone " + (shardIndex + 1)); // Each shard gets its own zone
            
            // Configure region for this specific shard
            Map<String, Object> regionConfig = new HashMap<>();
            regionConfig.put("providerName", shardConfig.getCloudProvider().toUpperCase());
            regionConfig.put("priority", 7);
            regionConfig.put("regionName", shardConfig.getRegion().toUpperCase());
            
            // Configure shard nodes with shard-specific instance size
            Map<String, Object> electableSpecs = new HashMap<>();
            electableSpecs.put("instanceSize", shardConfig.getInstanceSize());
            electableSpecs.put("nodeCount", shardConfig.getNodeCount());
            regionConfig.put("electableSpecs", electableSpecs);
            
            // No read-only or analytics nodes by default
            Map<String, Object> readOnlySpecs = new HashMap<>();
            readOnlySpecs.put("instanceSize", shardConfig.getInstanceSize());
            readOnlySpecs.put("nodeCount", 0);
            regionConfig.put("readOnlySpecs", readOnlySpecs);
            
            Map<String, Object> analyticsSpecs = new HashMap<>();
            analyticsSpecs.put("instanceSize", shardConfig.getInstanceSize());
            analyticsSpecs.put("nodeCount", 0);
            regionConfig.put("analyticsSpecs", analyticsSpecs);
            
            replicationSpec.put("regionConfigs", List.of(regionConfig));
            replicationSpecs.add(replicationSpec);
        }
        
        spec.put("replicationSpecs", replicationSpecs);
        
        // Additional cluster settings
        spec.put("backupEnabled", false);
        spec.put("providerBackupEnabled", false);
        spec.put("autoScaling", buildAutoScalingSpec(false));
        
        return spec;
    }
    
    /**
     * Build update specification for converting to asymmetric sharding
     */
    private Map<String, Object> buildAsymmetricShardingUpdateSpec(String mongoVersion, List<ShardConfig> shardConfigs) {
        Map<String, Object> updateSpec = new HashMap<>();
        updateSpec.put("mongoDBMajorVersion", mongoVersion);
        updateSpec.put("clusterType", "SHARDED");
        
        // Build new replicationSpecs for asymmetric configuration
        List<Map<String, Object>> replicationSpecs = new ArrayList<>();
        
        for (int shardIndex = 0; shardIndex < shardConfigs.size(); shardIndex++) {
            ShardConfig shardConfig = shardConfigs.get(shardIndex);
            
            Map<String, Object> replicationSpec = new HashMap<>();
            replicationSpec.put("zoneName", "Zone " + (shardIndex + 1));
            
            Map<String, Object> regionConfig = new HashMap<>();
            regionConfig.put("providerName", shardConfig.getCloudProvider().toUpperCase());
            regionConfig.put("priority", 7);
            regionConfig.put("regionName", shardConfig.getRegion().toUpperCase());
            
            Map<String, Object> electableSpecs = new HashMap<>();
            electableSpecs.put("instanceSize", shardConfig.getInstanceSize());
            electableSpecs.put("nodeCount", shardConfig.getNodeCount());
            regionConfig.put("electableSpecs", electableSpecs);
            
            Map<String, Object> readOnlySpecs = new HashMap<>();
            readOnlySpecs.put("instanceSize", shardConfig.getInstanceSize());
            readOnlySpecs.put("nodeCount", 0);
            regionConfig.put("readOnlySpecs", readOnlySpecs);
            
            Map<String, Object> analyticsSpecs = new HashMap<>();
            analyticsSpecs.put("instanceSize", shardConfig.getInstanceSize());
            analyticsSpecs.put("nodeCount", 0);
            regionConfig.put("analyticsSpecs", analyticsSpecs);
            
            replicationSpec.put("regionConfigs", List.of(regionConfig));
            replicationSpecs.add(replicationSpec);
        }
        
        updateSpec.put("replicationSpecs", replicationSpecs);
        
        return updateSpec;
    }
    
    /**
     * Helper method to create a simple asymmetric sharded cluster with different instance sizes per shard
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name for the new cluster
     * @param mongoVersion The MongoDB version
     * @param instanceSizes Array of instance sizes, one per shard
     * @param region The cloud region (same for all shards)
     * @param cloudProvider The cloud provider (same for all shards)
     * @return Map containing cluster creation response
     */
    public Map<String, Object> createAsymmetricShardedClusterSimple(String projectId, String clusterName,
                                                                   String mongoVersion, String[] instanceSizes,
                                                                   String region, String cloudProvider) {
        List<ShardConfig> shardConfigs = new ArrayList<>();
        for (String instanceSize : instanceSizes) {
            shardConfigs.add(new ShardConfig(instanceSize, region, cloudProvider));
        }
        
        return createAsymmetricShardedCluster(projectId, clusterName, mongoVersion, shardConfigs);
    }
}