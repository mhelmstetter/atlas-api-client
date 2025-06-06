package com.mongodb.atlas.api.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.metrics.MetricsStorage;
import com.mongodb.atlas.api.util.MetricsUtils;

/**
 * Exports detailed metrics data to CSV format for individual projects
 * Can export from either stored data or direct API calls
 */
public class DetailedMetricsCsvExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(DetailedMetricsCsvExporter.class);
    
    private final MetricsStorage metricsStorage;
    private final AtlasApiClient apiClient;
    private final List<String> metrics;
    private final String period;
    private final String granularity;
    
    public DetailedMetricsCsvExporter(MetricsStorage metricsStorage, AtlasApiClient apiClient, 
                                    List<String> metrics, String period, String granularity) {
        this.metricsStorage = metricsStorage;
        this.apiClient = apiClient;
        this.metrics = metrics;
        this.period = period;
        this.granularity = granularity;
    }
    
    /**
     * Export detailed metrics for a project from stored data
     * 
     * @param projectName The project name
     * @param filename The CSV filename to export to
     */
    public void exportProjectDetailedMetricsFromStorage(String projectName, String filename) {
        if (metricsStorage == null) {
            throw new IllegalStateException("MetricsStorage is required for exporting from storage");
        }
        
        try (FileWriter writer = new FileWriter(filename)) {
            // Calculate time range
            Instant endTime = Instant.now();
            Instant startTime;
            
            if (period != null && !period.isEmpty()) {
                startTime = MetricsUtils.calculateStartTime(endTime, period);
                logger.info("Exporting detailed metrics for project '{}' from {} to {}", 
                        projectName, startTime, endTime);
            } else {
                // Use all available data for this project
                startTime = findEarliestDataTime(projectName);
                logger.info("Exporting detailed metrics for project '{}' using all available data from {} to {}", 
                        projectName, startTime, endTime);
            }
            
            // Collect all data first, organized by timestamp and host/partition
            Map<String, Map<String, Map<String, Double>>> allData = collectAllMetricsData(
                    projectName, startTime, endTime);
            
            if (allData.isEmpty()) {
                logger.warn("No data found for project '{}' in the specified time range", projectName);
                return;
            }
            
            // Check if we have any disk metrics to determine if we need partition column
            boolean hasDiskMetrics = metrics.stream().anyMatch(m -> m.startsWith("DISK_"));
            
            // Write CSV header
            writeHeader(writer, hasDiskMetrics);
            
            // Write data rows
            int totalRows = writeDataRows(writer, allData, hasDiskMetrics);
            
            logger.info("Exported {} rows of detailed metrics for project '{}' to {}", 
                    totalRows, projectName, filename);
            
        } catch (IOException e) {
            logger.error("Error writing detailed metrics CSV file for project '{}': {}", 
                    projectName, e.getMessage());
            throw new RuntimeException("Failed to export detailed metrics", e);
        }
    }
    
    /**
     * Collect all metrics data and organize by timestamp -> host/partition -> metric -> value
     */
    private Map<String, Map<String, Map<String, Double>>> collectAllMetricsData(
            String projectName, Instant startTime, Instant endTime) {
        
        // Structure: timestamp -> host/partition -> metric -> value
        Map<String, Map<String, Map<String, Double>>> allData = new TreeMap<>();
        
        // Process each metric
        for (String metric : metrics) {
            List<Document> documents = metricsStorage.getMetrics(
                    projectName, null, metric, startTime, endTime);
            
            // Process each document
            for (Document doc : documents) {
                Instant timestamp = doc.getDate("timestamp").toInstant();
                String timestampStr = timestamp.toString();
                
                Document metadata = doc.get("metadata", Document.class);
                String host = metadata.getString("host");
                String partition = metadata.getString("partition");
                
                // Create location key
                String locationKey;
                if (partition != null && !partition.isEmpty()) {
                    // For disk metrics: host:port:partition
                    locationKey = host + ":" + partition;
                } else {
                    // For system metrics: just host:port
                    locationKey = host;
                }
                
                // Get value
                Object valueObj = doc.get("value");
                Double value = 0.0;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue();
                }
                
                // Store in nested structure
                allData.computeIfAbsent(timestampStr, k -> new TreeMap<>())
                       .computeIfAbsent(locationKey, k -> new HashMap<>())
                       .put(metric, value);
            }
        }
        
        return allData;
    }
    
    /**
     * Write the CSV header row
     */
    private void writeHeader(FileWriter writer, boolean includePartition) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("Timestamp,Host");
        
        if (includePartition) {
            header.append(",Partition");
        }
        
        // Add normalized metric names as column headers
        for (String metric : metrics) {
            String normalizedMetric = normalizeMetricName(metric);
            header.append(",").append(normalizedMetric);
        }
        
        writer.write(header.toString() + "\n");
    }
    
    /**
     * Write all data rows
     */
    private int writeDataRows(FileWriter writer, Map<String, Map<String, Map<String, Double>>> allData, 
                             boolean includePartition) throws IOException {
        int totalRows = 0;
        
        // Iterate through all timestamps and locations
        for (Map.Entry<String, Map<String, Map<String, Double>>> timestampEntry : allData.entrySet()) {
            String timestamp = timestampEntry.getKey();
            Map<String, Map<String, Double>> locationData = timestampEntry.getValue();
            
            for (Map.Entry<String, Map<String, Double>> locationEntry : locationData.entrySet()) {
                String locationKey = locationEntry.getKey();
                Map<String, Double> metricValues = locationEntry.getValue();
                
                StringBuilder row = new StringBuilder();
                
                // Timestamp
                row.append("\"").append(timestamp).append("\",");
                
                // Parse location key
                String host;
                String partition = "";
                
                if (includePartition && locationKey.contains(":")) {
                    // Check if this is a disk metric with partition
                    // For disk metrics, locationKey format is: host:port:partition
                    String[] parts = locationKey.split(":", 3);
                    if (parts.length >= 3) {
                        // This is a disk metric: host:port:partition
                        host = parts[0] + ":" + parts[1]; // Combine host:port
                        partition = parts[2];
                    } else {
                        // This is a system metric: host:port
                        host = locationKey;
                        partition = "";
                    }
                } else {
                    // System metric or no partition
                    host = locationKey;
                    partition = "";
                }
                
                // Host (includes port)
                row.append("\"").append(host).append("\"");
                
                // Partition column (only if we're including it)
                if (includePartition) {
                    row.append(",\"").append(partition).append("\"");
                }
                
                // Add metric values in the same order as the header
                for (String metric : metrics) {
                    row.append(",");
                    Double value = metricValues.get(metric);
                    if (value != null) {
                        row.append(value);
                    } else {
                        // Empty cell if no data for this metric at this timestamp/location
                        row.append("");
                    }
                }
                
                writer.write(row.toString() + "\n");
                totalRows++;
            }
        }
        
        return totalRows;
    }
    
    /**
     * Normalize metric name to be more human-readable
     * Converts SYSTEM_NORMALIZED_CPU_USER to "System Normalized CPU User"
     */
    private String normalizeMetricName(String metric) {
        if (metric == null || metric.isEmpty()) {
            return metric;
        }
        
        // Split by underscores and convert to title case
        String[] words = metric.toLowerCase().split("_");
        StringBuilder normalized = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                normalized.append(" ");
            }
            
            // Capitalize first letter of each word
            String word = words[i];
            if (!word.isEmpty()) {
                normalized.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    normalized.append(word.substring(1));
                }
            }
        }
        
        return normalized.toString();
    }
    
    /**
     * Export detailed metrics for a project from API calls
     * 
     * @param projectName The project name
     * @param projectId The project ID
     * @param filename The CSV filename to export to
     */
    public void exportProjectDetailedMetricsFromApi(String projectName, String projectId, String filename) {
        if (apiClient == null) {
            throw new IllegalStateException("AtlasApiClient is required for exporting from API");
        }
        
        try (FileWriter writer = new FileWriter(filename)) {
            logger.info("Exporting detailed metrics for project '{}' via API", projectName);
            
            // Collect all data first, organized by timestamp and host/partition
            Map<String, Map<String, Map<String, Double>>> allData = collectAllMetricsDataFromApi(
                    projectName, projectId);
            
            if (allData.isEmpty()) {
                logger.warn("No data found for project '{}' via API", projectName);
                return;
            }
            
            // Check if we have any disk metrics to determine if we need partition column
            boolean hasDiskMetrics = metrics.stream().anyMatch(m -> m.startsWith("DISK_"));
            
            // Write CSV header
            writeHeader(writer, hasDiskMetrics);
            
            // Write data rows
            int totalRows = writeDataRows(writer, allData, hasDiskMetrics);
            
            logger.info("Exported {} rows of detailed metrics for project '{}' to {}", 
                    totalRows, projectName, filename);
            
        } catch (IOException e) {
            logger.error("Error writing detailed metrics CSV file for project '{}': {}", 
                    projectName, e.getMessage());
            throw new RuntimeException("Failed to export detailed metrics", e);
        }
    }
    
    /**
     * Collect all metrics data from API and organize by timestamp -> host/partition -> metric -> value
     */
    private Map<String, Map<String, Map<String, Double>>> collectAllMetricsDataFromApi(
            String projectName, String projectId) {
        
        // Structure: timestamp -> host/partition -> metric -> value
        Map<String, Map<String, Map<String, Double>>> allData = new TreeMap<>();
        
        // Get all processes for this project
        List<Map<String, Object>> processes = apiClient.clusters().getProcesses(projectId);
        
        // Filter out config servers and mongos instances
        List<Map<String, Object>> filteredProcesses = processes.stream()
                .filter(process -> {
                    String typeName = (String) process.get("typeName");
                    return !typeName.startsWith("SHARD_CONFIG") && !typeName.equals("SHARD_MONGOS");
                })
                .collect(Collectors.toList());
        
        logger.info("Found {} mongod processes for project '{}'", filteredProcesses.size(), projectName);
        
        // Process each MongoDB instance
        for (Map<String, Object> process : filteredProcesses) {
            String hostname = (String) process.get("hostname");
            int port = (int) process.get("port");
            String hostPort = hostname + ":" + port;
            
            try {
                // Collect system metrics
                List<String> systemMetrics = metrics.stream()
                        .filter(m -> !m.startsWith("DISK_"))
                        .collect(Collectors.toList());
                
                if (!systemMetrics.isEmpty()) {
                    List<Map<String, Object>> measurements = apiClient.monitoring().getProcessMeasurementsWithTimeRange(
                            projectId, hostname, port, systemMetrics, granularity, period);
                    
                    processApiMeasurements(allData, measurements, hostPort, null);
                }
                
                // Collect disk metrics if requested
                List<String> diskMetrics = metrics.stream()
                        .filter(m -> m.startsWith("DISK_"))
                        .collect(Collectors.toList());
                
                if (!diskMetrics.isEmpty()) {
                    // Get disk partitions
                    List<Map<String, Object>> disks = apiClient.monitoring().getProcessDisks(projectId, hostname, port);
                    
                    for (Map<String, Object> disk : disks) {
                        String partitionName = (String) disk.get("partitionName");
                        
                        List<Map<String, Object>> diskMeasurements = apiClient.monitoring().getDiskMeasurementsWithTimeRange(
                                projectId, hostname, port, partitionName, diskMetrics, granularity, period);
                        
                        processApiMeasurements(allData, diskMeasurements, hostPort, partitionName);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error collecting metrics for process {}:{} in project '{}': {}", 
                        hostname, port, projectName, e.getMessage());
            }
        }
        
        return allData;
    }
    
    /**
     * Process API measurements and add to the data structure
     */
    private void processApiMeasurements(Map<String, Map<String, Map<String, Double>>> allData,
                                      List<Map<String, Object>> measurements, 
                                      String host, String partition) {
        if (measurements == null || measurements.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> measurement : measurements) {
            String metric = (String) measurement.get("name");
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");
            
            if (dataPoints == null || dataPoints.isEmpty()) {
                continue;
            }
            
            for (Map<String, Object> dataPoint : dataPoints) {
                String timestampStr = (String) dataPoint.get("timestamp");
                
                // Create location key
                String locationKey;
                if (partition != null && !partition.isEmpty()) {
                    // For disk metrics: host:port:partition
                    locationKey = host + ":" + partition;
                } else {
                    // For system metrics: just host:port
                    locationKey = host;
                }
                
                // Get value
                Object valueObj = dataPoint.get("value");
                Double value = 0.0;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue();
                }
                
                // Store in nested structure
                allData.computeIfAbsent(timestampStr, k -> new TreeMap<>())
                       .computeIfAbsent(locationKey, k -> new HashMap<>())
                       .put(metric, value);
            }
        }
    }
    
    /**
     * Find the earliest data time for a specific project across all metrics
     */
    private Instant findEarliestDataTime(String projectName) {
        Instant earliest = Instant.now();
        
        for (String metric : metrics) {
            try {
                Instant metricEarliest = metricsStorage.getEarliestDataTime(projectName, null, metric);
                if (!metricEarliest.equals(Instant.EPOCH) && metricEarliest.isBefore(earliest)) {
                    earliest = metricEarliest;
                }
            } catch (Exception e) {
                logger.warn("Error finding earliest time for project {} metric {}: {}", 
                        projectName, metric, e.getMessage());
            }
        }
        
        return earliest;
    }
}