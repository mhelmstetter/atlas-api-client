package com.helmstetter.atlas.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Handles all direct API interactions with detailed request logging
 */
public class AtlasApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasApiClient.class);
    private static final Logger requestLogger = LoggerFactory.getLogger("AtlasRequestLogger");
    
    // API base URLs and versions
    public static final String BASE_URL_V2 = "https://cloud.mongodb.com/api/atlas/v2";
    public static final String BASE_URL_V1 = "https://cloud.mongodb.com/api/atlas/v1.0";
    public static final String API_VERSION_V2 = "application/vnd.atlas.2025-02-19+json";
    public static final String API_VERSION_V1 = "application/json";
    
    // Rate limiting constants
    private static final int RATE_LIMIT_MAX_REQUESTS = 100; // Maximum requests per time window
    private static final int RATE_LIMIT_WINDOW_MINUTES = 1; // Time window in minutes
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    
    // Rate limiting tracking
    private final Deque<Instant> requestTimestamps = new LinkedList<>();
    private final Map<String, Integer> projectRequestCounts = new ConcurrentHashMap<>();
    
    // Detailed request tracking
    private final Map<String, AtomicInteger> endpointCounts = new ConcurrentHashMap<>();
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private int totalRequests = 0;
    private Instant rateWindowStart = Instant.now();
    
    // Debug level for request logging (0=minimal, 1=basic, 2=detailed, 3=verbose with parameters)
    private int debugLevel = 2;
    
    public AtlasApiClient(String apiPublicKey, String apiPrivateKey) {
        this(apiPublicKey, apiPrivateKey, 2); // Default to detailed logging
    }
    
    public AtlasApiClient(String apiPublicKey, String apiPrivateKey, int debugLevel) {
        this.restClient = createRestClient(apiPublicKey, apiPrivateKey);
        this.objectMapper = new ObjectMapper();
        this.debugLevel = debugLevel;
        
        logger.info("Atlas API client initialized with debug level {} (0=minimal, 1=basic, 2=detailed, 3=verbose)", 
                debugLevel);
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
     * Set the debug level for request logging
     * 0 = Minimal logging (just rate limit info)
     * 1 = Basic request logging (URL and counts)
     * 2 = Detailed logging (endpoints, timing, parameters)
     * 3 = Verbose logging (all parameters and values)
     */
    public void setDebugLevel(int level) {
        this.debugLevel = Math.max(0, Math.min(3, level));
        logger.info("API client debug level set to {}", this.debugLevel);
    }
    
    /**
     * Make a GET request to the Atlas API with rate limiting
     * This method will enforce the rate limit and block if necessary
     */
    public String getResponseBody(String url, String apiVersion) {
        return getResponseBody(url, apiVersion, null);
    }
    
    /**
     * Make a GET request to the Atlas API with rate limiting and project tracking
     * @param url The API URL
     * @param apiVersion The API version header
     * @param projectId The project ID for tracking (can be null)
     * @return The response body
     */
    public String getResponseBody(String url, String apiVersion, String projectId) {
        // Log the request details
        logRequest(url, projectId);
        
        // Apply rate limiting
        checkRateLimit();
        
        // Track this request
        trackRequest(projectId, url);
        
        // Make the actual request
        try {
            long startTime = System.currentTimeMillis();
            String response = restClient.method(HttpMethod.GET)
                    .uri(url)
                    .header("Accept", apiVersion)
                    .retrieve()
                    .body(String.class);
            long endTime = System.currentTimeMillis();
            
            // Log the response time if debug level is high enough
            if (debugLevel >= 2) {
                requestLogger.info("Response time: {} ms for URL: {}", 
                        (endTime - startTime), url);
            }
            
            return response;
        } catch (Exception e) {
            requestLogger.error("Request failed: {} - {}", url, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Log the details of a request based on debug level
     */
    private void logRequest(String url, String projectId) {
        if (debugLevel <= 0) {
            return; // No debug logging
        }
        
        // Parse URL to get the endpoint
        String endpoint = extractEndpoint(url);
        
        // Track count for this endpoint
        AtomicInteger count = endpointCounts.computeIfAbsent(endpoint, k -> new AtomicInteger(0));
        int requestNum = count.incrementAndGet();
        
        // Get formatted time
        String time = LocalDateTime.now().format(timeFormat);
        
        if (debugLevel >= 1) {
            if (projectId != null) {
                requestLogger.info("[{}] Request #{}: {} (Project: {})", 
                        time, requestNum, endpoint, projectId);
            } else {
                requestLogger.info("[{}] Request #{}: {}", 
                        time, requestNum, endpoint);
            }
        }
        
        if (debugLevel >= 2) {
            // Log full URL
            requestLogger.info("Full URL: {}", url);
            
            // Parse and log query parameters
            Map<String, String> queryParams = parseQueryParams(url);
            if (!queryParams.isEmpty() && debugLevel >= 3) {
                requestLogger.info("Query parameters:");
                queryParams.forEach((k, v) -> requestLogger.info("  {} = {}", k, v));
            }
        }
    }
    
    /**
     * Extract the endpoint from a URL
     */
    private String extractEndpoint(String url) {
        // Remove base URL
        String endpoint = url;
        if (url.startsWith(BASE_URL_V2)) {
            endpoint = url.substring(BASE_URL_V2.length());
        } else if (url.startsWith(BASE_URL_V1)) {
            endpoint = url.substring(BASE_URL_V1.length());
        }
        
        // Remove query string
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex > 0) {
            endpoint = endpoint.substring(0, queryIndex);
        }
        
        return endpoint;
    }
    
    /**
     * Parse query parameters from a URL
     */
    private Map<String, String> parseQueryParams(String url) {
        Map<String, String> params = new HashMap<>();
        
        int queryIndex = url.indexOf('?');
        if (queryIndex <= 0) {
            return params;
        }
        
        String queryString = url.substring(queryIndex + 1);
        String[] pairs = queryString.split("&");
        
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0) {
                String key = pair.substring(0, equalsIndex);
                String value = pair.substring(equalsIndex + 1);
                params.put(key, value);
            } else {
                params.put(pair, "");
            }
        }
        
        return params;
    }
    
    /**
     * Check if we're about to exceed rate limits and wait if necessary
     */
    private synchronized void checkRateLimit() {
        Instant now = Instant.now();
        
        // Remove timestamps older than our time window
        Instant cutoff = now.minus(RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst().isBefore(cutoff)) {
            requestTimestamps.removeFirst();
        }
        
        // If we've reached the limit, wait until we can make another request
        if (requestTimestamps.size() >= RATE_LIMIT_MAX_REQUESTS) {
            try {
                Instant oldestTimestamp = requestTimestamps.peekFirst();
                Instant nextAllowedRequest = oldestTimestamp.plus(RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
                long waitTimeMs = now.until(nextAllowedRequest, ChronoUnit.MILLIS);
                
                if (waitTimeMs > 0) {
                    logger.warn("Rate limit reached ({} requests in {} minute). Waiting {} ms before next request.",
                            RATE_LIMIT_MAX_REQUESTS, RATE_LIMIT_WINDOW_MINUTES, waitTimeMs);
                    Thread.sleep(waitTimeMs + 50); // Add a small buffer for safety
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Rate limit wait interrupted", e);
            }
        }
        
        // Add current timestamp to the queue
        requestTimestamps.addLast(Instant.now());
    }
    
    /**
     * Track an API request for monitoring and debugging
     */
    private synchronized void trackRequest(String projectId, String url) {
        // Extract project ID from URL if not provided directly
        String effectiveProjectId = projectId;
        if (effectiveProjectId == null) {
            // Try to extract project ID from URL
            if (url.contains("/groups/")) {
                String[] parts = url.split("/groups/");
                if (parts.length > 1) {
                    parts = parts[1].split("/");
                    if (parts.length > 0) {
                        effectiveProjectId = parts[0];
                    }
                }
            }
        }
        
        // Increment project-specific counter if we have a project ID
        if (effectiveProjectId != null) {
            int count = projectRequestCounts.getOrDefault(effectiveProjectId, 0) + 1;
            projectRequestCounts.put(effectiveProjectId, count);
        }
        
        // Track total requests
        totalRequests++;
        
        // Log every 10 requests
        if (totalRequests % 10 == 0) {
            logRequestStats();
        }
        
        // Reset counters every hour
        if (Instant.now().isAfter(rateWindowStart.plus(1, ChronoUnit.HOURS))) {
            logger.info("Resetting rate limit counters after 1 hour");
            resetRateLimitCounters();
        }
    }
    
    /**
     * Log request statistics for monitoring
     */
    public void logRequestStats() {
        Instant now = Instant.now();
        int requestsInLastMinute = 0;
        
        // Count requests in the last minute
        for (Instant timestamp : requestTimestamps) {
            if (timestamp.isAfter(now.minus(1, ChronoUnit.MINUTES))) {
                requestsInLastMinute++;
            }
        }
        
        logger.info("API request stats: {} total requests, {} in last minute, {} project(s) accessed",
                totalRequests, requestsInLastMinute, projectRequestCounts.size());
        
        // Log per-project stats
        for (Map.Entry<String, Integer> entry : projectRequestCounts.entrySet()) {
            logger.info("  Project {}: {} requests", entry.getKey(), entry.getValue());
        }
        
        // If detailed logging is enabled, log endpoint stats
        if (debugLevel >= 2 && !endpointCounts.isEmpty()) {
            requestLogger.info("Endpoint usage statistics:");
            endpointCounts.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get())) // Sort by count descending
                .forEach(entry -> 
                    requestLogger.info("  {}: {} requests", entry.getKey(), entry.getValue().get())
                );
        }
    }
    
    /**
     * Reset rate limit counters
     */
    private synchronized void resetRateLimitCounters() {
        // Keep the recent timestamps but reset the counters
        rateWindowStart = Instant.now();
        projectRequestCounts.clear();
    }
    
    /**
     * Generate a detailed endpoint usage report
     */
    public String generateEndpointReport() {
        StringBuilder report = new StringBuilder();
        report.append("MongoDB Atlas API Endpoint Usage Report\n");
        report.append("=======================================\n\n");
        
        report.append("Total requests: ").append(totalRequests).append("\n\n");
        
        // Project usage
        report.append("Project usage:\n");
        projectRequestCounts.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())) // Sort by count descending
            .forEach(entry -> 
                report.append(String.format("  %-40s: %d requests\n", entry.getKey(), entry.getValue()))
            );
        report.append("\n");
        
        // Endpoint usage
        report.append("Endpoint usage:\n");
        endpointCounts.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get())) // Sort by count descending
            .forEach(entry -> 
                report.append(String.format("  %-40s: %d requests\n", entry.getKey(), entry.getValue().get()))
            );
        
        report.append("\nReport generated: ").append(LocalDateTime.now()).append("\n");
        
        return report.toString();
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
        String responseBody = getResponseBody(url, API_VERSION_V2, projectId);
        return extractResults(responseBody);
    }
    
    /**
     * Get all clusters in a project
     */
    public List<Map<String, Object>> getClusters(String projectId) {
        String url = BASE_URL_V2 + "/groups/" + projectId + "/clusters";
        
        logger.info("Fetching clusters for project {}", projectId);
        String responseBody = getResponseBody(url, API_VERSION_V2, projectId);
        return extractResults(responseBody);
    }
    
    /**
     * Get disk partition information for a specific process
     */
    public List<Map<String, Object>> getProcessDisks(String projectId, String hostname, int port) {
        String processId = hostname + ":" + port;
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + "/disks";
        
        logger.debug("Fetching disk partitions for process {} in project {}", processId, projectId);
        String responseBody = getResponseBody(url, API_VERSION_V1, projectId);
        return extractResults(responseBody);
    }
    
    /**
     * Get process measurements with optimized pagination
     * This version allows customizing itemsPerPage and adds improved logging
     */
    public List<Map<String, Object>> getProcessMeasurements(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, String period, int itemsPerPage) {
        
        List<Map<String, Object>> allMeasurements = new ArrayList<>();
        String metricParams = formatMetricsParam(metrics);
        String processId = hostname + ":" + port;
        
        // Start with page 1
        int pageNum = 1;
        boolean hasMorePages = true;
        
        // Log the parameter details
        logger.info("Fetching measurements for {}:{} with {} metrics, period={}, granularity={}, itemsPerPage={}",
                hostname, port, metrics.size(), period, granularity, itemsPerPage);
        
        // Track total data points retrieved
        int totalDataPoints = 0;
        
        while (hasMorePages) {
            String url = BASE_URL_V2 + "/groups/" + projectId + "/processes/" + processId
                    + "/measurements?granularity=" + granularity 
                    + "&period=" + period 
                    + "&pageNum=" + pageNum
                    + "&itemsPerPage=" + itemsPerPage
                    + "&" + metricParams;

            try {
                logger.debug("Calling process measurements URL (page {}): {}", pageNum, url);
                String responseBody = getResponseBody(url, API_VERSION_V2, projectId);
                Map<String, Object> responseMap = parseResponse(responseBody, Map.class);
                
                // Get measurements from this page
                List<Map<String, Object>> pageMeasurements = (List<Map<String, Object>>) responseMap.get("measurements");
                
                if (pageMeasurements != null && !pageMeasurements.isEmpty()) {
                    int pageDataPoints = 0;
                    
                    // Count data points on this page
                    for (Map<String, Object> measurement : pageMeasurements) {
                        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                        if (dataPoints != null) {
                            pageDataPoints += dataPoints.size();
                        }
                    }
                    totalDataPoints += pageDataPoints;
                    
                    logger.debug("Retrieved page {} with {} measurements and {} data points", 
                            pageNum, pageMeasurements.size(), pageDataPoints);
                    
                    if (allMeasurements.isEmpty()) {
                        // First page - just add all measurements
                        allMeasurements.addAll(pageMeasurements);
                    } else {
                        // Subsequent pages - need to merge data points with matching metrics
                        mergeDataPoints(allMeasurements, pageMeasurements);
                    }
                    
                    // Check if there are more pages - look for pagination metadata
                    Object totalCount = responseMap.get("totalCount");
                    Object resultsPerPage = responseMap.get("resultsPerPage");
                    
                    if (totalCount instanceof Integer && resultsPerPage instanceof Integer) {
                        int total = (Integer) totalCount;
                        int perPage = (Integer) resultsPerPage;
                        int totalPages = (perPage > 0) ? (int) Math.ceil((double) total / perPage) : 0;
                        
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
        
        // Log the total data retrieved
        logger.info("Retrieved {} measurements with {} total data points for {}:{} (took {} pages)",
                allMeasurements.size(), totalDataPoints, hostname, port, pageNum - 1);
        
        return allMeasurements;
    }
    
    /**
     * Get disk-level measurements for a specific partition with optimized pagination
     */
    public List<Map<String, Object>> getDiskMeasurements(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, String period, int itemsPerPage) {
        
        String processId = hostname + ":" + port;
        String metricParams = formatMetricsParam(metrics);
        
        // Log the parameter details
        logger.info("Fetching disk measurements for {}:{} partition {} with {} metrics, period={}, granularity={}, itemsPerPage={}",
                hostname, port, partitionName, metrics.size(), period, granularity, itemsPerPage);
        
        String url = BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                "/disks/" + partitionName + "/measurements" +
                "?granularity=" + granularity + 
                "&period=" + period + 
                "&itemsPerPage=" + itemsPerPage +
                "&" + metricParams;
        
        try {
            logger.debug("Calling disk measurements URL: {}", url);
            String responseBody = getResponseBody(url, API_VERSION_V1, projectId);
            
            Map<String, Object> responseMap = parseResponse(responseBody, Map.class);
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) responseMap.get("measurements");
            
            // Count total data points
            int totalDataPoints = 0;
            if (measurements != null) {
                for (Map<String, Object> measurement : measurements) {
                    List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                    if (dataPoints != null) {
                        totalDataPoints += dataPoints.size();
                    }
                }
            }
            
            logger.info("Retrieved {} measurements with {} total data points for {}:{} partition {}",
                    measurements != null ? measurements.size() : 0, 
                    totalDataPoints, hostname, port, partitionName);
            
            return measurements;
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new AtlasApiException("Failed to get disk measurements", e);
        }
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
                String responseBody = getResponseBody(url, API_VERSION_V2, projectId);
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
            String responseBody = getResponseBody(url, API_VERSION_V1, projectId);
            
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
     * Get API usage statistics
     * @return Map containing API usage stats
     */
    public Map<String, Object> getApiStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", totalRequests);
        
        Instant now = Instant.now();
        long requestsInLastMinute = requestTimestamps.stream()
                .filter(ts -> ts.isAfter(now.minus(1, ChronoUnit.MINUTES)))
                .count();
        
        stats.put("requestsInLastMinute", requestsInLastMinute);
        stats.put("projectStats", new HashMap<>(projectRequestCounts));
        
        // Add endpoint statistics
        Map<String, Integer> endpointStats = new HashMap<>();
        endpointCounts.forEach((endpoint, count) -> endpointStats.put(endpoint, count.get()));
        stats.put("endpointStats", endpointStats);
        
        return stats;
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