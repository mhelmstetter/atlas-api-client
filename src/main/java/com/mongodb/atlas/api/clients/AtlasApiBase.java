package com.mongodb.atlas.api.clients;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
 * Base Atlas API client that handles authentication, rate limiting, and common HTTP operations.
 * Specialized clients for different API categories are built on top of this.
 */
public class AtlasApiBase {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasApiBase.class);
    private static final Logger requestLogger = LoggerFactory.getLogger("AtlasRequestLogger");
    
    // API base URLs and versions
    public static final String BASE_URL_V2 = "https://cloud.mongodb.com/api/atlas/v2";
    public static final String BASE_URL_V1 = "https://cloud.mongodb.com/api/atlas/v1.0";
    public static final String API_VERSION_V2 = "application/vnd.atlas.2025-03-12+json";
    public static final String API_VERSION_V1 = "application/json";
    
    // Rate limiting constants
    private static final int RATE_LIMIT_MAX_REQUESTS = 100;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 1;
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    
    // Rate limiting tracking (shared across all specialized clients)
    private final Deque<Instant> requestTimestamps = new LinkedList<>();
    private final Map<String, Integer> projectRequestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> endpointCounts = new ConcurrentHashMap<>();
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private int totalRequests = 0;
    private Instant rateWindowStart = Instant.now();
    private int debugLevel = 0;
    
    public AtlasApiBase(String apiPublicKey, String apiPrivateKey) {
        this(apiPublicKey, apiPrivateKey, 0);
    }
    
    // Helper methods for detailed logging and data processing
    
    void logResponseDataInfo(List<Map<String, Object>> measurements, String processId, int pageNum) {
        if (measurements == null || measurements.isEmpty()) {
            logger.info("Page {} for {}: No measurements found", pageNum, processId);
            return;
        }
        
        int totalDataPoints = 0;
        Map<String, TimeRangeInfo> metricRanges = new HashMap<>();
        
        for (Map<String, Object> measurement : measurements) {
            String metricName = (String) measurement.get("name");
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            
            if (dataPoints != null && !dataPoints.isEmpty()) {
                totalDataPoints += dataPoints.size();
                
                Instant minTimestamp = null;
                Instant maxTimestamp = null;
                
                for (Map<String, Object> dataPoint : dataPoints) {
                    String timestampStr = (String) dataPoint.get("timestamp");
                    
                    if (timestampStr != null) {
                        try {
                            Instant timestamp = Instant.parse(timestampStr);
                            
                            if (minTimestamp == null || timestamp.isBefore(minTimestamp)) {
                                minTimestamp = timestamp;
                            }
                            
                            if (maxTimestamp == null || timestamp.isAfter(maxTimestamp)) {
                                maxTimestamp = timestamp;
                            }
                        } catch (Exception e) {
                            // Skip invalid timestamps
                        }
                    }
                }
                
                if (minTimestamp != null && maxTimestamp != null) {
                    TimeRangeInfo rangeInfo = new TimeRangeInfo(minTimestamp, maxTimestamp, dataPoints.size());
                    metricRanges.put(metricName, rangeInfo);
                }
            }
        }
        
        logger.info("Page {} for {}: {} total data points across {} metrics", 
                pageNum, processId, totalDataPoints, metricRanges.size());
        
        for (Map.Entry<String, TimeRangeInfo> entry : metricRanges.entrySet()) {
            TimeRangeInfo info = entry.getValue();
            
            long durationSeconds = ChronoUnit.SECONDS.between(info.minTimestamp, info.maxTimestamp);
            long expectedPoints = calculateExpectedPoints(durationSeconds, "PT1M");
            double coverage = (double) info.dataPointCount / expectedPoints * 100.0;
            
            logger.info("  Metric {}: {} to {} ({} points, covering {}%)", 
                    entry.getKey(), 
                    info.minTimestamp, 
                    info.maxTimestamp,
                    info.dataPointCount,
                    String.format("%.1f", coverage));
            
            if (coverage > 120) {
                logger.warn("  Potential data overlap for {} on page {}: coverage {}%",
                        entry.getKey(), pageNum, String.format("%.1f", coverage));
            }
        }
    }
    
    void checkAndLogTimestampOverlaps(
            List<Map<String, Object>> existingMeasurements, 
            List<Map<String, Object>> newMeasurements,
            String processId,
            int pageNum) {
        
        Map<String, TimeRangeInfo> existingRanges = new HashMap<>();
        
        for (Map<String, Object> measurement : existingMeasurements) {
            String metricName = (String) measurement.get("name");
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            
            if (dataPoints != null && !dataPoints.isEmpty()) {
                Instant minTimestamp = null;
                Instant maxTimestamp = null;
                
                for (Map<String, Object> dataPoint : dataPoints) {
                    String timestampStr = (String) dataPoint.get("timestamp");
                    
                    if (timestampStr != null) {
                        try {
                            Instant timestamp = Instant.parse(timestampStr);
                            
                            if (minTimestamp == null || timestamp.isBefore(minTimestamp)) {
                                minTimestamp = timestamp;
                            }
                            
                            if (maxTimestamp == null || timestamp.isAfter(maxTimestamp)) {
                                maxTimestamp = timestamp;
                            }
                        } catch (Exception e) {
                            // Skip invalid timestamps
                        }
                    }
                }
                
                if (minTimestamp != null && maxTimestamp != null) {
                    existingRanges.put(metricName, new TimeRangeInfo(minTimestamp, maxTimestamp, dataPoints.size()));
                }
            }
        }
        
        for (Map<String, Object> newMeasurement : newMeasurements) {
            String metricName = (String) newMeasurement.get("name");
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) newMeasurement.get("dataPoints");
            
            if (dataPoints != null && !dataPoints.isEmpty() && existingRanges.containsKey(metricName)) {
                TimeRangeInfo existingRange = existingRanges.get(metricName);
                
                Instant minTimestamp = null;
                Instant maxTimestamp = null;
                
                for (Map<String, Object> dataPoint : dataPoints) {
                    String timestampStr = (String) dataPoint.get("timestamp");
                    
                    if (timestampStr != null) {
                        try {
                            Instant timestamp = Instant.parse(timestampStr);
                            
                            if (minTimestamp == null || timestamp.isBefore(minTimestamp)) {
                                minTimestamp = timestamp;
                            }
                            
                            if (maxTimestamp == null || timestamp.isAfter(maxTimestamp)) {
                                maxTimestamp = timestamp;
                            }
                        } catch (Exception e) {
                            // Skip invalid timestamps
                        }
                    }
                }
                
                if (minTimestamp != null && maxTimestamp != null) {
                    boolean hasOverlap = !(maxTimestamp.isBefore(existingRange.minTimestamp) || 
                                          minTimestamp.isAfter(existingRange.maxTimestamp));
                    
                    if (hasOverlap) {
                        logger.warn("TIMESTAMP OVERLAP DETECTED for {} on page {}", metricName, pageNum);
                        logger.warn("  Existing range: {} to {} ({} points)", 
                                existingRange.minTimestamp, existingRange.maxTimestamp, existingRange.dataPointCount);
                        logger.warn("  New range: {} to {} ({} points)", 
                                minTimestamp, maxTimestamp, dataPoints.size());
                    }
                }
            }
        }
    }
    
    int countDataPoints(List<Map<String, Object>> measurements) {
        int count = 0;
        for (Map<String, Object> measurement : measurements) {
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            if (dataPoints != null) {
                count += dataPoints.size();
            }
        }
        return count;
    }
    
    void logTimeRangeInfo(List<Map<String, Object>> measurements, String processId) {
        java.util.Date earliest = findEarliestDate(measurements);
        java.util.Date latest = findLatestDate(measurements);
        
        if (earliest != null && latest != null) {
            logger.info("Initial data range for {}: {} to {}", 
                    processId, earliest, latest);
        }
    }
    
    private java.util.Date findEarliestDate(List<Map<String, Object>> measurements) {
        java.util.Date earliest = null;
        for (Map<String, Object> measurement : measurements) {
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            if (dataPoints != null && !dataPoints.isEmpty()) {
                for (Map<String, Object> dataPoint : dataPoints) {
                    String timestamp = (String) dataPoint.get("timestamp");
                    if (timestamp != null) {
                        try {
                            java.util.Date date = java.util.Date.from(Instant.parse(timestamp));
                            if (earliest == null || date.before(earliest)) {
                                earliest = date;
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }
        }
        return earliest;
    }

    private java.util.Date findLatestDate(List<Map<String, Object>> measurements) {
        java.util.Date latest = null;
        for (Map<String, Object> measurement : measurements) {
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            if (dataPoints != null && !dataPoints.isEmpty()) {
                for (Map<String, Object> dataPoint : dataPoints) {
                    String timestamp = (String) dataPoint.get("timestamp");
                    if (timestamp != null) {
                        try {
                            java.util.Date date = java.util.Date.from(Instant.parse(timestamp));
                            if (latest == null || date.after(latest)) {
                                latest = date;
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }
        }
        return latest;
    }
    
    private long calculateExpectedPoints(long durationSeconds, String granularity) {
        long granularitySeconds;
        
        if (granularity.equals("PT10S")) {
            granularitySeconds = 10;
        } else if (granularity.equals("PT1M") || granularity.equals("PT60S")) {
            granularitySeconds = 60;
        } else if (granularity.equals("PT5M") || granularity.equals("PT300S")) {
            granularitySeconds = 300;
        } else if (granularity.equals("PT1H") || granularity.equals("PT3600S")) {
            granularitySeconds = 3600;
        } else {
            granularitySeconds = 60; // Default to 1 minute
        }
        
        return (durationSeconds / granularitySeconds) + 1;
    }
    
    /**
     * Helper class to track time range information for metrics
     */
    private static class TimeRangeInfo {
        Instant minTimestamp;
        Instant maxTimestamp;
        int dataPointCount;
        
        public TimeRangeInfo(Instant minTimestamp, Instant maxTimestamp, int dataPointCount) {
            this.minTimestamp = minTimestamp;
            this.maxTimestamp = maxTimestamp;
            this.dataPointCount = dataPointCount;
        }
    }
    
    public AtlasApiBase(String apiPublicKey, String apiPrivateKey, int debugLevel) {
        this.restClient = createRestClient(apiPublicKey, apiPrivateKey);
        this.objectMapper = new ObjectMapper();
        this.debugLevel = debugLevel;
        
        logger.info("Atlas API base client initialized with debug level {}", debugLevel);
    }
    
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
     * Core HTTP GET method with rate limiting and logging - supports custom Accept headers
     */
    protected String getResponseBody(String url, String acceptHeader, String projectId) {
        logRequest(url, projectId);
        checkRateLimit();
        trackRequest(projectId, url);
        
        try {
            long startTime = System.currentTimeMillis();
            String response = restClient.method(HttpMethod.GET)
                    .uri(url)
                    .header("Accept", acceptHeader)
                    .retrieve()
                    .body(String.class);
            long endTime = System.currentTimeMillis();
            
            if (debugLevel >= 2) {
                requestLogger.debug("Response time: {} ms for URL: {}", (endTime - startTime), url);
            }
            
            return response;
        } catch (Exception e) {
            requestLogger.error("Request failed: {} - {}", url, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Core HTTP GET method for binary responses (e.g., gzip log data)
     */
    protected byte[] getBinaryResponseBody(String url, String acceptHeader, String projectId) {
        logRequest(url, projectId);
        checkRateLimit();
        trackRequest(projectId, url);
        
        try {
            logger.info("BINARY REQUEST: Making request to URL: {}", url);
            logger.info("BINARY REQUEST: Using Accept header: {}", acceptHeader);
            
            long startTime = System.currentTimeMillis();
            byte[] response = restClient.method(HttpMethod.GET)
                    .uri(url)
                    .header("Accept", acceptHeader)
                    .retrieve()
                    .body(byte[].class);
            long endTime = System.currentTimeMillis();
            
            if (debugLevel >= 2) {
                requestLogger.debug("BINARY RESPONSE: Success! Response time: {} ms for URL: {} ({} bytes)", 
                                 (endTime - startTime), url, response != null ? response.length : 0);
            }
            
            return response;
        } catch (Exception e) {
            requestLogger.error("BINARY REQUEST FAILED: {} - {}", url, e.getMessage());
            logger.error("BINARY REQUEST DETAILS: Accept header was: {}", acceptHeader);
            throw e;
        }
    }
    
    protected String getResponseBody(String url, String apiVersion) {
        return getResponseBody(url, apiVersion, null);
    }
    
    /**
     * Core HTTP method with support for different HTTP methods (GET, POST, PUT, etc.)
     */
    protected String makeApiRequest(String url, HttpMethod method, String requestBody, 
                                  String acceptHeader, String projectId) {
        logRequest(url, projectId);
        checkRateLimit();
        trackRequest(projectId, url);
        
        try {
            long startTime = System.currentTimeMillis();
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(url)
                    .header("Accept", acceptHeader)
                    .header("Content-Type", "application/json");
            
            String response;
            if (requestBody != null && !requestBody.isEmpty()) {
                response = request.body(requestBody).retrieve().body(String.class);
            } else {
                response = request.retrieve().body(String.class);
            }
            
            long endTime = System.currentTimeMillis();
            
            if (debugLevel >= 2) {
                requestLogger.debug("Response time: {} ms for {} {}", (endTime - startTime), method, url);
            }
            
            return response;
        } catch (Exception e) {
            requestLogger.error("Request failed: {} {} - {}", method, url, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Generic method to parse API responses
     */
    protected <T> T parseResponse(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON response: {}", e.getMessage());
            throw new AtlasApiException("Failed to parse JSON response", e);
        }
    }
    
    /**
     * Extract results array from paginated API responses
     */
    @SuppressWarnings("unchecked")
    protected <T> List<T> extractResults(String responseBody) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return (List<T>) responseMap.get("results");
        } catch (JsonProcessingException e) {
            logger.error("Failed to extract results from JSON response: {}", e.getMessage());
            throw new AtlasApiException("Failed to extract results from JSON response", e);
        }
    }
    
    /**
     * Format metrics for URL parameters
     */
    protected String formatMetricsParam(List<String> metrics) {
        return metrics.stream()
                .map(m -> "m=" + m)
                .collect(Collectors.joining("&"));
    }
    
    // All the rate limiting and logging methods from your original class...
    // (keeping the implementation exactly as you had it)
    
    private synchronized void checkRateLimit() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst().isBefore(cutoff)) {
            requestTimestamps.removeFirst();
        }
        
        if (requestTimestamps.size() >= RATE_LIMIT_MAX_REQUESTS) {
            try {
                Instant oldestTimestamp = requestTimestamps.peekFirst();
                Instant nextAllowedRequest = oldestTimestamp.plus(RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
                long waitTimeMs = now.until(nextAllowedRequest, ChronoUnit.MILLIS);
                
                if (waitTimeMs > 0) {
                    logger.warn("Rate limit reached. Waiting {} ms", waitTimeMs);
                    Thread.sleep(waitTimeMs + 50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Rate limit wait interrupted", e);
            }
        }
        
        requestTimestamps.addLast(Instant.now());
    }
    
    private void logRequest(String url, String projectId) {
        if (debugLevel <= 0) return;
        
        String endpoint = extractEndpoint(url);
        AtomicInteger count = endpointCounts.computeIfAbsent(endpoint, k -> new AtomicInteger(0));
        int requestNum = count.incrementAndGet();
        String time = LocalDateTime.now().format(timeFormat);
        
        if (debugLevel >= 1) {
            if (projectId != null) {
                requestLogger.debug("[{}] Request #{}: {} (Project: {})", time, requestNum, endpoint, projectId);
            } else {
                requestLogger.debug("[{}] Request #{}: {}", time, requestNum, endpoint);
            }
        }
        
        if (debugLevel >= 2) {
            requestLogger.debug("Full URL: {}", url);
        }
    }
    
    private String extractEndpoint(String url) {
        String endpoint = url;
        if (url.startsWith(BASE_URL_V2)) {
            endpoint = url.substring(BASE_URL_V2.length());
        } else if (url.startsWith(BASE_URL_V1)) {
            endpoint = url.substring(BASE_URL_V1.length());
        }
        
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex > 0) {
            endpoint = endpoint.substring(0, queryIndex);
        }
        
        return endpoint;
    }
    
    private synchronized void trackRequest(String projectId, String url) {
        String effectiveProjectId = projectId;
        if (effectiveProjectId == null && url.contains("/groups/")) {
            String[] parts = url.split("/groups/");
            if (parts.length > 1) {
                parts = parts[1].split("/");
                if (parts.length > 0) {
                    effectiveProjectId = parts[0];
                }
            }
        }
        
        if (effectiveProjectId != null) {
            int count = projectRequestCounts.getOrDefault(effectiveProjectId, 0) + 1;
            projectRequestCounts.put(effectiveProjectId, count);
        }
        
        totalRequests++;
        
        if (totalRequests % 10 == 0) {
            logRequestStats();
        }
    }
    
    public void logRequestStats() {
        Instant now = Instant.now();
        int requestsInLastMinute = 0;
        
        for (Instant timestamp : requestTimestamps) {
            if (timestamp.isAfter(now.minus(1, ChronoUnit.MINUTES))) {
                requestsInLastMinute++;
            }
        }
        
        logger.debug("API request stats: {} total, {} in last minute, {} projects",
                totalRequests, requestsInLastMinute, projectRequestCounts.size());
    }
    
    public void setDebugLevel(int level) {
        this.debugLevel = Math.max(0, Math.min(3, level));
        logger.info("Debug level set to {}", this.debugLevel);
    }
    
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    public Map<String, Object> getApiStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", totalRequests);
        
        Instant now = Instant.now();
        long requestsInLastMinute = requestTimestamps.stream()
                .filter(ts -> ts.isAfter(now.minus(1, ChronoUnit.MINUTES)))
                .count();
        
        stats.put("requestsInLastMinute", requestsInLastMinute);
        stats.put("projectStats", new HashMap<>(projectRequestCounts));
        
        Map<String, Integer> endpointStats = new HashMap<>();
        endpointCounts.forEach((endpoint, count) -> endpointStats.put(endpoint, count.get()));
        stats.put("endpointStats", endpointStats);
        
        return stats;
    }
    
    public static class AtlasApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AtlasApiException(String message) {
            super(message);
        }
        
        public AtlasApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}