package com.mongodb.atlas.api.clients;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlas API client for logs endpoints with binary gzip support
 */
public class AtlasLogsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasLogsClient.class);
    
    private final AtlasApiBase apiBase;
    private final AtlasApiClient client;
    
    // Atlas versioned gzip accept header
    public static final String GZIP_ACCEPT_HEADER = "application/vnd.atlas.2025-01-01+gzip";
    
    // Common log types for MongoDB clusters
    public static final String LOG_TYPE_MONGODB = "mongodb.gz";
    public static final String LOG_TYPE_MONGODB_AUDIT = "mongodb-audit-log.gz";
    public static final String LOG_TYPE_MONGOS = "mongos.gz";
    public static final String LOG_TYPE_MONGOS_AUDIT = "mongos-audit-log.gz";
    
    public AtlasLogsClient(AtlasApiBase apiBase, AtlasApiClient client) {
        this.apiBase = apiBase;
        this.client = client;
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
     * Get available log types for all processes in a cluster
     */
    public Map<String, List<Map<String, Object>>> getLogTypesForCluster(String projectId, String clusterName) {
        logger.info("Fetching log types for all processes in cluster: {}", clusterName);
        
        // Get all processes for the project
        List<Map<String, Object>> processes = client.clusters().getProcessesForCluster(projectId, clusterName);
        
        return processes.stream()
            .collect(Collectors.toMap(
                process -> (String) process.get("id"),
                process -> {
                    String[] hostPort = ((String) process.get("id")).split(":");
                    String hostname = hostPort[0];
                    int port = Integer.parseInt(hostPort[1]);
                    
                    try {
                        return getLogTypes(projectId, hostname, port);
                    } catch (Exception e) {
                        logger.warn("Failed to get log types for process {}: {}", 
                                   process.get("id"), e.getMessage());
                        return new ArrayList<>();
                    }
                }
            ));
    }
    
    /**
     * Get compressed log data for a specific host (returns raw gzip bytes)
     */
    public byte[] getCompressedLogsForHost(String projectId, String hostname, int port, 
                                         String logName, Instant startDate, Instant endDate) {
        String processId = hostname + ":" + port;
        
        // Format dates as required by the API (Unix timestamp)
        String startDateStr = String.valueOf(startDate.getEpochSecond());
        String endDateStr = String.valueOf(endDate.getEpochSecond());
        
        String url = AtlasApiBase.BASE_URL_V1 + "/groups/" + projectId + "/clusters/" + processId + 
                "/logs/" + logName + 
                "?startDate=" + startDateStr + 
                "&endDate=" + endDateStr;
        
        logger.info("Fetching compressed log data for {}:{} log {} from {} to {}", 
                hostname, port, logName, startDate, endDate);
        
        try {
            // Get binary gzip data using the special accept header
            byte[] compressedData = apiBase.getBinaryResponseBody(url, GZIP_ACCEPT_HEADER, projectId);
            logger.info("Retrieved {} bytes of compressed log data for {}:{} log {}", 
                       compressedData.length, hostname, port, logName);
            return compressedData;
        } catch (Exception e) {
            logger.error("Failed to get compressed logs for {}:{} log {}: {}", 
                    hostname, port, logName, e.getMessage());
            throw new AtlasApiBase.AtlasApiException("Failed to get compressed logs", e);
        }
    }
    
    /**
     * Get compressed logs for all processes in a cluster (raw gzip data)
     */
    public Map<String, byte[]> getCompressedLogsForCluster(String projectId, String clusterName, 
                                                          String logName, Instant startDate, Instant endDate) {
        logger.info("Fetching compressed {} logs for all processes in cluster: {}", logName, clusterName);
        
        List<Map<String, Object>> processes = client.clusters().getProcessesForCluster(projectId, clusterName);
        
        return processes.stream()
            .collect(Collectors.toMap(
                process -> (String) process.get("id"),
                process -> {
                    String[] hostPort = ((String) process.get("id")).split(":");
                    String hostname = hostPort[0];
                    int port = Integer.parseInt(hostPort[1]);
                    
                    try {
                        return getCompressedLogsForHost(projectId, hostname, port, logName, startDate, endDate);
                    } catch (Exception e) {
                        logger.warn("Failed to get compressed logs for process {}: {}", 
                                   process.get("id"), e.getMessage());
                        return new byte[0]; // Return empty array on failure
                    }
                }
            ));
    }
    
    /**
     * Download and save compressed log file (keeps original gzip format)
     */
    public Path downloadCompressedLogFileForHost(String projectId, String hostname, int port, 
                                        String logName, Instant startDate, Instant endDate, 
                                        String outputDirectory) throws IOException {
        
        byte[] compressedData = getCompressedLogsForHost(projectId, hostname, port, logName, startDate, endDate);
        
        // Create output directory if it doesn't exist
        Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);
        
        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String processId = hostname + "_" + port;
        String filename = String.format("%s_%s_%s.gz", processId, logName.replace(".gz", ""), timestamp);
        
        Path outputFile = outputDir.resolve(filename);
        
        // Write compressed data directly to file
        try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            fos.write(compressedData);
        }
        
        logger.info("Downloaded compressed log file for {}:{} to: {} ({} bytes)", 
                   hostname, port, outputFile, compressedData.length);
        return outputFile;
    }
    
    /**
     * Get available log names for a cluster (convenience method)
     */
    public List<String> getAvailableLogNamesForCluster(String projectId, String clusterName) {
        Map<String, List<Map<String, Object>>> clusterLogTypes = getLogTypesForCluster(projectId, clusterName);
        
        return clusterLogTypes.values().stream()
            .flatMap(logTypes -> logTypes.stream())
            .map(logType -> (String) logType.get("logName"))
            .distinct()
            .collect(Collectors.toList());
    }
    
}