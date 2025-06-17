package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for MongoDB Atlas Programmatic API Keys API
 * Handles programmatic API key operations for organizations and projects
 * 
 * API Keys provide programmatic access to Atlas resources and can be scoped to:
 * - Organization level: Access to all projects within an organization
 * - Project level: Access to specific projects only
 * 
 * Key Features:
 * - Create, read, update, and delete API keys
 * - Manage API key access lists (IP whitelisting)
 * - Assign roles and permissions
 * - Manage API key expiration
 */
public class AtlasProgrammaticAPIKeysClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasProgrammaticAPIKeysClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasProgrammaticAPIKeysClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all programmatic API keys for an organization
     * 
     * @param orgId The Atlas organization ID
     * @return List of API keys
     */
    public List<Map<String, Object>> getOrganizationAPIKeys(String orgId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys";
        logger.info("Fetching API keys for organization {}", orgId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, null);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific programmatic API key by ID
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @return Map containing API key information
     */
    public Map<String, Object> getOrganizationAPIKey(String orgId, String apiKeyId) {
        logger.info("Getting API key '{}' in organization {}", apiKeyId, orgId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> apiKey = objectMapper.readValue(responseBody, Map.class);
            logger.debug("API key '{}' details retrieved", apiKeyId);
            
            return apiKey;
            
        } catch (Exception e) {
            logger.error("Failed to get API key '{}' in organization {}: {}", 
                        apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get API key '" + apiKeyId + "'", e);
        }
    }

    /**
     * Create a new programmatic API key for an organization
     * 
     * @param orgId The Atlas organization ID
     * @param description Description for the API key
     * @param roles List of roles to assign to the API key
     * @return Map containing API key creation response (includes private key - save immediately!)
     */
    public Map<String, Object> createOrganizationAPIKey(String orgId, String description, List<String> roles) {
        logger.info("Creating API key '{}' in organization {}", description, orgId);
        
        try {
            Map<String, Object> apiKeySpec = buildAPIKeySpec(description, roles);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys";
            String requestBody = objectMapper.writeValueAsString(apiKeySpec);
            
            logger.debug("API key creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("API key '{}' created successfully", description);
            logger.warn("IMPORTANT: Save the private key from this response - it cannot be retrieved again!");
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create API key '{}' in organization {}: {}", 
                        description, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create API key '" + description + "'", e);
        }
    }

    /**
     * Update an existing programmatic API key
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @param description New description for the API key
     * @param roles New list of roles to assign to the API key
     * @return Map containing update response
     */
    public Map<String, Object> updateOrganizationAPIKey(String orgId, String apiKeyId, 
                                                       String description, List<String> roles) {
        logger.info("Updating API key '{}' in organization {}", apiKeyId, orgId);
        
        try {
            Map<String, Object> updateSpec = buildAPIKeySpec(description, roles);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId;
            String requestBody = objectMapper.writeValueAsString(updateSpec);
            
            logger.debug("API key update payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("API key '{}' updated successfully", apiKeyId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to update API key '{}' in organization {}: {}", 
                        apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update API key '" + apiKeyId + "'", e);
        }
    }

    /**
     * Delete a programmatic API key
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID to delete
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteOrganizationAPIKey(String orgId, String apiKeyId) {
        logger.info("Deleting API key '{}' in organization {}", apiKeyId, orgId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("API key '{}' deleted successfully", apiKeyId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete API key '{}' in organization {}: {}", 
                        apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete API key '" + apiKeyId + "'", e);
        }
    }

    /**
     * Get API key access list (IP whitelist)
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @return List of access list entries
     */
    public List<Map<String, Object>> getAPIKeyAccessList(String orgId, String apiKeyId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId + "/accessList";
        logger.info("Fetching access list for API key '{}' in organization {}", apiKeyId, orgId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, null);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Create API key access list entries (IP whitelist)
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @param accessListEntries List of IP addresses or CIDR blocks to whitelist
     * @return Map containing creation response
     */
    public Map<String, Object> createAPIKeyAccessList(String orgId, String apiKeyId, 
                                                     List<String> accessListEntries) {
        logger.info("Creating access list entries for API key '{}' in organization {}", apiKeyId, orgId);
        
        try {
            List<Map<String, Object>> entries = new java.util.ArrayList<>();
            for (String entry : accessListEntries) {
                Map<String, Object> accessEntry = new HashMap<>();
                if (entry.contains("/")) {
                    accessEntry.put("cidrBlock", entry);
                } else {
                    accessEntry.put("ipAddress", entry);
                }
                entries.add(accessEntry);
            }
            
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId + "/accessList";
            String requestBody = objectMapper.writeValueAsString(entries);
            
            logger.debug("Access list creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Access list entries created for API key '{}'", apiKeyId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create access list for API key '{}' in organization {}: {}", 
                        apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create access list for API key '" + apiKeyId + "'", e);
        }
    }

    /**
     * Delete API key access list entry
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @param accessListEntry The IP address or CIDR block to remove
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteAPIKeyAccessListEntry(String orgId, String apiKeyId, String accessListEntry) {
        logger.info("Deleting access list entry '{}' for API key '{}' in organization {}", 
                   accessListEntry, apiKeyId, orgId);
        
        try {
            String encodedEntry = java.net.URLEncoder.encode(accessListEntry, "UTF-8");
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId + 
                        "/accessList/" + encodedEntry;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Access list entry '{}' deleted for API key '{}'", accessListEntry, apiKeyId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete access list entry '{}' for API key '{}' in organization {}: {}", 
                        accessListEntry, apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete access list entry '" + accessListEntry + "'", e);
        }
    }

    /**
     * Assign API key to projects
     * 
     * @param orgId The Atlas organization ID
     * @param apiKeyId The API key ID
     * @param projectAssignments List of project assignments with roles
     * @return Map containing assignment response
     */
    public Map<String, Object> assignAPIKeyToProjects(String orgId, String apiKeyId, 
                                                     List<Map<String, Object>> projectAssignments) {
        logger.info("Assigning API key '{}' to projects in organization {}", apiKeyId, orgId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/orgs/" + orgId + "/apiKeys/" + apiKeyId + "/assignedTasks";
            String requestBody = objectMapper.writeValueAsString(projectAssignments);
            
            logger.debug("Project assignment payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, null);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("API key '{}' assigned to projects successfully", apiKeyId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to assign API key '{}' to projects in organization {}: {}", 
                        apiKeyId, orgId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to assign API key '" + apiKeyId + "' to projects", e);
        }
    }

    /**
     * Build API key specification for creation/update
     */
    private Map<String, Object> buildAPIKeySpec(String description, List<String> roles) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("desc", description);
        spec.put("roles", roles);
        return spec;
    }

    /**
     * Helper method to create a project assignment
     * 
     * @param projectId The project ID
     * @param roles List of roles for this project
     * @return Map representing a project assignment
     */
    public static Map<String, Object> createProjectAssignment(String projectId, List<String> roles) {
        Map<String, Object> assignment = new HashMap<>();
        assignment.put("groupId", projectId);
        assignment.put("roles", roles);
        return assignment;
    }

    /**
     * Get common organization-level roles
     */
    public static class OrganizationRoles {
        public static final String ORG_OWNER = "ORG_OWNER";
        public static final String ORG_MEMBER = "ORG_MEMBER";
        public static final String ORG_GROUP_CREATOR = "ORG_GROUP_CREATOR";
        public static final String ORG_BILLING_ADMIN = "ORG_BILLING_ADMIN";
        public static final String ORG_READ_ONLY = "ORG_READ_ONLY";
    }

    /**
     * Get common project-level roles
     */
    public static class ProjectRoles {
        public static final String GROUP_OWNER = "GROUP_OWNER";
        public static final String GROUP_CLUSTER_MANAGER = "GROUP_CLUSTER_MANAGER";
        public static final String GROUP_DATA_ACCESS_ADMIN = "GROUP_DATA_ACCESS_ADMIN";
        public static final String GROUP_DATA_ACCESS_READ_ONLY = "GROUP_DATA_ACCESS_READ_ONLY";
        public static final String GROUP_DATA_ACCESS_READ_WRITE = "GROUP_DATA_ACCESS_READ_WRITE";
        public static final String GROUP_MONITORING_ADMIN = "GROUP_MONITORING_ADMIN";
        public static final String GROUP_READ_ONLY = "GROUP_READ_ONLY";
    }
}