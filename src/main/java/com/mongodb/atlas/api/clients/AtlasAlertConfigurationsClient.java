package com.mongodb.atlas.api.clients;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Atlas API client for alert configuration endpoints
 * Handles creating, updating, and managing alert configurations in MongoDB Atlas
 */
public class AtlasAlertConfigurationsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasAlertConfigurationsClient.class);
    
    private final AtlasApiBase apiBase;
    
    public AtlasAlertConfigurationsClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Get all alert configurations for a project
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @return List of alert configurations
     */
    public List<Map<String, Object>> getAlertConfigurations(String projectId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/alertConfigs";
        
        logger.debug("Fetching alert configurations for project {}", projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.extractResults(responseBody);
        } catch (Exception e) {
            logger.error("Failed to get alert configurations for project {}: {}", projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get alert configurations", e);
        }
    }
    
    /**
     * Get a specific alert configuration by ID
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @return Alert configuration details
     */
    public Map<String, Object> getAlertConfiguration(String projectId, String alertConfigId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alertConfigs/" + alertConfigId;
        
        logger.debug("Fetching alert configuration {} for project {}", alertConfigId, projectId);
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (Exception e) {
            logger.error("Failed to get alert configuration {} for project {}: {}", 
                        alertConfigId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get alert configuration", e);
        }
    }
    
    /**
     * Create a new alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfig Alert configuration object
     * @return Created alert configuration
     */
    public Map<String, Object> createAlertConfiguration(String projectId, Map<String, Object> alertConfig) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/alertConfigs";
        
        logger.debug("Creating alert configuration for project {}", projectId);
        
        try {
            String requestBodyJson = apiBase.getObjectMapper().writeValueAsString(alertConfig);
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBodyJson, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize alert configuration: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to serialize alert configuration request", e);
        } catch (Exception e) {
            logger.error("Failed to create alert configuration for project {}: {}", projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to create alert configuration", e);
        }
    }
    
    /**
     * Update an existing alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @param alertConfig Updated alert configuration object
     * @return Updated alert configuration
     */
    public Map<String, Object> updateAlertConfiguration(String projectId, String alertConfigId, 
                                                       Map<String, Object> alertConfig) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alertConfigs/" + alertConfigId;
        
        logger.debug("Updating alert configuration {} for project {}", alertConfigId, projectId);
        
        try {
            String requestBodyJson = apiBase.getObjectMapper().writeValueAsString(alertConfig);
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PUT, requestBodyJson, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize alert configuration update: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to serialize alert configuration update", e);
        } catch (Exception e) {
            logger.error("Failed to update alert configuration {} for project {}: {}", 
                        alertConfigId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to update alert configuration", e);
        }
    }
    
    /**
     * Enable an alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @return Updated alert configuration
     */
    public Map<String, Object> enableAlertConfiguration(String projectId, String alertConfigId) {
        return toggleAlertConfiguration(projectId, alertConfigId, true);
    }
    
    /**
     * Disable an alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @return Updated alert configuration
     */
    public Map<String, Object> disableAlertConfiguration(String projectId, String alertConfigId) {
        return toggleAlertConfiguration(projectId, alertConfigId, false);
    }
    
    /**
     * Toggle alert configuration enabled/disabled state
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     * @param enabled Whether to enable (true) or disable (false) the configuration
     * @return Updated alert configuration
     */
    private Map<String, Object> toggleAlertConfiguration(String projectId, String alertConfigId, boolean enabled) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alertConfigs/" + alertConfigId;
        
        Map<String, Object> toggleRequest = Map.of("enabled", enabled);
        
        logger.debug("{} alert configuration {} for project {}", 
                    enabled ? "Enabling" : "Disabling", alertConfigId, projectId);
        
        try {
            String requestBodyJson = apiBase.getObjectMapper().writeValueAsString(toggleRequest);
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBodyJson, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            return apiBase.parseResponse(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize alert configuration toggle: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to serialize alert configuration toggle", e);
        } catch (Exception e) {
            logger.error("Failed to {} alert configuration {} for project {}: {}", 
                        enabled ? "enable" : "disable", alertConfigId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to toggle alert configuration", e);
        }
    }
    
    /**
     * Delete an alert configuration
     * 
     * @param projectId Project ID (24-character hexadecimal string)
     * @param alertConfigId Alert configuration ID (24-character hexadecimal string)
     */
    public void deleteAlertConfiguration(String projectId, String alertConfigId) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId 
                   + "/alertConfigs/" + alertConfigId;
        
        logger.debug("Deleting alert configuration {} for project {}", alertConfigId, projectId);
        
        try {
            apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                 AtlasApiBase.API_VERSION_V2, projectId);
            logger.info("Successfully deleted alert configuration {} for project {}", alertConfigId, projectId);
        } catch (Exception e) {
            logger.error("Failed to delete alert configuration {} for project {}: {}", 
                        alertConfigId, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to delete alert configuration", e);
        }
    }
    
    /**
     * Get available matcher field names for alert configurations
     * 
     * @return List of field names that can be used in matchers
     */
    public List<String> getMatcherFieldNames() {
        String url = AtlasApiBase.BASE_URL_V2 + "/alertConfigs/matchers/fieldNames";
        
        logger.debug("Fetching available matcher field names");
        
        try {
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2);
            return apiBase.parseResponse(responseBody, List.class);
        } catch (Exception e) {
            logger.error("Failed to get matcher field names: {}", e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get matcher field names", e);
        }
    }
    
    /**
     * Create a basic metric alert configuration
     * Helper method to create common metric-based alert configurations
     * 
     * @param projectId Project ID
     * @param eventTypeName Event type (e.g., "OUTSIDE_METRIC_THRESHOLD")
     * @param metricName Metric name to monitor
     * @param operator Threshold operator (e.g., "GREATER_THAN", "LESS_THAN")
     * @param threshold Threshold value
     * @param notifications List of notification configurations
     * @return Created alert configuration
     */
    public Map<String, Object> createMetricAlertConfiguration(
            String projectId,
            String eventTypeName,
            String metricName,
            String operator,
            Double threshold,
            List<Map<String, Object>> notifications) {
        
        Map<String, Object> alertConfig = Map.of(
            "eventTypeName", eventTypeName,
            "enabled", true,
            "metricThreshold", Map.of(
                "metricName", metricName,
                "operator", operator,
                "threshold", threshold
            ),
            "notifications", notifications
        );
        
        return createAlertConfiguration(projectId, alertConfig);
    }
    
    /**
     * Create a host-based alert configuration
     * Helper method to create host-specific alert configurations
     * 
     * @param projectId Project ID
     * @param eventTypeName Event type name
     * @param hostname Target hostname
     * @param port Target port (optional, can be null)
     * @param notifications List of notification configurations
     * @return Created alert configuration
     */
    public Map<String, Object> createHostAlertConfiguration(
            String projectId,
            String eventTypeName,
            String hostname,
            Integer port,
            List<Map<String, Object>> notifications) {
        
        Map<String, Object> matcher = Map.of(
            "fieldName", "HOSTNAME",
            "operator", "EQUALS",
            "value", hostname
        );
        
        Map<String, Object> alertConfig = Map.of(
            "eventTypeName", eventTypeName,
            "enabled", true,
            "matchers", List.of(matcher),
            "notifications", notifications
        );
        
        // Add port matcher if specified
        if (port != null) {
            Map<String, Object> portMatcher = Map.of(
                "fieldName", "PORT",
                "operator", "EQUALS", 
                "value", port.toString()
            );
            alertConfig = Map.of(
                "eventTypeName", eventTypeName,
                "enabled", true,
                "matchers", List.of(matcher, portMatcher),
                "notifications", notifications
            );
        }
        
        return createAlertConfiguration(projectId, alertConfig);
    }
}