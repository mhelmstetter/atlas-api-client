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

}
