package com.mongodb.atlas.api.clients;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtlasClustersClient {

	private static final Logger logger = LoggerFactory.getLogger(AtlasClustersClient.class);

	private final AtlasApiBase apiBase;

	public AtlasClustersClient(AtlasApiBase apiBase) {
		this.apiBase = apiBase;
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
                    .filter(p -> includeProjectNames.contains(p.get("name")))
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
}