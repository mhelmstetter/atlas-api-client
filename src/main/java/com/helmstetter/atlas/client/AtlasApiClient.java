package com.helmstetter.atlas.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the MongoDB Atlas API
 * Handles all direct API interactions
 */
public class AtlasApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasApiClient.class);
    
    // API base URLs and versions
    public static final String BASE_URL_V2 = "https://cloud.mongodb.com/api/atlas/v2";
    public static final String BASE_URL_V1 = "https://cloud.mongodb.com/api/atlas/v1.0";
    public static final String API_VERSION_V2 = "application/vnd.atlas.2025-02-19+json";
    public static final String API_VERSION_V1 = "application/json";
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    
    public AtlasApiClient(String apiPublicKey, String apiPrivateKey) {
        this.restClient = createRestClient(apiPublicKey, apiPrivateKey);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Create a REST client with authentication
     */
    private RestClient createRestClient(String apiPublicKey, String apiPrivateKey) {
        Credentials credentials = new UsernamePasswordCredentials(apiPublicKey, apiPrivateKey.toCharArray());
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, -1), credentials);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return RestClient.builder().requestFactory(factory).build();
    }
    
    /**
     * Make a GET request to the Atlas API
     */
    public String getResponseBody(String url, String apiVersion) {
        return restClient.method(HttpMethod.GET)
                .uri(url)
                .header("Accept", apiVersion)
                .retrieve()
                .body(String.class);
    }
    
    /**
     * Generic method to parse API responses into maps
     */
    private <T> T parseResponse(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON response: {}", e.getMessage());
            throw new AtlasApiException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Generic method to extract results from API responses
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> extractResults(String responseBody) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<T>) responseMap.get("results");
        } catch (JsonProcessingException e) {
            logger.error("Failed to extract results from JSON response: {}", e.getMessage());
            throw new AtlasApiException("Failed to extract results from JSON response", e);
        }
    }
    
    /**
     * Get all projects matching the specified names
     * 
     * @param includeProjectNames Set of project names to include
     * @return Map of project names to project IDs
     */
    public Map<String, String> getProjects(Set<String> includeProjectNames) {
        String url = BASE_URL_V2 + "/groups";
        String responseBody = getResponseBody(url, API_VERSION_V2);

        try {
            List<Map<String, Object>> projects = extractResults(responseBody);
            
            return projects.stream()
                    .filter(p -> includeProjectNames.contains(p.get("name")))
                    .collect(Collectors.toMap(
                            p -> (String) p.get("name"), 
                            p -> (String) p.get("id")));
        } catch (Exception e) {
            logger.error("Failed to retrieve projects: {}", e.getMessage());
            throw new AtlasApiException("Failed to retrieve projects", e);
        }
    }
    
    /**
     * Get all processes for a project
     */
    public List<Map<String, Object>> getProcesses(String projectId) {
        String url = BASE_URL_V2 + "/groups/" + projectId + "/processes";
        String responseBody = getResponseBody(url, API_VERSION_V2);
        return extractResults(responseBody);
    }
    
    /**
     * Get all clusters in a project
     */
    public List<Map<String, Object>> getClusters(String projectId) {
        String url = BASE_URL_V2 + "/groups/" + projectId + "/clusters";
        
        logger.info("Fetching clusters for project {}", projectId);
        String responseBody = getResponseBody(url, API_VERSION_V2);
        return extractResults(responseBody);
    }
    
    /**
     * Get disk partition information for a specific process
     */
    public List<Map<String, Object>> getProcessDisks(String projectId, String hostname, int port) {
        String processId = hostname + ":" + port;
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + "/disks";
        
        logger.debug("Fetching disk partitions for process {} in project {}", processId, projectId);
        String responseBody = getResponseBody(url, API_VERSION_V1);
        return extractResults(responseBody);
    }
    
    public List<Map<String, Object>> getProcessMeasurements(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, String period) {
        
        List<Map<String, Object>> allMeasurements = new ArrayList<>();
        String metricParams = formatMetricsParam(metrics);
        String processId = hostname + ":" + port;
        
        // Start with page 1
        int pageNum = 1;
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            String url = BASE_URL_V2 + "/groups/" + projectId + "/processes/" + processId
                    + "/measurements?granularity=" + granularity 
                    + "&period=" + period 
                    + "&pageNum=" + pageNum
                    + "&itemsPerPage=500"  // Adjust as needed
                    + "&" + metricParams;

            try {
                logger.debug("Calling process measurements URL (page {}): {}", pageNum, url);
                String responseBody = getResponseBody(url, API_VERSION_V2);
                Map<String, Object> responseMap = parseResponse(responseBody, Map.class);
                
                // Get measurements from this page
                List<Map<String, Object>> pageMeasurements = (List<Map<String, Object>>) responseMap.get("measurements");
                
                if (pageMeasurements != null && !pageMeasurements.isEmpty()) {
                    if (allMeasurements.isEmpty()) {
                        // First page - just add all measurements
                        allMeasurements.addAll(pageMeasurements);
                    } else {
                        // Subsequent pages - need to merge data points with matching metrics
                        mergeDataPoints(allMeasurements, pageMeasurements);
                    }
                    
                    // Check if there are more pages - look for pagination metadata
                    // This depends on the exact API response structure
                    Object totalCount = responseMap.get("totalCount");
                    Object resultsPerPage = responseMap.get("resultsPerPage");
                    
                    if (totalCount instanceof Integer && resultsPerPage instanceof Integer) {
                        int total = (Integer) totalCount;
                        int perPage = (Integer) resultsPerPage;
                        int totalPages = (int) Math.ceil((double) total / perPage);
                        
                        hasMorePages = pageNum < totalPages;
                        logger.debug("Pagination info: page {}/{}, hasMorePages: {}", 
                                pageNum, totalPages, hasMorePages);
                    } else {
                        // If we can't determine pagination info, check if the page had data
                        hasMorePages = !pageMeasurements.isEmpty();
                        logger.debug("No pagination metadata, inferring hasMorePages: {}", hasMorePages);
                    }
                } else {
                    // No measurements on this page, we're done
                    hasMorePages = false;
                    logger.debug("No data on page {}, ending pagination", pageNum);
                }
                
                // Move to next page
                pageNum++;
                
            } catch (Exception e) {
                logger.error("Failed to get measurements for {}:{} (page {}): {}", 
                        hostname, port, pageNum, e.getMessage());
                throw new AtlasApiException("Failed to get measurements", e);
            }
        }
        
        // Log the timespan of data
        if (!allMeasurements.isEmpty()) {
            for (Map<String, Object> measurement : allMeasurements) {
                List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                String metricName = (String) measurement.get("name");
                
                if (dataPoints != null && dataPoints.size() > 1) {
                    String firstTimestamp = (String) dataPoints.get(0).get("timestamp");
                    String lastTimestamp = (String) dataPoints.get(dataPoints.size() - 1).get("timestamp");
                    logger.info("Data timespan for {}:{} metric {}: {} to {} ({} points)", 
                            hostname, port, metricName, firstTimestamp, lastTimestamp, dataPoints.size());
                }
            }
        }
        
        return allMeasurements;
    }

    /**
     * Merge data points from a new page with existing measurements
     */
    private void mergeDataPoints(List<Map<String, Object>> existingMeasurements, 
                                List<Map<String, Object>> newPageMeasurements) {
        
        // Create a map of measurement name to measurement for quick lookups
        Map<String, Map<String, Object>> existingMap = new HashMap<>();
        for (Map<String, Object> measurement : existingMeasurements) {
            String name = (String) measurement.get("name");
            existingMap.put(name, measurement);
        }
        
        // Merge the new measurements into existing ones
        for (Map<String, Object> newMeasurement : newPageMeasurements) {
            String name = (String) newMeasurement.get("name");
            
            if (existingMap.containsKey(name)) {
                // Append data points to existing measurement
                List<Map<String, Object>> existingDataPoints = 
                        (List<Map<String, Object>>) existingMap.get(name).get("dataPoints");
                List<Map<String, Object>> newDataPoints = 
                        (List<Map<String, Object>>) newMeasurement.get("dataPoints");
                
                existingDataPoints.addAll(newDataPoints);
            } else {
                // This is a new measurement name, just add it to the list
                existingMeasurements.add(newMeasurement);
                existingMap.put(name, newMeasurement);
            }
        }
    }
    
    /**
     * Get disk-level measurements for a specific partition
     */
    public List<Map<String, Object>> getDiskMeasurements(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, String period) {
        
        String processId = hostname + ":" + port;
        String metricParams = formatMetricsParam(metrics);
        
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                "/disks/" + partitionName + "/measurements?granularity=" + granularity + 
                "&period=" + period + "&" + metricParams;
        
        try {
            logger.debug("Calling disk measurements URL for partition {}: {}", partitionName, url);
            String responseBody = getResponseBody(url, API_VERSION_V1);
            
            Map<String, Object> responseMap = parseResponse(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("measurements");
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new AtlasApiException("Failed to get disk measurements", e);
        }
    }
    
    /**
     * Format metrics for URL parameter
     */
    private String formatMetricsParam(List<String> metrics) {
        return metrics.stream()
                .map(m -> "m=" + m)
                .collect(Collectors.joining("&"));
    }
    
    /**
     * Get ObjectMapper for JSON parsing
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    /**
     * Custom exception for API errors
     */
    public static class AtlasApiException extends RuntimeException {
        private static final long serialVersionUID = 5970961510846497756L;

		public AtlasApiException(String message) {
            super(message);
        }
        
        public AtlasApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}