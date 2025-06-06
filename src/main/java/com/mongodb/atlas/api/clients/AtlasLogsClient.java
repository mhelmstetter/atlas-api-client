package com.mongodb.atlas.api.clients;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlas API client for logs endpoints
 */
public class AtlasLogsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasLogsClient.class);
    
    private final AtlasApiBase apiBase;
    
    public AtlasLogsClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Get available log types for a process
     */
    public List<Map<String, Object>> getLogTypes(String projectId, String hostname, int port) {
        String processId = hostname + ":" + port;
        String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/clusters/" + processId + "/logs";
        
        logger.info("Fetching available log types for process {}:{}", hostname, port);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
        return apiBase.extractResults(responseBody);
    }
    
    /**
     * Get log lines for a specific log file
     */
    public String getLogsForHost(String projectId, String hostname, int port, 
                             String logName, Instant startDate, Instant endDate) {
        String processId = hostname + ":" + port;
        
        // Format dates as required by the API (usually Unix timestamp or ISO format)
        String startDateStr = String.valueOf(startDate.getEpochSecond());
        String endDateStr = String.valueOf(endDate.getEpochSecond());
        
        String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/clusters/" + processId + 
                "/logs/" + logName + 
                "?startDate=" + startDateStr + 
                "&endDate=" + endDateStr;
        
        logger.info("Fetching log lines for {}:{} log {} from {} to {}", 
                hostname, port, logName, startDate, endDate);
        
        try {
            return apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
        } catch (Exception e) {
            logger.error("Failed to get log lines for {}:{} log {}: {}", 
                    hostname, port, logName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get log lines", e);
        }
    }
    
    /**
     * Download compressed log file
     */
    public byte[] downloadLogFile(String projectId, String hostname, int port, 
                                 String logName, Instant startDate, Instant endDate) {
        // This would need special handling for binary content
        // You might need to modify the base client to support byte[] responses
        throw new UnsupportedOperationException("Binary log download not yet implemented");
    }
    

}

