package com.helmstetter.atlas.client;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

/**
 * Main entry point for the MongoDB Atlas API client
 */
@Command(name = "MongoDBAtlasClient", mixinStandardHelpOptions = true, 
    description = "MongoDB Atlas API client", defaultValueProvider = PropertiesDefaultProvider.class)
public class MongoDBAtlasClient implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoDBAtlasClient.class);
    
    @Option(names = { "--apiPublicKey" }, description = "Atlas API public key", required = false)
    private String apiPublicKey;
    
    @Option(names = { "--apiPrivateKey" }, description = "Atlas API private key", required = false)
    private String apiPrivateKey;
    
    @Option(names = { "--includeProjectNames" }, description = "project names to be processed", required = false, split = ",")
    private Set<String> includeProjectNames;
    
    @Option(names = { "--config", "-c" }, description = "config file", required = false, defaultValue = "atlas-client.properties")
    private File configFile;
    
    @Option(names = { "--metrics" }, description = "metrics to analyze (comma-separated)", required = false, split = ",",
        defaultValue = "SYSTEM_NORMALIZED_CPU_USER,SYSTEM_MEMORY_USED,SYSTEM_MEMORY_FREE,DISK_PARTITION_IOPS_TOTAL")
    private List<String> metrics;
    
    @Option(names = { "--period" }, description = "time period to analyze (ISO 8601 duration format)", required = false, 
        defaultValue = "PT8H")
    private String period;
    
    @Option(names = { "--granularity" }, description = "measurement granularity (ISO 8601 duration format)", required = false, 
        defaultValue = "PT10S")
    private String granularity;
    
    @Option(names = { "--exportCsvFilename" }, description = "Export project summary to CSV file", required = false)
    private String exportCsvFilename;
    
    @Option(names = { "--analyzePatterns" }, description = "Enable pattern analysis", required = false, defaultValue = "false")
    private boolean analyzePatterns;
    
    @Option(names = { "--exportPatternsCsvFilename" }, description = "Export pattern analysis to CSV file", required = false)
    private String exportPatternsCsvFilename;
    
    @Option(names = { "--generateCharts" }, description = "Generate pattern charts", required = false, defaultValue = "false")
    private boolean generateCharts;
    
    @Option(names = { "--chartOutputDir" }, description = "Directory for pattern charts", required = false, defaultValue = "charts")
    private String chartOutputDir;
    
    // Add these options to the MongoDBAtlasClient class
    @Option(names = { "--combinedCharts" }, description = "Generate combined charts per metric", required = false, defaultValue = "true")
    private boolean combinedCharts;

    @Option(names = { "--generateDashboard" }, description = "Generate project dashboards", required = false, defaultValue = "true")
    private boolean generateDashboard;

    @Option(names = { "--generateHtmlIndex" }, description = "Generate HTML index of all charts", required = false, defaultValue = "true")
    private boolean generateHtmlIndex;
    
    // Service components
    private AtlasApiClient apiClient;
    private MetricsProcessor metricsProcessor;
    
    @Override
    public Integer call() throws Exception {
        // Initialize the API client
        this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey);
        
        // Initialize the metrics processor with pattern analysis option
        this.metricsProcessor = new MetricsProcessor(apiClient, metrics, period, granularity, analyzePatterns);
        
        // Process metrics for all projects
        Map<String, ProjectMetricsResult> results = metricsProcessor.processProjectMetrics(includeProjectNames);
        
        // Export to CSV if filename was provided
        if (exportCsvFilename != null && !exportCsvFilename.isEmpty()) {
            CsvExporter exporter = new CsvExporter(metrics, analyzePatterns);
            exporter.exportProjectMetricsToCSV(results, exportCsvFilename);
        }
        
        // Export pattern analysis to CSV if enabled and filename was provided
        if (analyzePatterns && exportPatternsCsvFilename != null && !exportPatternsCsvFilename.isEmpty()) {
            CsvExporter exporter = new CsvExporter(metrics);
            exporter.exportPatternAnalysisToCSV(results, exportPatternsCsvFilename);
        }
        
        // Generate visualizations if any visualization option is enabled
        if (analyzePatterns && (generateCharts || combinedCharts || generateDashboard || generateHtmlIndex)) {
            PatternVisualReporter reporter = new PatternVisualReporter(apiClient, chartOutputDir);
            
            // Generate only what's requested
            for (ProjectMetricsResult projectResult : results.values()) {
                // Individual per-host charts
                if (generateCharts) {
                    logger.info("Generating individual charts for project: {}", projectResult.getProjectName());
                    reporter.generatePatternCharts(projectResult, period, granularity);
                }
                
                // Combined charts (one per metric with all hosts)
                if (combinedCharts) {
                    logger.info("Generating combined charts for project: {}", projectResult.getProjectName());
                    for (String metric : projectResult.getMetrics()) {
                        reporter.generateCombinedMetricChart(projectResult, metric, period, granularity);
                    }
                }
                
                // Project dashboard (all metrics on one page)
                if (generateDashboard) {
                    logger.info("Generating dashboard for project: {}", projectResult.getProjectName());
                    reporter.generateProjectDashboard(projectResult, period, granularity);
                }
            }
            
            // Generate HTML index if requested
            if (generateHtmlIndex) {
                logger.info("Generating HTML index");
                reporter.createHtmlIndex(results);
            }
            
            logger.info("Visualizations generated in directory: {}", chartOutputDir);
        }
        
        return 0;
    }

    
    public static void main(String[] args) {
        MongoDBAtlasClient client = new MongoDBAtlasClient();
        Logger logger = LoggerFactory.getLogger(MongoDBAtlasClient.class);

        int exitCode = 0;
        try {
            CommandLine cmd = new CommandLine(client);
            ParseResult parseResult = cmd.parseArgs(args);

            File defaultsFile;
            if (client.configFile != null) {
                defaultsFile = client.configFile;
            } else {
                defaultsFile = new File("atlas-client.properties");
            }

            if (defaultsFile.exists()) {
                logger.info("Loading configuration from {}", defaultsFile.getAbsolutePath());
                cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
            } else {
                logger.warn("Configuration file {} not found", defaultsFile.getAbsolutePath());
            }
            parseResult = cmd.parseArgs(args);

            if (!CommandLine.printHelpIfRequested(parseResult)) {
                logger.info("Starting MongoDB Atlas client");
                exitCode = client.call();
                logger.info("MongoDB Atlas client completed with exit code {}", exitCode);
            }
        } catch (ParameterException ex) {
            logger.error("Parameter error: {}", ex.getMessage());
            ex.getCommandLine().usage(System.err);
            exitCode = 1;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            exitCode = 2;
        }

        System.exit(exitCode);
    }
}