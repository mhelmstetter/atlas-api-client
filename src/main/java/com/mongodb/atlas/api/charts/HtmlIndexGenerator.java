package com.mongodb.atlas.api.charts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.metrics.ProjectMetricsResult;

/**
 * Generates HTML index page for MongoDB Atlas metrics charts
 * Enhanced to properly support specified chart dimensions
 */
public class HtmlIndexGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(HtmlIndexGenerator.class);
    
    private final String outputDirectory;
    private final int chartWidth;
    private final int chartHeight;
    private final boolean darkMode;
    
    public HtmlIndexGenerator(String outputDirectory, int chartWidth, int chartHeight, boolean darkMode) {
        this.outputDirectory = outputDirectory;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.darkMode = darkMode;
    }
    
    /**
     * Generate HTML index with all charts
     */
    public void generate(Map<String, ProjectMetricsResult> projectResults) {
        String indexPath = outputDirectory + "/index.html";
        
        try (FileWriter writer = new FileWriter(indexPath)) {
            // Write HTML head
            writeHtmlHead(writer);
            
            // Generate timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.write("  <p class='timestamp'>Generated on: " + dateFormat.format(new Date()) + "</p>\n");
            
            // Create a single grid for all projects
            writer.write("  <div class='all-charts'>\n");
            
            // Track if we found any charts
            boolean hasAnyCharts = false;
            
            // Get sorted projects
            List<ProjectMetricsResult> sortedProjects = getSortedProjects(projectResults);
            
            // For each project, collect and display charts
            for (ProjectMetricsResult projectResult : sortedProjects) {
                boolean projectHasCharts = writeProjectCharts(writer, projectResult);
                hasAnyCharts |= projectHasCharts;
            }
            
            if (!hasAnyCharts) {
                writer.write("  <p>No charts available for any projects.</p>\n");
            }
            
            writer.write("  </div>\n");
            writer.write("</body>\n");
            writer.write("</html>\n");
            
            logger.info("HTML index created at: {}", indexPath);
        } catch (IOException e) {
            logger.error("Error creating HTML index: {}", e.getMessage());
        }
    }
    
    /**
     * Write HTML head section with styles
     * Enhanced to properly size charts according to specified dimensions
     */
    private void writeHtmlHead(FileWriter writer) throws IOException {
        writer.write("<!DOCTYPE html>\n");
        writer.write("<html>\n");
        writer.write("<head>\n");
        writer.write("  <title>MongoDB Atlas Metrics</title>\n");
        writer.write("  <style>\n");
        writer.write("    body { font-family: Arial, sans-serif; margin: 20px; }\n");
        
        // Apply styles based on dark mode setting
        if (darkMode) {
            writer.write("    body { background-color: #1e1e1e; color: #e6e6e6; }\n");
            writer.write("    h1 { color: #e6e6e6; font-size: 22px; margin: 15px 0; }\n");
            writer.write("    h2 { color: #aaaaaa; font-size: 14px; margin: 5px 0; }\n");
            writer.write("    .chart-cell { background-color: #2d2d2d; padding: 10px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); }\n");
            writer.write("    .timestamp { color: #888888; font-size: 12px; margin-bottom: 20px; }\n");
        } else {
            writer.write("    body { background-color: #f5f5f5; color: #333; }\n");
            writer.write("    h1 { color: #333; font-size: 22px; margin: 15px 0; }\n");
            writer.write("    h2 { color: #666; font-size: 14px; margin: 5px 0; }\n");
            writer.write("    .chart-cell { background-color: #fff; padding: 10px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
            writer.write("    .timestamp { color: #999; font-size: 12px; margin-bottom: 20px; }\n");
        }
        
        // Calculate minimum column width based on chart dimensions plus padding
        int minColumnWidth = chartWidth + 40; // Add padding for chart cell
        
        // Use the actual chart width for the grid layout instead of fixed 350px
        writer.write(String.format("    .all-charts { display: grid; grid-template-columns: repeat(auto-fill, minmax(%dpx, 1fr)); gap: 15px; }\n", minColumnWidth));
        
        // Set explicit chart dimensions
        writer.write(String.format("    .chart-img { width: %dpx; height: %dpx; max-width: 100%%; }\n", chartWidth, chartHeight));
        writer.write("    .chart-container { display: flex; justify-content: center; align-items: center; overflow: hidden; }\n");
        
        // Add responsive behavior for smaller screens
        writer.write("    @media (max-width: 768px) {\n");
        writer.write("      .all-charts { grid-template-columns: 1fr; }\n");
        writer.write("      .chart-img { width: 100%; height: auto; }\n");
        writer.write("    }\n");
        
        writer.write("  </style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("  <h1>MongoDB Atlas Metrics</h1>\n");
    }
    
    /**
     * Write project charts to HTML
     * @return true if any charts were found for this project
     */
    private boolean writeProjectCharts(FileWriter writer, ProjectMetricsResult projectResult) throws IOException {
        boolean hasCharts = false;
        String projectName = projectResult.getProjectName();
        String safeProjectName = projectName.replace(' ', '_');
        
        for (String metric : projectResult.getMetrics()) {
            String combinedChartFile = safeProjectName + "_" + metric + "_combined.svg";
            File combinedChartFileCheck = new File(outputDirectory, combinedChartFile);
            
            if (combinedChartFileCheck.exists()) {
                hasCharts = true;
                
                writer.write("    <div class='chart-cell'>\n");
                writer.write("      <h2>" + projectName + "</h2>\n");
                writer.write("      <div class='chart-container'>\n");
                
                // Use explicit width and height attributes and ensure proper scaling
                writer.write(String.format("        <object type='image/svg+xml' data='%s' " +
                        "alt='%s for %s' class='chart-img' width='%d' height='%d'>" +
                        "Your browser does not support SVG</object>\n", 
                        combinedChartFile, metric, projectName, chartWidth, chartHeight));
                
                writer.write("      </div>\n");
                writer.write("    </div>\n");
            }
        }
        
        return hasCharts;
    }
    
    /**
     * Sort projects by name using natural alphanumeric ordering
     */
    private List<ProjectMetricsResult> getSortedProjects(Map<String, ProjectMetricsResult> projectResults) {
        return projectResults.values().stream()
            .sorted(Comparator.comparing(ProjectMetricsResult::getProjectName, new AlphaNumericComparator()))
            .collect(Collectors.toList());
    }
    
    /**
     * Comparator for natural alphanumeric sorting of project names
     * Sorts first by non-numeric characters, then by numeric portions as numbers
     */
    private static class AlphaNumericComparator implements Comparator<String> {
        private static final Pattern PATTERN = Pattern.compile("(\\D*)(\\d*)");
        
        @Override
        public int compare(String s1, String s2) {
            Matcher m1 = PATTERN.matcher(s1);
            Matcher m2 = PATTERN.matcher(s2);
            
            // Continue matching and comparing until the strings are different
            while (m1.find() && m2.find()) {
                // Compare non-numeric portions case-insensitively
                String nonDigit1 = m1.group(1);
                String nonDigit2 = m2.group(1);
                int nonDigitCompare = nonDigit1.compareToIgnoreCase(nonDigit2);
                if (nonDigitCompare != 0) {
                    return nonDigitCompare;
                }
                
                // Compare numeric portions as numbers (not as strings)
                String digit1 = m1.group(2);
                String digit2 = m2.group(2);
                if (!digit1.isEmpty() && !digit2.isEmpty()) {
                    int num1 = Integer.parseInt(digit1);
                    int num2 = Integer.parseInt(digit2);
                    if (num1 != num2) {
                        return num1 - num2;
                    }
                } else if (!digit1.isEmpty()) {
                    return 1; // s1 has a number, s2 doesn't
                } else if (!digit2.isEmpty()) {
                    return -1; // s2 has a number, s1 doesn't
                }
                
                // If we've matched the entire strings, we're done
                if (m1.end() == s1.length() && m2.end() == s2.length()) {
                    return 0;
                }
                
                // Reset the matchers to start from where we left off
                m1 = PATTERN.matcher(s1).region(m1.end(), s1.length());
                m2 = PATTERN.matcher(s2).region(m2.end(), s2.length());
            }
            
            // If one string is done and the other isn't, the shorter comes first
            return s1.length() - s2.length();
        }
    }
}