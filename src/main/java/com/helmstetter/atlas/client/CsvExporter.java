package com.helmstetter.atlas.client;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports project metrics data to CSV format
 */
public class CsvExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(CsvExporter.class);
    
    private final List<String> metrics;
    
    public CsvExporter(List<String> metrics) {
        this.metrics = metrics;
    }
    
    /**
     * Export project metrics results to a CSV file
     * 
     * @param results The project metrics results to export
     * @param filename The name of the CSV file to create
     */
    public void exportProjectMetricsToCSV(Map<String, ProjectMetricsResult> results, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // Build header row with metrics
            StringBuilder header = new StringBuilder("Project");
            for (String metric : metrics) {
                header.append(",").append(metric).append("_Avg")
                      .append(",").append(metric).append("_Max")
                      .append(",").append(metric).append("_MaxLocation");
            }
            writer.write(header.toString() + "\n");
            
            // Write data rows for each project
            for (ProjectMetricsResult result : results.values()) {
                StringBuilder row = new StringBuilder(result.getProjectName());
                
                for (String metric : metrics) {
                    Double avgValue = result.getAvgValue(metric);
                    Double maxValue = result.getMaxValue(metric);
                    String maxLocation = result.getMaxLocation(metric);
                    
                    // Format the values, handle missing values
                    if (avgValue != null && maxValue != null && maxValue > Double.MIN_VALUE) {
                        // Convert MB to GB for memory metrics in the CSV
                        if (metric.equals("SYSTEM_MEMORY_USED") || metric.equals("SYSTEM_MEMORY_FREE")) {
                            avgValue = avgValue / 1024.0;  // Convert MB to GB
                            maxValue = maxValue / 1024.0;  // Convert MB to GB
                        }
                        
                        row.append(",").append(formatValue(avgValue))
                           .append(",").append(formatValue(maxValue))
                           .append(",\"").append(maxLocation).append("\"");
                    } else {
                        row.append(",0.00,0.00,\"\"");
                    }
                }
                writer.write(row.toString() + "\n");
            }
            
            logger.info("Project summary exported to {}", filename);
        } catch (IOException e) {
            logger.error("Error writing CSV file: {}", e.getMessage());
        }
    }
    
    /**
     * Format a double value for display (round to 2 decimal places)
     */
    private String formatValue(double value) {
        return String.format("%.2f", value);
    }
}