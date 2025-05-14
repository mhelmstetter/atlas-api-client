package com.helmstetter.atlas.client;

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
     * Get all projects matching the specified names
     * 
     * @param includeProjectNames Set of project names to include
     * @return Map of project names to project IDs
     */
    public Map<String, String> getProjects(Set<String> includeProjectNames) {
        String url = BASE_URL_V2 + "/groups";
        String responseBody = getResponseBody(url, API_VERSION_V2);

        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
            
            return results.stream()
                    .filter(p -> includeProjectNames.contains(p.get("name")))
                    .collect(Collectors.toMap(
                            p -> (String) p.get("name"), 
                            p -> (String) p.get("id")));
        } catch (Exception e) {
            logger.error("Failed to parse projects JSON response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Get all processes for a project
     */
    public List<Map<String, Object>> getProcesses(String projectId) {
        String url = BASE_URL_V2 + "/groups/" + projectId + "/processes";
        String responseBody = getResponseBody(url, API_VERSION_V2);

        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("results");
        } catch (Exception e) {
            logger.error("Failed to parse processes JSON response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Get all clusters in a project
     */
    public List<Map<String, Object>> getClusters(String projectId) {
        String url = BASE_URL_V2 + "/groups/" + projectId + "/clusters";
        
        logger.info("Fetching clusters for project {}", projectId);
        String responseBody = getResponseBody(url, API_VERSION_V2);

        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("results");
        } catch (Exception e) {
            logger.error("Failed to retrieve clusters for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Get disk partition information for a specific process
     */
    public List<Map<String, Object>> getProcessDisks(String projectId, String hostname, int port) {
        String processId = hostname + ":" + port;
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + "/disks";
        
        logger.debug("Fetching disk partitions for process {} in project {}", processId, projectId);
        String responseBody = getResponseBody(url, API_VERSION_V1);

        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("results");
        } catch (Exception e) {
            logger.error("Failed to retrieve disk partitions for process {}: {}", processId, e.getMessage());
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Get process-level measurements (CPU, memory, etc.)
     */
    public List<Map<String, Object>> getProcessMeasurements(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, String period) {
        
        // Build metrics parameter for URL
        String metricParams = metrics.stream()
                .map(m -> "m=" + m)
                .collect(Collectors.joining("&"));
        
        String url = BASE_URL_V2 + "/groups/" + projectId + "/processes/" + hostname + ":" + port
                + "/measurements?granularity=" + granularity + "&period=" + period + "&" + metricParams;

        try {
            logger.debug("Calling process measurements URL: {}", url);
            String responseBody = getResponseBody(url, API_VERSION_V2);
    
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("measurements");
        } catch (Exception e) {
            logger.error("Failed to get measurements for {}:{}: {}", hostname, port, e.getMessage());
            throw new RuntimeException("Failed to get measurements", e);
        }
    }
    
    /**
     * Get disk-level measurements for a specific partition
     */
    public List<Map<String, Object>> getDiskMeasurements(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, String period) {
        
        String processId = hostname + ":" + port;
        
        // Build metrics parameter for URL
        String metricParams = metrics.stream()
                .map(m -> "m=" + m)
                .collect(Collectors.joining("&"));
        
        // Build the URL for disk measurements
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                "/disks/" + partitionName + "/measurements?granularity=" + granularity + 
                "&period=" + period + "&" + metricParams;
        
        try {
            logger.debug("Calling disk measurements URL for partition {}: {}", partitionName, url);
            String responseBody = getResponseBody(url, API_VERSION_V1);
            
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("measurements");
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new RuntimeException("Failed to get disk measurements", e);
        }
    }
    
    /**
     * Get ObjectMapper for JSON parsing
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}