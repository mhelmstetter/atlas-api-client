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
import java.util.Arrays;
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
    
    // Atlas gzip accept header - using v2 API format as per CLI
    public static final String GZIP_ACCEPT_HEADER = "application/vnd.atlas.2023-02-01+gzip";
    
    // Common log types for MongoDB clusters
    public static final String LOG_TYPE_MONGODB = "mongodb.gz";
    public static final String LOG_TYPE_MONGODB_AUDIT = "mongodb-audit-log.gz";
    public static final String LOG_TYPE_MONGOS = "mongos.gz";
    public static final String LOG_TYPE_MONGOS_AUDIT = "mongos-audit-log.gz";
    
    // Log types to download by default (excluding audit logs)
    public static final List<String> DEFAULT_LOG_TYPES = Arrays.asList(LOG_TYPE_MONGODB, LOG_TYPE_MONGOS);
    
    public AtlasLogsClient(AtlasApiBase apiBase, AtlasApiClient client) {
        this.apiBase = apiBase;
        this.client = client;
    }
    
    /**
     * Get available log types for a process
     */
    public List<Map<String, Object>> getLogTypes(String projectId, String hostname, int port) {
        // Use v2 API endpoint as confirmed by Atlas CLI
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + hostname + "/logs";
        
        logger.info("Fetching available log types for hostname: {} (port: {})", hostname, port);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
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
        
        // Build URL using only hostname (not hostname:port) as per Atlas API specification
        String baseUrl = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + hostname + "/logs/" + logName;
        
        // Build query parameters conditionally like Python code
        StringBuilder url = new StringBuilder(baseUrl);
        
        if (startDate != null && endDate != null) {
            // Both dates provided
            String startDateStr = String.valueOf(startDate.getEpochSecond());
            String endDateStr = String.valueOf(endDate.getEpochSecond());
            url.append("?endDate=").append(endDateStr).append("&startDate=").append(startDateStr);
        } else if (startDate != null) {
            // Only start date provided
            String startDateStr = String.valueOf(startDate.getEpochSecond());
            url.append("?startDate=").append(startDateStr);
        } else if (endDate != null) {
            // Only end date provided
            String endDateStr = String.valueOf(endDate.getEpochSecond());
            url.append("?endDate=").append(endDateStr);
        }
        // If neither date is provided, use API defaults (no query params)
        
        String finalUrl = url.toString();
        logger.info("Fetching compressed log data from URL: {}", finalUrl);
        logger.info("Log details - Host: {}, Port: {}, Log: {}, Start: {}, End: {}", 
                hostname, port, logName, startDate, endDate);
        
        try {
            // Get binary gzip data using the special accept header
            byte[] compressedData = apiBase.getBinaryResponseBody(finalUrl, GZIP_ACCEPT_HEADER, projectId);
            logger.info("Retrieved {} bytes of compressed log data for {}:{} log {}", 
                       compressedData.length, hostname, port, logName);
            return compressedData;
        } catch (Exception e) {
            logger.error("Failed to get compressed logs for {}:{} log {} from URL {}: {}", 
                    hostname, port, logName, finalUrl, e.getMessage());
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
     * Download compressed log files for all processes in a cluster
     * Downloads only MONGODB and MONGOS log types (excludes audit logs)
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The name of the cluster
     * @param startDate Start date for log retrieval
     * @param endDate End date for log retrieval
     * @param outputDirectory Directory where log files will be saved
     * @return List of paths to downloaded log files
     * @throws IOException If file operations fail
     */
    public List<Path> downloadCompressedLogFilesForCluster(String projectId, String clusterName, 
                                                          Instant startDate, Instant endDate, 
                                                          String outputDirectory) throws IOException {
        return downloadCompressedLogFilesForCluster(projectId, clusterName, startDate, endDate, 
                                                   outputDirectory, DEFAULT_LOG_TYPES);
    }
    
    /**
     * Download compressed log files for all processes in a cluster with specified log types
     * Uses process hostnames WITHOUT port numbers as confirmed by MongoDB support
     */
    public List<Path> downloadCompressedLogFilesForCluster(String projectId, String clusterName, 
                                                          Instant startDate, Instant endDate, 
                                                          String outputDirectory, 
                                                          List<String> logTypes) throws IOException {
        
        logger.info("Starting download of compressed log files for cluster: {}", clusterName);
        logger.info("Log types to download: {}", logTypes);
        logger.info("Time range: {} to {}", startDate, endDate);
        logger.info("Output directory: {}", outputDirectory);
        
        List<Path> downloadedFiles = new ArrayList<>();
        
        // Create output directory if it doesn't exist
        Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);
        
        // Get all processes for the cluster using the existing method
        List<Map<String, Object>> processes = client.clusters().getProcessesForCluster(projectId, clusterName);
        
        if (processes.isEmpty()) {
            logger.warn("No processes found for cluster: {}", clusterName);
            return downloadedFiles;
        }
        
        logger.info("Found {} processes in cluster: {}", processes.size(), clusterName);
        
        // For each process, try to download each requested log type directly (skip listing step)
        for (Map<String, Object> process : processes) {
            String processId = (String) process.get("id");
            String[] hostPort = processId.split(":");
            String hostname = hostPort[0]; // Use hostname WITHOUT port
            int port = Integer.parseInt(hostPort[1]);
            
            // Get process type for better debugging
            String typeName = (String) process.get("typeName");
            
            logger.info("Processing logs for process: {} (type: {}) - using hostname: {}", 
                       processId, typeName, hostname);
            
            // Skip certain process types that typically don't have logs available
            if (typeName != null && (typeName.contains("CONFIG") || typeName.equals("ARBITER"))) {
                logger.info("Skipping process {} (type: {}) - this process type typically doesn't expose logs via this API", 
                           processId, typeName);
                continue;
            }
            
            // Try to download each requested log type directly (without listing first)
            for (String logType : logTypes) {
                try {
                    logger.info("Attempting direct download of {} for process {} ({})", 
                               logType, hostname, typeName);
                    
                    Path downloadedFile = downloadCompressedLogFileForHost(
                        projectId, hostname, port, logType, startDate, endDate, outputDirectory);
                    downloadedFiles.add(downloadedFile);
                    logger.info("Successfully downloaded {} for process {} ({})", 
                               logType, hostname, typeName);
                } catch (Exception e) {
                    logger.warn("Failed to download {} for process {} ({}): {} - continuing with next log type", 
                               logType, hostname, typeName, e.getMessage());
                    // Continue with other log types/processes
                }
            }
        }
        
        logger.info("Completed download for cluster: {}. Downloaded {} files total.", 
                   clusterName, downloadedFiles.size());
        
        return downloadedFiles;
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