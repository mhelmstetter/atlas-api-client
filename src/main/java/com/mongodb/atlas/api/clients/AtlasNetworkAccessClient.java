package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for MongoDB Atlas Network Access API
 * Handles IP access list and network security operations
 */
public class AtlasNetworkAccessClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasNetworkAccessClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasNetworkAccessClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all IP access list entries for a project
     * 
     * @param projectId The Atlas project ID
     * @return List of IP access list entries
     */
    public List<Map<String, Object>> getIpAccessList(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList";
        logger.info("Fetching IP access list for project {}", projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific IP access list entry
     * 
     * @param projectId The Atlas project ID
     * @param entryValue The IP address or CIDR block
     * @return Map containing access list entry information
     */
    public Map<String, Object> getIpAccessListEntry(String projectId, String entryValue) {
        logger.info("Getting IP access list entry '{}' in project {}", entryValue, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList/" + entryValue;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get IP access list entry '{}' in project {}: {}", 
                        entryValue, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get IP access list entry '" + entryValue + "'", e);
        }
    }

    /**
     * Add an IP address to the access list
     *
     * @param projectId The Atlas project ID
     * @param ipAddress The IP address to allow
     * @param comment Optional comment describing the entry
     * @return Map containing creation response
     */
    public Map<String, Object> addIpAddress(String projectId, String ipAddress, String comment) {
        return addIpAddress(projectId, ipAddress, comment, null);
    }

    /**
     * Add an IP address to the access list with optional expiration
     *
     * @param projectId The Atlas project ID
     * @param ipAddress The IP address to allow
     * @param comment Optional comment describing the entry
     * @param deleteAfterDate Optional ISO 8601 date when entry should be deleted (e.g., "2024-12-31T23:59:59Z")
     * @return Map containing creation response
     */
    public Map<String, Object> addIpAddress(String projectId, String ipAddress, String comment, String deleteAfterDate) {
        logger.info("Adding IP address '{}' to access list for project {}", ipAddress, projectId);

        try {
            Map<String, Object> accessListEntry = new HashMap<>();
            accessListEntry.put("ipAddress", ipAddress);
            if (comment != null && !comment.trim().isEmpty()) {
                accessListEntry.put("comment", comment);
            }
            if (deleteAfterDate != null && !deleteAfterDate.trim().isEmpty()) {
                accessListEntry.put("deleteAfterDate", deleteAfterDate);
                logger.info("IP address will be automatically deleted after: {}", deleteAfterDate);
            }

            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList";
            String requestBody = objectMapper.writeValueAsString(List.of(accessListEntry));

            logger.info("Adding IP address: {} to project: {}", ipAddress, projectId);
            logger.info("IP access list creation payload: {}", requestBody);

            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody,
                                                       AtlasApiBase.API_VERSION_V2, projectId);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("IP address '{}' added to access list successfully", ipAddress);

            return response;

        } catch (Exception e) {
            logger.error("Failed to add IP address '{}' to access list for project {}: {}",
                        ipAddress, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to add IP address '" + ipAddress + "' to access list", e);
        }
    }

    /**
     * Add a CIDR block to the access list
     *
     * @param projectId The Atlas project ID
     * @param cidrBlock The CIDR block to allow (e.g., "192.168.1.0/24")
     * @param comment Optional comment describing the entry
     * @return Map containing creation response
     */
    public Map<String, Object> addCidrBlock(String projectId, String cidrBlock, String comment) {
        return addCidrBlock(projectId, cidrBlock, comment, null);
    }

    /**
     * Add a CIDR block to the access list with optional expiration
     *
     * @param projectId The Atlas project ID
     * @param cidrBlock The CIDR block to allow (e.g., "192.168.1.0/24")
     * @param comment Optional comment describing the entry
     * @param deleteAfterDate Optional ISO 8601 date when entry should be deleted (e.g., "2024-12-31T23:59:59Z")
     * @return Map containing creation response
     */
    public Map<String, Object> addCidrBlock(String projectId, String cidrBlock, String comment, String deleteAfterDate) {
        logger.info("Adding CIDR block '{}' to access list for project {}", cidrBlock, projectId);

        try {
            Map<String, Object> accessListEntry = new HashMap<>();
            accessListEntry.put("cidrBlock", cidrBlock);
            if (comment != null && !comment.trim().isEmpty()) {
                accessListEntry.put("comment", comment);
            }
            if (deleteAfterDate != null && !deleteAfterDate.trim().isEmpty()) {
                accessListEntry.put("deleteAfterDate", deleteAfterDate);
                logger.info("CIDR block will be automatically deleted after: {}", deleteAfterDate);
            }

            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList";
            String requestBody = objectMapper.writeValueAsString(List.of(accessListEntry));

            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody,
                                                       AtlasApiBase.API_VERSION_V2, projectId);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("CIDR block '{}' added to access list successfully", cidrBlock);

            return response;

        } catch (Exception e) {
            logger.error("Failed to add CIDR block '{}' to access list for project {}: {}",
                        cidrBlock, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to add CIDR block '" + cidrBlock + "' to access list", e);
        }
    }

    /**
     * Allow access from anywhere (0.0.0.0/0) - Use with caution!
     * 
     * @param projectId The Atlas project ID
     * @param comment Comment explaining why this is needed
     * @return Map containing creation response
     */
    public Map<String, Object> allowAccessFromAnywhere(String projectId, String comment) {
        logger.warn("Adding 0.0.0.0/0 to access list for project {} - THIS ALLOWS ACCESS FROM ANYWHERE!", projectId);
        return addCidrBlock(projectId, "0.0.0.0/0", comment);
    }

    /**
     * Delete an IP access list entry
     * 
     * @param projectId The Atlas project ID
     * @param entryValue The IP address or CIDR block to remove
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteIpAccessListEntry(String projectId, String entryValue) {
        logger.info("Deleting IP access list entry '{}' from project {}", entryValue, projectId);

        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList/" + entryValue;

            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null,
                                                       AtlasApiBase.API_VERSION_V2, projectId);

            // DELETE may return empty response
            if (responseBody == null || responseBody.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "deleted");
                response.put("entryValue", entryValue);
                logger.info("IP access list entry '{}' deleted successfully", entryValue);
                return response;
            }

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("IP access list entry '{}' deleted successfully", entryValue);

            return response;

        } catch (Exception e) {
            logger.error("Failed to delete IP access list entry '{}' from project {}: {}",
                        entryValue, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete IP access list entry '" + entryValue + "'", e);
        }
    }

    /**
     * Update an IP access list entry comment
     * 
     * @param projectId The Atlas project ID
     * @param entryValue The IP address or CIDR block
     * @param newComment The new comment
     * @return Map containing update response
     */
    public Map<String, Object> updateIpAccessListEntry(String projectId, String entryValue, String newComment) {
        logger.info("Updating IP access list entry '{}' in project {}", entryValue, projectId);

        try {
            Map<String, Object> updateSpec = new HashMap<>();
            updateSpec.put("comment", newComment);

            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/accessList/" + entryValue;
            String requestBody = objectMapper.writeValueAsString(List.of(updateSpec));

            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody,
                                                       AtlasApiBase.API_VERSION_V2, projectId);

            // PATCH may return empty response
            if (responseBody == null || responseBody.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "updated");
                response.put("entryValue", entryValue);
                response.put("comment", newComment);
                logger.info("IP access list entry '{}' updated successfully", entryValue);
                return response;
            }

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("IP access list entry '{}' updated successfully", entryValue);

            return response;

        } catch (Exception e) {
            logger.error("Failed to update IP access list entry '{}' in project {}: {}",
                        entryValue, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update IP access list entry '" + entryValue + "'", e);
        }
    }
}