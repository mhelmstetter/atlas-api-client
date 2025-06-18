package com.mongodb.atlas.api.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Atlas API client for alerts endpoints
 * Handles retrieving and acknowledging alerts from MongoDB Atlas
 */
public class AtlasAlertsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasAlertsClient.class);
    
    private final AtlasApiBase apiBase;
    
    public AtlasAlertsClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Get all alerts for a project with proper pagination
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param status Optional filter by alert status (OPEN, CLOSED)
     * @return List of alerts
     */
    public List<Map<String, Object>> getProjectAlerts(String projectId, String status) {
        List<Map<String, Object>> allAlerts = new ArrayList<>();
        int pageNum = 1;
        boolean hasMorePages = true;
        int itemsPerPage = 500; // Max items per page
        
        logger.debug("Fetching all alerts for project {} with status filter: {}", projectId, status);
        
        while (hasMorePages) {
            StringBuilder urlBuilder = new StringBuilder(AtlasApiBase.BASE_URL_V2)
                    .append("/groups/").append(projectId).append("/alerts")
                    .append("?pageNum=").append(pageNum)
                    .append("&itemsPerPage=").append(itemsPerPage);
            
            // Add status filter if provided
            if (status != null && !status.isEmpty()) {
                urlBuilder.append("&status=").append(status);
            }
            
            String url = urlBuilder.toString();
            logger.debug("Fetching alerts page {} for project {}", pageNum, projectId);
            
            try {
                String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
                Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
                
                // Extract alerts from this page
                List<Map<String, Object>> pageAlerts = (List<Map<String, Object>>) responseMap.get("results");
                if (pageAlerts != null && !pageAlerts.isEmpty()) {
                    allAlerts.addAll(pageAlerts);
                    
                    // Check pagination - Atlas API returns totalCount and resultsPerPage (or links)
                    Object totalCount = responseMap.get("totalCount");
                    
                    if (totalCount instanceof Integer) {
                        int total = (Integer) totalCount;
                        int totalPages = (itemsPerPage > 0) ? (int) Math.ceil((double) total / itemsPerPage) : 0;
                        hasMorePages = pageNum < totalPages;
                        
                        logger.debug("Alerts pagination: page {}/{}, {} total alerts", pageNum, totalPages, total);
                    } else {
                        // Fallback: check if we got a full page
                        hasMorePages = pageAlerts.size() >= itemsPerPage;
                    }
                } else {
                    hasMorePages = false;
                }
                
                pageNum++;
                
            } catch (Exception e) {
                logger.error("Failed to get alerts for project {} (page {}): {}", projectId, pageNum, e.getMessage());
                throw new AtlasApiBase.AtlasApiException("Failed to get project alerts", e);
            }
        }
        
        logger.debug("Fetched {} total alerts for project {}", allAlerts.size(), projectId);
        return allAlerts;
    }
    
    /**
     * Get all alerts for a project (without status filter)
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @return List of all alerts
     */
    public List<Map<String, Object>> getProjectAlerts(String projectId) {
        return getProjectAlerts(projectId, null);
    }
    
    /**
     * Get open alerts for a project
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @return List of open alerts
     */
    public List<Map<String, Object>> getOpenAlerts(String projectId) {
        return getProjectAlerts(projectId, "OPEN");
    }
    
    /**
     * Get closed alerts for a project
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @return List of closed alerts
     */
    public List<Map<String, Object>> getClosedAlerts(String projectId) {
        return getProjectAlerts(projectId, "CLOSED");
    }
    
    /**
     * Get a specific alert by ID
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertId Alert ID (24-character hexadecimal string)
     * @return Alert details
     */
    public Map<String, Object> getAlert(String projectId, String alertId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/alerts/" + alertId;
        
        logger.debug("Fetching alert {} for project {}", alertId, projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (Exception e) {
            logger.error("Failed to get alert {} for project {}: {}", alertId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get alert", e);
        }
    }
    
    /**
     * Get all open alerts for a specific alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @return List of open alerts for the configuration
     */
    public List<Map<String, Object>> getAlertsForConfiguration(String projectId, String alertConfigId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alertConfigs/" + alertConfigId + "/alerts";
        
        logger.debug("Fetching alerts for configuration {} in project {}", alertConfigId, projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.extractResults(responseBody);
        } catch (Exception e) {
            logger.error("Failed to get alerts for configuration {} in project {}: {}", 
                        alertConfigId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get alerts for configuration", e);
        }
    }
    
    /**
     * Acknowledge an alert
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertId Alert ID (24-character hexadecimal string)
     * @param acknowledgedUntil ISO 8601 timestamp until when the alert is acknowledged
     * @param comment Optional acknowledgment comment (max 200 characters)
     * @return Updated alert details
     */
    public Map<String, Object> acknowledgeAlert(String projectId, String alertId, 
                                               String acknowledgedUntil, String comment) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/alerts/" + alertId;
        
        // Build acknowledgment request body
        Map<String, Object> requestBody = Map.of(
            "acknowledgedUntil", acknowledgedUntil,
            "acknowledgementComment", comment != null ? comment : ""
        );
        
        logger.debug("Acknowledging alert {} for project {} until {}", alertId, projectId, acknowledgedUntil);
        
        try {
            String requestBodyJson = apiBase.getObjectMapper().writeValueAsString(requestBody);
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBodyJson, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize acknowledgment request: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to serialize acknowledgment request", e);
        } catch (Exception e) {
            logger.error("Failed to acknowledge alert {} for project {}: {}", alertId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to acknowledge alert", e);
        }
    }
    
    /**
     * Acknowledge an alert permanently (until far future date)
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertId Alert ID (24-character hexadecimal string)
     * @param comment Optional acknowledgment comment (max 200 characters)
     * @return Updated alert details
     */
    public Map<String, Object> acknowledgeAlertPermanently(String projectId, String alertId, String comment) {
        // Set acknowledgment to far future (year 2100)
        String farFuture = "2100-01-01T00:00:00.000Z";
        return acknowledgeAlert(projectId, alertId, farFuture, comment);
    }
    
    /**
     * Unacknowledge a previously acknowledged alert
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertId Alert ID (24-character hexadecimal string)
     * @return Updated alert details
     */
    public Map<String, Object> unacknowledgeAlert(String projectId, String alertId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/alerts/" + alertId;
        
        // Build unacknowledgment request body
        Map<String, Object> requestBody = Map.of("unacknowledgeAlert", true);
        
        logger.debug("Unacknowledging alert {} for project {}", alertId, projectId);
        
        try {
            String requestBodyJson = apiBase.getObjectMapper().writeValueAsString(requestBody);
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBodyJson, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize unacknowledgment request: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to serialize unacknowledgment request", e);
        } catch (Exception e) {
            logger.error("Failed to unacknowledge alert {} for project {}: {}", alertId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to unacknowledge alert", e);
        }
    }
    
    /**
     * Get alert configurations associated with a specific alert
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertId Alert ID (24-character hexadecimal string)
     * @return List of alert configurations
     */
    public List<Map<String, Object>> getAlertConfigurations(String projectId, String alertId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alerts/" + alertId + "/alertConfigs";
        
        logger.debug("Fetching alert configurations for alert {} in project {}", alertId, projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.extractResults(responseBody);
        } catch (Exception e) {
            logger.error("Failed to get alert configurations for alert {} in project {}: {}", 
                        alertId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get alert configurations", e);
        }
    }
}