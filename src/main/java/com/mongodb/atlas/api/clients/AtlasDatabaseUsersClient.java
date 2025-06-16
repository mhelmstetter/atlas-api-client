package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for MongoDB Atlas Database Users API
 * Handles database user management operations
 */
public class AtlasDatabaseUsersClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasDatabaseUsersClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasDatabaseUsersClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all database users for a project
     * 
     * @param projectId The Atlas project ID
     * @return List of database users
     */
    public List<Map<String, Object>> getDatabaseUsers(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/databaseUsers";
        logger.info("Fetching database users for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific database user
     * 
     * @param projectId The Atlas project ID
     * @param username The database username
     * @return Map containing user information
     */
    public Map<String, Object> getDatabaseUser(String projectId, String username) {
        logger.info("Getting database user '{}' in project {}", username, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/databaseUsers/admin/" + username;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get database user '{}' in project {}: {}", 
                        username, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get database user '" + username + "'", e);
        }
    }

    /**
     * Create a new database user
     * 
     * @param projectId The Atlas project ID
     * @param username The database username
     * @param password The database password
     * @param roles List of role assignments
     * @param scopes List of scope restrictions
     * @return Map containing creation response
     */
    public Map<String, Object> createDatabaseUser(String projectId, String username, String password,
                                                 List<Map<String, Object>> roles, 
                                                 List<Map<String, Object>> scopes) {
        logger.info("Creating database user '{}' in project {}", username, projectId);
        
        try {
            Map<String, Object> userSpec = buildDatabaseUserSpec(username, password, roles, scopes);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/databaseUsers";
            String requestBody = objectMapper.writeValueAsString(userSpec);
            
            logger.debug("Database user creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Database user '{}' created successfully", username);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create database user '{}' in project {}: {}", 
                        username, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create database user '" + username + "'", e);
        }
    }

    /**
     * Update an existing database user
     * 
     * @param projectId The Atlas project ID
     * @param username The database username
     * @param password The new password (optional, can be null)
     * @param roles List of role assignments
     * @param scopes List of scope restrictions
     * @return Map containing update response
     */
    public Map<String, Object> updateDatabaseUser(String projectId, String username, String password,
                                                 List<Map<String, Object>> roles, 
                                                 List<Map<String, Object>> scopes) {
        logger.info("Updating database user '{}' in project {}", username, projectId);
        
        try {
            Map<String, Object> userSpec = buildDatabaseUserSpec(username, password, roles, scopes);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/databaseUsers/admin/" + username;
            String requestBody = objectMapper.writeValueAsString(userSpec);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Database user '{}' updated successfully", username);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to update database user '{}' in project {}: {}", 
                        username, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update database user '" + username + "'", e);
        }
    }

    /**
     * Delete a database user
     * 
     * @param projectId The Atlas project ID
     * @param username The database username
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteDatabaseUser(String projectId, String username) {
        logger.info("Deleting database user '{}' in project {}", username, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/databaseUsers/admin/" + username;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Database user '{}' deleted successfully", username);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete database user '{}' in project {}: {}", 
                        username, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete database user '" + username + "'", e);
        }
    }

    /**
     * Create a read-only database user
     */
    public Map<String, Object> createReadOnlyUser(String projectId, String username, String password) {
        Map<String, Object> readRole = new HashMap<>();
        readRole.put("roleName", "read");
        readRole.put("databaseName", "admin");
        
        return createDatabaseUser(projectId, username, password, List.of(readRole), null);
    }

    /**
     * Create a read-write database user
     */
    public Map<String, Object> createReadWriteUser(String projectId, String username, String password) {
        Map<String, Object> readWriteRole = new HashMap<>();
        readWriteRole.put("roleName", "readWrite");
        readWriteRole.put("databaseName", "admin");
        
        return createDatabaseUser(projectId, username, password, List.of(readWriteRole), null);
    }

    /**
     * Build database user specification for Atlas API
     */
    private Map<String, Object> buildDatabaseUserSpec(String username, String password,
                                                     List<Map<String, Object>> roles, 
                                                     List<Map<String, Object>> scopes) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("username", username);
        spec.put("databaseName", "admin");
        
        if (password != null) {
            spec.put("password", password);
        }
        
        if (roles != null && !roles.isEmpty()) {
            spec.put("roles", roles);
        }
        
        if (scopes != null && !scopes.isEmpty()) {
            spec.put("scopes", scopes);
        }
        
        return spec;
    }
}