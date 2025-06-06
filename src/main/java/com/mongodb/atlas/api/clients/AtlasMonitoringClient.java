package com.mongodb.atlas.api.clients;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlas API client for monitoring and metrics endpoints
 */
public class AtlasMonitoringClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasMonitoringClient.class);
    
    private final AtlasApiBase apiBase;
    
    public AtlasMonitoringClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Get disk partition information for a specific process
     */
    public List<Map<String, Object>> getProcessDisks(String projectId, String hostname, int port) {
        String processId = hostname + ":" + port;
        String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + "/disks";
        
        logger.debug("Fetching disk partitions for process {} in project {}", processId, projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
        return apiBase.extractResults(responseBody);
    }
    
    /**
     * Get process measurements with pagination support
     */
    public List<Map<String, Object>> getProcessMeasurements(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, String period) {
        
        List<Map<String, Object>> allMeasurements = new ArrayList<>();
        String metricParams = apiBase.formatMetricsParam(metrics);
        String processId = hostname + ":" + port;
        
        int pageNum = 1;
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/processes/" + processId
                    + "/measurements?granularity=" + granularity 
                    + "&period=" + period 
                    + "&pageNum=" + pageNum
                    + "&itemsPerPage=500"
                    + "&" + metricParams;

            try {
                String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
                Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
                
                List<Map<String, Object>> pageMeasurements = 
                    (List<Map<String, Object>>) responseMap.get("measurements");
                
                if (pageMeasurements != null && !pageMeasurements.isEmpty()) {
                    if (allMeasurements.isEmpty()) {
                        allMeasurements.addAll(pageMeasurements);
                    } else {
                        mergeDataPoints(allMeasurements, pageMeasurements);
                    }
                    
                    // Check pagination
                    Object totalCount = responseMap.get("totalCount");
                    Object resultsPerPage = responseMap.get("resultsPerPage");

                    if (totalCount instanceof Integer && resultsPerPage instanceof Integer) {
                        int total = (Integer) totalCount;
                        int perPage = (Integer) resultsPerPage;
                        int totalPages = (perPage > 0) ? (int) Math.ceil((double) total / perPage) : 0;
                        hasMorePages = pageNum < totalPages;
                    } else {
                        hasMorePages = false;
                    }
                } else {
                    hasMorePages = false;
                }
                
                pageNum++;
                
            } catch (Exception e) {
                logger.error("Failed to get measurements for {}:{} (page {}): {}", 
                        hostname, port, pageNum, e.getMessage());
                throw new AtlasApiBase.AtlasApiException("Failed to get measurements", e);
            }
        }
        
        return allMeasurements;
    }
    
    /**
     * Get process measurements using explicit start and end timestamps
     */
    public List<Map<String, Object>> getProcessMeasurementsWithTimeRange(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, String period) {
        
        int pageSize = 500;
        List<Map<String, Object>> allMeasurements = new ArrayList<>();
        String metricParams = apiBase.formatMetricsParam(metrics);
        String processId = hostname + ":" + port;
        
        int pageNum = 1;
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/processes/" + processId
                    + "/measurements?granularity=" + granularity 
                    + "&period=" + period
                    + "&pageNum=" + pageNum
                    + "&itemsPerPage=" + pageSize
                    + "&" + metricParams;

            try {
                String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
                Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
                
                List<Map<String, Object>> pageMeasurements = 
                    (List<Map<String, Object>>) responseMap.get("measurements");
                
                if (pageMeasurements != null && !pageMeasurements.isEmpty()) {
                    apiBase.logResponseDataInfo(pageMeasurements, processId, pageNum);
                    
                    if (allMeasurements.isEmpty()) {
                        allMeasurements.addAll(pageMeasurements);
                    } else {
                    	apiBase.checkAndLogTimestampOverlaps(allMeasurements, pageMeasurements, processId, pageNum);
                        mergeDataPoints(allMeasurements, pageMeasurements);
                    }
                    
                    int pageDataPoints = apiBase.countDataPoints(pageMeasurements);
                    
                    logger.debug("Page {} processed: {} data points", pageNum, pageDataPoints);
                    
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
                        hasMorePages = false;
                    }
                } else {
                    hasMorePages = false;
                }
                
                pageNum++;
                
            } catch (Exception e) {
                logger.error("Failed to get measurements for {}:{} (page {}): {}", 
                        hostname, port, pageNum, e.getMessage());
                throw new AtlasApiBase.AtlasApiException("Failed to get measurements", e);
            }
        }
        
        return allMeasurements;
    }
    
    /**
     * Get process measurements using explicit start and end timestamps
     */
    public List<Map<String, Object>> getProcessMeasurementsWithExplicitTimeRange(
            String projectId, String hostname, int port, 
            List<String> metrics, String granularity, 
            Instant startTime, Instant endTime) {
        
        List<Map<String, Object>> allMeasurements = new ArrayList<>();
        String metricParams = apiBase.formatMetricsParam(metrics);
        String processId = hostname + ":" + port;
        
        String endTimeStr = endTime.toString();
        String startTimeStr = startTime.toString();
        
        logger.info("Fetching measurements for {}:{} from {} to {}", 
                hostname, port, startTimeStr, endTimeStr);
        
        int pageNum = 1;
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/processes/" + processId
                    + "/measurements?granularity=" + granularity 
                    + "&start=" + startTimeStr
                    + "&end=" + endTimeStr
                    + "&pageNum=" + pageNum
                    + "&itemsPerPage=500"
                    + "&" + metricParams;

            try {
                String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
                Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
                
                List<Map<String, Object>> pageMeasurements = 
                    (List<Map<String, Object>>) responseMap.get("measurements");
                
                if (pageMeasurements != null && !pageMeasurements.isEmpty()) {
                    if (allMeasurements.isEmpty()) {
                        allMeasurements.addAll(pageMeasurements);
                        apiBase.logTimeRangeInfo(pageMeasurements, processId);
                    } else {
                        mergeDataPoints(allMeasurements, pageMeasurements);
                    }
                    
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
                        hasMorePages = false;
                    }
                } else {
                    hasMorePages = false;
                    logger.debug("No data on page {}, ending pagination", pageNum);
                }
                
                pageNum++;
                
            } catch (Exception e) {
                logger.error("Failed to get measurements for {}:{} (page {}): {}", 
                        hostname, port, pageNum, e.getMessage());
                throw new AtlasApiBase.AtlasApiException("Failed to get measurements", e);
            }
        }
        
        // Log final data timespan
        if (!allMeasurements.isEmpty()) {
            for (Map<String, Object> measurement : allMeasurements) {
                List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                String metricName = (String) measurement.get("name");
                
                if (dataPoints != null && dataPoints.size() > 1) {
                    String firstTimestamp = (String) dataPoints.get(0).get("timestamp");
                    String lastTimestamp = (String) dataPoints.get(dataPoints.size() - 1).get("timestamp");
                    
                    logger.info("Final data timespan for {}:{} metric {}: {} to {} ({} points)", 
                            hostname, port, metricName, firstTimestamp, lastTimestamp, dataPoints.size());
                }
            }
        }
        
        return allMeasurements;
    }
    
    /**
     * Get disk measurements for a specific partition
     */
    public List<Map<String, Object>> getDiskMeasurements(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, String period) {
        
        String processId = hostname + ":" + port;
        String metricParams = apiBase.formatMetricsParam(metrics);
        
        String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                "/disks/" + partitionName + "/measurements?granularity=" + granularity + 
                "&period=" + period + "&" + metricParams;
        
        try {
            logger.debug("Fetching disk measurements for partition {}: {}", partitionName, url);
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
            
            Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
            return (List<Map<String, Object>>) responseMap.get("measurements");
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get disk measurements", e);
        }
    }
    
    /**
     * Get disk measurements using explicit start and end timestamps (with days parameter)
     */
    public List<Map<String, Object>> getDiskMeasurementsWithTimeRange(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, int periodDays) {
        
        String processId = hostname + ":" + port;
        String metricParams = apiBase.formatMetricsParam(metrics);
        
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(periodDays, ChronoUnit.DAYS);
        
        String endTimeStr = endTime.toString();
        String startTimeStr = startTime.toString();
        
        logger.info("Fetching disk measurements for {}:{} partition {} from {} to {}", 
                hostname, port, partitionName, startTimeStr, endTimeStr);
        
        try {
            String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                    "/disks/" + partitionName + "/measurements" +
                    "?granularity=" + granularity + 
                    "&start=" + startTimeStr +
                    "&end=" + endTimeStr +
                    "&" + metricParams;
            
            logger.debug("Calling disk measurements URL with timerange: {}", url);
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
            
            Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) responseMap.get("measurements");
            
            // Log data range information
            if (measurements != null && !measurements.isEmpty()) {
                int totalDataPoints = 0;
                for (Map<String, Object> measurement : measurements) {
                    List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                    if (dataPoints != null) {
                        totalDataPoints += dataPoints.size();
                        
                        if (!dataPoints.isEmpty()) {
                            String firstTimestamp = (String) dataPoints.get(0).get("timestamp");
                            String lastTimestamp = (String) dataPoints.get(dataPoints.size() - 1).get("timestamp");
                            
                            logger.info("Retrieved data for {}:{} partition {} metric {}: {} to {} ({} points)", 
                                    hostname, port, partitionName, 
                                    measurement.get("name"), firstTimestamp, lastTimestamp, 
                                    dataPoints.size());
                        }
                    }
                }
                
                logger.info("Total disk data points for {}:{} partition {}: {}", 
                        hostname, port, partitionName, totalDataPoints);
            } else {
                logger.warn("No disk measurements returned for {}:{} partition {}", 
                        hostname, port, partitionName);
            }
            
            return measurements;
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get disk measurements", e);
        }
    }
    
    /**
     * Get disk measurements using period string
     */
    public List<Map<String, Object>> getDiskMeasurementsWithTimeRange(
            String projectId, String hostname, int port, String partitionName,
            List<String> metrics, String granularity, String period) {
        
        String processId = hostname + ":" + port;
        String metricParams = apiBase.formatMetricsParam(metrics);
        
        logger.info("Fetching disk measurements for {}:{} partition {} for period {}", 
                hostname, port, partitionName, period);
        
        try {
            String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/processes/" + processId + 
                    "/disks/" + partitionName + "/measurements" +
                    "?granularity=" + granularity + 
                    "&period=" + period +
                    "&" + metricParams;
            
            logger.debug("Calling disk measurements URL with timerange: {}", url);
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V1, projectId);
            
            Map<String, Object> responseMap = apiBase.parseResponse(responseBody, Map.class);
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) responseMap.get("measurements");
            
            // Log data range information
            if (measurements != null && !measurements.isEmpty()) {
                int totalDataPoints = 0;
                for (Map<String, Object> measurement : measurements) {
                    List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
                    if (dataPoints != null) {
                        totalDataPoints += dataPoints.size();
                        
                        if (!dataPoints.isEmpty()) {
                            String firstTimestamp = (String) dataPoints.get(0).get("timestamp");
                            String lastTimestamp = (String) dataPoints.get(dataPoints.size() - 1).get("timestamp");
                            
                            logger.info("Retrieved data for {}:{} partition {} metric {}: {} to {} ({} points)", 
                                    hostname, port, partitionName, 
                                    measurement.get("name"), firstTimestamp, lastTimestamp, 
                                    dataPoints.size());
                        }
                    }
                }
                
                logger.info("Total disk data points for {}:{} partition {}: {}", 
                        hostname, port, partitionName, totalDataPoints);
            } else {
                logger.warn("No disk measurements returned for {}:{} partition {}", 
                        hostname, port, partitionName);
            }
            
            return measurements;
        } catch (Exception e) {
            logger.error("Error getting disk measurements for {}:{} partition {}: {}", 
                    hostname, port, partitionName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get disk measurements", e);
        }
    }
    
    private void mergeDataPoints(List<Map<String, Object>> existingMeasurements, 
                                List<Map<String, Object>> newPageMeasurements) {
        Map<String, Map<String, Object>> existingMap = new HashMap<>();
        for (Map<String, Object> measurement : existingMeasurements) {
            String name = (String) measurement.get("name");
            existingMap.put(name, measurement);
        }
        
        for (Map<String, Object> newMeasurement : newPageMeasurements) {
            String name = (String) newMeasurement.get("name");
            
            if (existingMap.containsKey(name)) {
                List<Map<String, Object>> existingDataPoints = 
                        (List<Map<String, Object>>) existingMap.get(name).get("dataPoints");
                List<Map<String, Object>> newDataPoints = 
                        (List<Map<String, Object>>) newMeasurement.get("dataPoints");
                
                existingDataPoints.addAll(newDataPoints);
            } else {
                existingMeasurements.add(newMeasurement);
                existingMap.put(name, newMeasurement);
            }
        }
    }
}