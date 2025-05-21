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
 * Enhanced with metric storage and separation of collection from processing
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
    
    @Option(names = { "--chartOutputDir" }, description = "Directory for charts", required = false, defaultValue = "charts")
    private String chartOutputDir;
    
    @Option(names = { "--generateHtmlIndex" }, description = "Generate HTML index of all charts", required = false, defaultValue = "true")
    private boolean generateHtmlIndex;
    
    @Option(names = { "--chartWidth" }, description = "Width of generated charts in pixels", required = false, defaultValue = "600")
    private int chartWidth;
    
    @Option(names = { "--chartHeight" }, description = "Height of generated charts in pixels", required = false, defaultValue = "300")
    private int chartHeight;
    
    @Option(names = { "--darkMode" }, description = "Enable dark mode for charts and HTML", required = false, defaultValue = "true")
    private boolean darkMode;
    
    // New options for metrics storage
    @Option(names = { "--storeMetrics" }, description = "Store metrics in MongoDB", required = false, defaultValue = "false")
    private boolean storeMetrics;
    
    @Option(names = { "--collectOnly" }, description = "Only collect and store metrics without processing", required = false, defaultValue = "false")
    private boolean collectOnly;
    
    @Option(names = { "--mongodbUri" }, description = "MongoDB connection URI for metrics storage", required = false)
    private String mongodbUri;
    
    @Option(names = { "--mongodbDatabase" }, description = "MongoDB database name for metrics storage", required = false, defaultValue = "atlas_metrics")
    private String mongodbDatabase;
    
    @Option(names = { "--mongodbCollection" }, description = "MongoDB collection name for metrics storage", required = false, defaultValue = "metrics")
    private String mongodbCollection;
    
    // Service components
    private AtlasApiClient apiClient;
    private MetricsStorage metricsStorage;
    private MetricsCollector metricsCollector;
    private MetricsProcessor metricsProcessor;
    
    @Override
    public Integer call() throws Exception {
        // Initialize the API client (with reduced debug level)
        this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, 0);
        
        // Initialize metrics storage if enabled
        if (storeMetrics) {
            if (mongodbUri == null || mongodbUri.isEmpty()) {
                logger.error("MongoDB URI is required when --storeMetrics is enabled");
                return 1;
            }
            
            try {
                logger.info("Initializing metrics storage: database={}, collection={}", 
                        mongodbDatabase, mongodbCollection);
                this.metricsStorage = new MetricsStorage(mongodbUri, mongodbDatabase, mongodbCollection);
            } catch (Exception e) {
                logger.error("Failed to initialize metrics storage: {}", e.getMessage(), e);
                return 1;
            }
        }
        
        // Initialize the metrics collector with storage option
        this.metricsCollector = new MetricsCollector(apiClient, metrics, period, granularity, 
                metricsStorage, storeMetrics, collectOnly);
        
        // Collect metrics for all projects
        Map<String, ProjectMetricsResult> results = metricsCollector.collectMetrics(includeProjectNames);
        
        // If in collect-only mode, we're done
        if (collectOnly) {
            logger.info("Collection complete. Skipping processing and visualization in collect-only mode.");
            
            // Close the metrics storage if used
            if (metricsStorage != null) {
                metricsStorage.close();
            }
            
            return 0;
        }
        
        // Initialize the metrics processor with pattern analysis option if we're not in collect-only mode
        this.metricsProcessor = new MetricsProcessor(apiClient, metrics, period, granularity, analyzePatterns);
        
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
        
        // Generate visualizations if pattern analysis is enabled
        if (analyzePatterns) {
            // Initialize PatternVisualReporter with dark mode option
            PatternVisualReporter reporter = new PatternVisualReporter(apiClient, chartOutputDir, chartWidth, chartHeight, darkMode);
            
            // Generate combined charts for each project and metric
            for (ProjectMetricsResult projectResult : results.values()) {
                logger.info("Generating charts for project: {}", projectResult.getProjectName());
                for (String metric : projectResult.getMetrics()) {
                    reporter.generateCombinedMetricChart(projectResult, metric, period, granularity);
                }
            }
            
            // Generate HTML index if requested
            if (generateHtmlIndex) {
                logger.info("Generating HTML index");
                reporter.createHtmlIndex(results);
            }
            
            logger.info("Visualizations generated in directory: {} (chart dimensions: {}x{}, dark mode: {})", 
                    chartOutputDir, chartWidth, chartHeight, darkMode ? "enabled" : "disabled");
        }
        
        // Close the metrics storage if used
        if (metricsStorage != null) {
            metricsStorage.close();
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