package com.mongodb.atlas.api.clients;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.logs.AtlasLogType;

/**
 * Atlas API client for logs endpoints with binary gzip support
 * Updated to use AtlasLogType enum for type safety and correct filename generation
 */
public class AtlasLogsClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasLogsClient.class);
    
    private final AtlasApiBase apiBase;
    private final AtlasApiClient client;
    
    // Atlas gzip accept header - using v2 API format as per CLI
    public static final String GZIP_ACCEPT_HEADER = "application/vnd.atlas.2023-02-01+gzip";
    
    public AtlasLogsClient(AtlasApiBase apiBase, AtlasApiClient client) {
        this.apiBase = apiBase;
        this.client = client;
    }
    
    /**
     * Get default log types (non-audit) as filenames
     */
    public static List<String> getDefaultLogTypeFileNames() {
        return AtlasLogType.toFileNames(AtlasLogType.getDefaultLogTypes());
    }
    
    /**
     * Get all log types including audit as filenames
     */
    public static List<String> getAllLogTypeFileNames() {
        return AtlasLogType.toFileNames(AtlasLogType.getAllLogTypes());
    }
    
    /**
     * Get available log types for a process - Atlas API doesn't provide this endpoint
     * Instead, we return standard log types based on process type
     */
    public List<Map<String, Object>> getLogTypes(String projectId, String hostname, int port) {
        logger.info("Determining available log types for hostname: {} (port: {})", hostname, port);
        
        // Atlas API doesn't have a "list log types" endpoint, so we determine
        // available log types based on the port (27017 = mongod, 27016 = mongos)
        List<Map<String, Object>> logTypes = new ArrayList<>();
        
        if (port == 27017) {
            // mongod process - supports mongodb.gz
            logTypes.add(Map.of("name", "mongodb.gz", "type", "database"));
        } else if (port == 27016) {
            // mongos process - supports mongos.gz  
            logTypes.add(Map.of("name", "mongos.gz", "type", "router"));
        }
        
        return logTypes;
    }
    
    /**
     * Get available log types for all processes in a cluster
     */
    public Map<String, List<Map<String, Object>>> getLogTypesForCluster(String projectId, String clusterName) {
        logger.info("Fetching log types for all processes in cluster: {}", clusterName);
        
        // Get all processes for the project
        List<Map<String, Object>> processes = client.clusters().getProcessesForCluster(projectId, clusterName);
        
        return processes.stream()
            .filter(process -> {
                String processId = (String) process.get("id");
                String typeName = (String) process.get("typeName");
                
                // Skip processes that don't provide user logs (config servers, arbiters)
                if (shouldSkipProcess(typeName, processId)) {
                    logger.info("Skipping log types check for process {} (type: {}) - this process type doesn't provide user logs", 
                               processId, typeName);
                    return false;
                }
                return true;
            })
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
        
        // Build URL using hostname as per Atlas API specification  
        String baseUrl = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + hostname + "/logs/" + logName;
        
        // Build query parameters conditionally like Python code
        StringBuilder url = new StringBuilder(baseUrl);
        
        if (startDate != null && endDate != null) {
            // Both dates provided
            long startEpochSeconds = startDate.getEpochSecond();
            long endEpochSeconds = endDate.getEpochSecond();
            
            // Validation to prevent future dates
            long currentEpochSeconds = Instant.now().getEpochSecond();
            if (startEpochSeconds > currentEpochSeconds) {
                throw new IllegalArgumentException("Start date cannot be in the future");
            }
            if (endEpochSeconds > currentEpochSeconds) {
                throw new IllegalArgumentException("End date cannot be in the future");
            }
            
            url.append("?endDate=").append(endEpochSeconds).append("&startDate=").append(startEpochSeconds);
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
        logger.debug("Fetching compressed log data from URL: {}", finalUrl);
        logger.debug("Log details - Host: {}, Port: {}, Log: {}, Start: {}, End: {}", 
                hostname, port, logName, startDate, endDate);
        
        try {
            // Get binary gzip data using the special accept header
            byte[] compressedData = apiBase.getBinaryResponseBody(finalUrl, GZIP_ACCEPT_HEADER, projectId);
            
            if (compressedData == null || compressedData.length == 0) {
                throw new AtlasApiBase.AtlasApiException(
                    "Cannot read the array length because \"compressedData\" is null");
            }
            
            logger.debug("Retrieved {} bytes of compressed log data for {}:{} log {}", 
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
                                                   outputDirectory, getDefaultLogTypeFileNames());
    }
    
    /**
     * Download compressed log files for all processes in a cluster with specified log types
     * Uses smart filtering to only request appropriate log types for each process type
     */
    public List<Path> downloadCompressedLogFilesForCluster(String projectId, String clusterName, 
                                                          Instant startDate, Instant endDate, 
                                                          String outputDirectory, 
                                                          List<String> requestedLogTypeFileNames) throws IOException {
        
        // Convert filenames to enum types for better type safety
        List<AtlasLogType> requestedLogTypes = AtlasLogType.fromFileNames(requestedLogTypeFileNames);
        
        logger.info("Starting download of compressed log files for cluster: {}", clusterName);
        logger.info("Requested log types: {}", requestedLogTypes);
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
        System.out.println("🔄 Processing " + processes.size() + " processes in cluster: " + clusterName);
        
        int processCount = 0;
        int failedCount = 0;
        // For each process, download only the appropriate log types
        for (Map<String, Object> process : processes) {
            processCount++;
            String processId = (String) process.get("id");
            String[] hostPort = processId.split(":");
            String hostname = hostPort[0]; // Use hostname WITHOUT port
            int port = Integer.parseInt(hostPort[1]);
            
            // Get process type for filtering
            String typeName = (String) process.get("typeName");
            
            logger.debug("Processing logs for process: {} (type: {}) - using hostname: {}", 
                       processId, typeName, hostname);
            
            // Skip process types that don't have user logs
            if (shouldSkipProcess(typeName, processId)) {
                logger.info("Skipping process {} (type: {}) - this process type doesn't provide user logs", 
                           processId, typeName);
                System.out.println("⏭️  [" + processCount + "/" + processes.size() + "] Skipping " + processId + " (config server)");
                continue;
            }
            
            // Get only the log types that make sense for this process type
            List<AtlasLogType> applicableLogTypes = getApplicableLogTypes(requestedLogTypes, typeName, port);
            
            if (applicableLogTypes.isEmpty()) {
                logger.info("No applicable log types for process {} (type: {})", processId, typeName);
                continue;
            }
            
            logger.debug("Applicable log types for {} ({}): {}", 
                       processId, typeName, AtlasLogType.toFileNames(applicableLogTypes));
            
            // Download only the applicable log types
            for (AtlasLogType logType : applicableLogTypes) {
                try {
                    logger.debug("Downloading {} for process {} ({})", logType.getFileName(), hostname, typeName);
                    
                    Path downloadedFile = downloadCompressedLogFileForHost(
                        projectId, clusterName, hostname, port, logType, startDate, endDate, outputDirectory);
                    downloadedFiles.add(downloadedFile);
                    
                    // Show concise success message: [count] short-hostname → logtype ✅ 
                    String shortHostname = hostname.split("\\.")[0]; // Just the first part before the dot
                    System.out.println("📥 [" + processCount + "/" + processes.size() + "] " + shortHostname + " → " + logType.getFileName() + " ✅");
                    logger.debug("Successfully downloaded {} for process {} ({})", 
                               logType.getFileName(), hostname, typeName);
                } catch (Exception e) {
                    failedCount++;
                    String shortHostname = hostname.split("\\.")[0]; // Just the first part before the dot
                    System.out.println("❌ [" + processCount + "/" + processes.size() + "] " + shortHostname + " → " + logType.getFileName() + " (" + e.getMessage() + ")");
                    logger.warn("Failed to download {} for process {} ({}): {} - continuing with next log type", 
                               logType.getFileName(), hostname, typeName, e.getMessage());
                    // Continue with other log types/processes
                }
            }
        }
        
        logger.info("Completed download for cluster: {}. Downloaded {} files total.", 
                   clusterName, downloadedFiles.size());
        
        // Print summary
        System.out.println();
        System.out.println("═".repeat(50));
        System.out.println("📊 Download Summary:");
        System.out.println("   ✅ Successfully downloaded: " + downloadedFiles.size() + " files");
        if (failedCount > 0) {
            System.out.println("   ❌ Failed downloads: " + failedCount);
        }
        System.out.println("   📁 Output directory: " + outputDirectory);
        System.out.println("═".repeat(50));
        
        return downloadedFiles;
    }
    
    /**
     * Determine if a process should be skipped entirely
     */
    private boolean shouldSkipProcess(String processTypeName, String processId) {
        // Check process type name first
        if (processTypeName != null) {
            String typeUpper = processTypeName.toUpperCase();
            
            // Skip config servers - they don't provide user application logs
            if (typeUpper.contains("CONFIG")) {
                return true;
            }
            
            // Skip arbiters - they don't store data or provide logs
            if (typeUpper.equals("ARBITER")) {
                return true;
            }
        }
        
        // Fallback: check process ID/hostname for config server patterns
        if (processId != null) {
            String processIdUpper = processId.toUpperCase();
            
            // Skip config servers based on hostname pattern
            if (processIdUpper.contains("-CONFIG-")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get applicable log types for a process - ONLY return types that make sense
     */
    private List<AtlasLogType> getApplicableLogTypes(List<AtlasLogType> requestedLogTypes, 
                                                   String processTypeName, int port) {
        List<AtlasLogType> applicable = new ArrayList<>();
        
        for (AtlasLogType logType : requestedLogTypes) {
            boolean isApplicable;
            
            if (processTypeName != null) {
                // Use process type based filtering (more reliable)
                isApplicable = logType.isCompatibleWith(processTypeName);
                if (!isApplicable) {
                    logger.debug("Skipping {} for process type {} - not compatible", 
                                logType.getFileName(), processTypeName);
                }
            } else {
                // Fallback to port-based filtering
                isApplicable = logType.isCompatibleWithPort(port);
                if (!isApplicable) {
                    logger.debug("Skipping {} for port {} - not compatible", 
                                logType.getFileName(), port);
                }
            }
            
            if (isApplicable) {
                applicable.add(logType);
            }
        }
        
        return applicable;
    }
    
    /**
     * Download and save compressed log file with correct filename generation using enum metadata
     */
    public Path downloadCompressedLogFileForHost(String projectId, String clusterName, String hostname, int port, 
                                        AtlasLogType logType, Instant startDate, Instant endDate, 
                                        String outputDirectory) throws IOException {
        
        byte[] compressedData = getCompressedLogsForHost(projectId, hostname, port, 
                                                        logType.getFileName(), startDate, endDate);
        
        // Create output directory if it doesn't exist
        Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);
        
        // Generate filename using standard Atlas naming convention
        // Format: {hostname}_{startDate}_{endDate}_{LOGTYPE}.log.gz
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss");
        
        String startDateStr = startDate != null ? startDate.atZone(java.time.ZoneOffset.UTC).format(dateFormatter) : "unknown";
        String endDateStr = endDate != null ? endDate.atZone(java.time.ZoneOffset.UTC).format(dateFormatter) : "unknown";
        String logTypeUpper = logType.getCleanName().toUpperCase(); // MONGODB or MONGOS
        
        String filename = String.format("%s_%s_%s_%s.log.gz", 
                                       hostname,
                                       startDateStr,
                                       endDateStr,
                                       logTypeUpper);
        
        Path outputFile = outputDir.resolve(filename);
        
        // Write compressed data directly to file
        try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            fos.write(compressedData);
        }
        
        logger.debug("Downloaded compressed log file for {}:{} {} to: {} ({} bytes)", 
                   hostname, port, logType.getFileName(), outputFile, compressedData.length);
        return outputFile;
    }
    
    /**
     * Download and save compressed log file (keeps original gzip format) - backward compatibility method
     */
    public Path downloadCompressedLogFileForHost(String projectId, String hostname, int port, 
                                        String logName, Instant startDate, Instant endDate, 
                                        String outputDirectory) throws IOException {
        
        AtlasLogType logType = AtlasLogType.fromFileName(logName);
        return downloadCompressedLogFileForHost(projectId, "unknown-cluster", hostname, port, logType, startDate, endDate, outputDirectory);
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
    
    /**
     * Get database access logs (authentication attempts) for a cluster
     */
    public List<Map<String, Object>> getAccessLogsForCluster(String projectId, String clusterName, 
                                                           String startDate, String endDate, int limit) {
        // Build URL for access logs endpoint
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + 
                   "/dbAccessHistory/clusters/" + clusterName;

        // Add query parameters
        StringBuilder queryParams = new StringBuilder();
        if (startDate != null) {
            queryParams.append("start=").append(startDate).append("&");
        }
        if (endDate != null) {
            queryParams.append("end=").append(endDate).append("&");
        }
        queryParams.append("itemsPerPage=").append(limit);

        if (queryParams.length() > 0) {
            url += "?" + queryParams.toString();
        }

        logger.info("Fetching access logs from URL: {}", url);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }
}