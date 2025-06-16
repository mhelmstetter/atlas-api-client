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

/**
 * Client for MongoDB Atlas Projects API
 * Handles project management operations
 */
public class AtlasProjectsClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasProjectsClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasProjectsClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all projects matching the specified names
     * 
     * @param includeProjectNames Set of project names to include (empty set returns all)
     * @return Map of project name to project ID
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
     * Get all projects for the authenticated user
     * 
     * @return List of project objects
     */
    public List<Map<String, Object>> getAllProjects() {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups";
        logger.info("Fetching all projects");
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific project by ID
     * 
     * @param projectId The Atlas project ID
     * @return Map containing project information
     */
    public Map<String, Object> getProject(String projectId) {
        logger.info("Getting project {}", projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get project {}: {}", projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get project '" + projectId + "'", e);
        }
    }

    /**
     * Get a project by name
     * 
     * @param projectName The project name
     * @return Map containing project information, or null if not found
     */
    public Map<String, Object> getProjectByName(String projectName) {
        logger.info("Getting project by name '{}'", projectName);
        
        try {
            List<Map<String, Object>> projects = getAllProjects();
            
            return projects.stream()
                    .filter(p -> projectName.equals(p.get("name")))
                    .findFirst()
                    .orElse(null);
            
        } catch (Exception e) {
            logger.error("Failed to get project by name '{}': {}", projectName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get project by name '" + projectName + "'", e);
        }
    }

    /**
     * Create a new project
     * 
     * @param projectName The name for the new project
     * @param orgId The organization ID that will own this project
     * @return Map containing creation response
     */
    public Map<String, Object> createProject(String projectName, String orgId) {
        logger.info("Creating project '{}'", projectName);
        
        try {
            Map<String, Object> projectSpec = new HashMap<>();
            projectSpec.put("name", projectName);
            projectSpec.put("orgId", orgId);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups";
            String requestBody = objectMapper.writeValueAsString(projectSpec);
            
            logger.debug("Project creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Project '{}' created successfully with ID: {}", projectName, response.get("id"));
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create project '{}': {}", projectName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create project '" + projectName + "'", e);
        }
    }

    /**
     * Delete a project
     * 
     * @param projectId The Atlas project ID
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteProject(String projectId) {
        logger.info("Deleting project {}", projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Project '{}' deleted successfully", projectId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete project {}: {}", projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete project '" + projectId + "'", e);
        }
    }

    /**
     * Get project team members
     * 
     * @param projectId The Atlas project ID
     * @return List of team members
     */
    public List<Map<String, Object>> getProjectTeams(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/teams";
        logger.info("Fetching teams for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get project users
     * 
     * @param projectId The Atlas project ID
     * @return List of project users
     */
    public List<Map<String, Object>> getProjectUsers(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/users";
        logger.info("Fetching users for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Add a user to a project
     * 
     * @param projectId The Atlas project ID
     * @param username The username to add
     * @param roles List of roles to assign to the user
     * @return Map containing response
     */
    public Map<String, Object> addUserToProject(String projectId, String username, List<String> roles) {
        logger.info("Adding user '{}' to project {}", username, projectId);
        
        try {
            Map<String, Object> userSpec = new HashMap<>();
            userSpec.put("username", username);
            userSpec.put("roles", roles);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/users";
            String requestBody = objectMapper.writeValueAsString(userSpec);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("User '{}' added to project {} successfully", username, projectId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to add user '{}' to project {}: {}", username, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to add user '" + username + "' to project", e);
        }
    }

    /**
     * Remove a user from a project
     * 
     * @param projectId The Atlas project ID
     * @param userId The user ID to remove
     * @return Map containing response
     */
    public Map<String, Object> removeUserFromProject(String projectId, String userId) {
        logger.info("Removing user '{}' from project {}", userId, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/users/" + userId;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("User '{}' removed from project {} successfully", userId, projectId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to remove user '{}' from project {}: {}", userId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to remove user '" + userId + "' from project", e);
        }
    }

    /**
     * Get project settings/configuration
     * 
     * @param projectId The Atlas project ID
     * @return Map containing project settings
     */
    public Map<String, Object> getProjectSettings(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/settings";
        logger.info("Fetching settings for project {}", projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return objectMapper.readValue(responseBody, Map.class);
        } catch (Exception e) {
            logger.error("Failed to get project settings for {}: {}", projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get project settings", e);
        }
    }
}