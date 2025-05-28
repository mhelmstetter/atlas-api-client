package com.mongodb.atlas.api.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.metrics.PatternAnalyzer;
import com.mongodb.atlas.api.metrics.ProjectMetricsResult;
import com.mongodb.atlas.api.metrics.PatternAnalyzer.PatternResult;
import com.mongodb.atlas.api.metrics.PatternAnalyzer.PatternType;
import com.mongodb.atlas.api.util.MetricsUtils;

/**
 * Exports project metrics data to CSV format with improved formatting
 */
public class CsvExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(CsvExporter.class);
    
    private final List<String> metrics;
    private final boolean includePatterns;
    
    public CsvExporter(List<String> metrics) {
        this(metrics, false);
    }
    
    public CsvExporter(List<String> metrics, boolean includePatterns) {
        this.metrics = metrics;
        this.includePatterns = includePatterns;
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
                
                // Add pattern columns if enabled
                if (includePatterns) {
                    header.append(",").append(metric).append("_DominantPattern");
                }
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
                        // Convert to display units (e.g., MB to GB for memory metrics)
                        double displayAvg = MetricsUtils.convertToDisplayUnits(metric, avgValue);
                        double displayMax = MetricsUtils.convertToDisplayUnits(metric, maxValue);
                        
                        row.append(",").append(MetricsUtils.formatValue(displayAvg))
                           .append(",").append(MetricsUtils.formatValue(displayMax))
                           .append(",\"").append(maxLocation).append("\"");
                        
                        // Add pattern information if enabled
                        if (includePatterns) {
                            if (result.hasPatternData(metric)) {
                                PatternType dominantPattern = result.getDominantPattern(metric);
                                row.append(",\"").append(dominantPattern.getDescription()).append("\"");
                            } else {
                                row.append(",\"Unknown\"");
                            }
                        }
                    } else {
                        row.append(",0.00,0.00,\"\"");
                        
                        // Add empty pattern column if enabled
                        if (includePatterns) {
                            row.append(",\"\"");
                        }
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
     * Export detailed pattern analysis results to a CSV file
     * 
     * @param results The project metrics results to export
     * @param filename The name of the CSV file to create
     */
    public void exportPatternAnalysisToCSV(Map<String, ProjectMetricsResult> results, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // Build header row
            writer.write("Project,Metric,Location,PatternType,Volatility,TrendSlope,SpikeCount,SawtoothCycles,Details\n");
            
            // Write data rows for each project and metric
            for (ProjectMetricsResult result : results.values()) {
                String projectName = result.getProjectName();
                
                for (String metric : metrics) {
                    if (!result.hasPatternData(metric)) {
                        continue;
                    }
                    
                    Map<String, PatternAnalyzer.PatternResult> patternResults = result.getPatternResults(metric);
                    
                    for (Map.Entry<String, PatternAnalyzer.PatternResult> entry : patternResults.entrySet()) {
                        String location = entry.getKey();
                        PatternAnalyzer.PatternResult patternResult = entry.getValue();
                        
                        StringBuilder row = new StringBuilder();
                        row.append("\"").append(projectName).append("\",")
                           .append("\"").append(metric).append("\",")
                           .append("\"").append(location).append("\",")
                           .append("\"").append(patternResult.getPatternType().getDescription()).append("\",")
                           .append(MetricsUtils.formatValue(patternResult.getVolatility())).append(",")
                           .append(MetricsUtils.formatValue(patternResult.getTrendSlope())).append(",")
                           .append(patternResult.getSpikeCount()).append(",")
                           .append(patternResult.getSawtoothCycles()).append(",")
                           .append("\"").append(patternResult.getDetails()).append("\"");
                        
                        writer.write(row.toString() + "\n");
                    }
                }
            }
            
            logger.info("Pattern analysis exported to {}", filename);
        } catch (IOException e) {
            logger.error("Error writing pattern analysis CSV file: {}", e.getMessage());
        }
    }
}