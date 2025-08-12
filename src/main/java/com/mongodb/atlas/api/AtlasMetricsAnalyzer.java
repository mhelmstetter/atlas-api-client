package com.mongodb.atlas.api;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.charts.ApiVisualReporter;
import com.mongodb.atlas.api.charts.StorageVisualReporter;
import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.csv.CsvExporter;
import com.mongodb.atlas.api.csv.DetailedMetricsCsvExporter;
import com.mongodb.atlas.api.metrics.MetricsCollector;
import com.mongodb.atlas.api.metrics.MetricsReporter;
import com.mongodb.atlas.api.metrics.MetricsStorage;
import com.mongodb.atlas.api.metrics.ProjectMetricsResult;
import com.mongodb.atlas.api.util.MetricsUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

/**
 * Main entry point for the MongoDB Atlas metrics analyzer
 * Enhanced with metric storage, collection, and reporting from stored data
 */
@Command(name = "AtlasMetricsAnalyzer", mixinStandardHelpOptions = true, 
    description = "MongoDB Atlas metrics analyzer for usage analysis and reporting", 
    defaultValueProvider = PropertiesDefaultProvider.class)
public class AtlasMetricsAnalyzer implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasMetricsAnalyzer.class);
    
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
    
    @Option(names = { "--period" }, description = "time period for data collection from API (ISO 8601 duration format)", required = false, 
        defaultValue = "PT48H")
    private String period;
    
    @Option(names = { "--reportPeriod" }, description = "time period for reporting from stored data (ISO 8601 duration format). If not specified, uses all available data.", required = false)
    private String reportPeriod;
    
    @Option(names = { "--granularity" }, description = "measurement granularity (ISO 8601 duration format)", required = false, 
        defaultValue = "PT1M")
    private String granularity;
    
    // CHANGED: exportCsvFilename to exportCsv (boolean)
    @Option(names = { "--exportCsv" }, description = "Export project summary to CSV file with default name", required = false, defaultValue = "false")
    private boolean exportCsv;
    
    
    // NEW: Export detailed metrics CSV for each project
    @Option(names = { "--exportDetailedMetricsCsv" }, description = "Export detailed metrics CSV for each project", required = false, defaultValue = "false")
    private boolean exportDetailedMetricsCsv;
    
    @Option(names = { "--chartOutputDir" }, description = "Directory for charts", required = false, defaultValue = "charts")
    private String chartOutputDir;
    
    @Option(names = { "--generateCharts" }, description = "Generate visual charts from metrics data", required = false, defaultValue = "false")
    private boolean generateCharts;
    
    
    @Option(names = { "--chartWidth" }, description = "Width of generated charts in pixels", required = false, defaultValue = "600")
    private int chartWidth;
    
    @Option(names = { "--chartHeight" }, description = "Height of generated charts in pixels", required = false, defaultValue = "300")
    private int chartHeight;
    
    @Option(names = { "--darkMode" }, description = "Enable dark mode for charts and HTML", required = false, defaultValue = "true")
    private boolean darkMode;
    
    @Option(names = { "--debug" }, description = "Enable debug logging for troubleshooting", required = false, defaultValue = "false")
    private boolean debug;
    
    @Option(names = { "--interactive" }, description = "Enable interactive mode for long-running operations", required = false, defaultValue = "false")
    private boolean interactive;
    
    // Storage options (automatically enabled when mongodbUri is provided)
    
    @Option(names = { "--collect" }, description = "Only collect and store metrics without processing or reporting", required = false, defaultValue = "false")
    private boolean collect;
    
    @Option(names = { "--mongodbUri" }, description = "MongoDB connection URI for metrics storage", required = false)
    private String mongodbUri;
    
    @Option(names = { "--mongodbDatabase" }, description = "MongoDB database name for metrics storage", required = false, defaultValue = "atlas_metrics")
    private String mongodbDatabase;
    
    @Option(names = { "--mongodbCollection" }, description = "MongoDB collection name for metrics storage", required = false, defaultValue = "metrics")
    private String mongodbCollection;
    
    @Option(names = { "--reportFromStorage" }, description = "Generate report from stored data instead of API", required = false, defaultValue = "false")
    private boolean reportFromStorage;
    
    @Option(names = { "--dataAvailabilityOnly" }, description = "Only generate data availability report without collecting new data", required = false, defaultValue = "false")
    private boolean dataAvailabilityOnly;
    
    
    // Service components
    private AtlasApiClient apiClient;
    private MetricsStorage metricsStorage;
    private MetricsCollector metricsCollector;
    private MetricsReporter metricsReporter;
    
    @Override
    public Integer call() throws Exception {
        
        // Validate options
        if ((reportFromStorage || dataAvailabilityOnly) && (mongodbUri == null || mongodbUri.isEmpty())) {
            logger.error("MongoDB URI is required when --reportFromStorage or --dataAvailabilityOnly is enabled");
            return 1;
        }
        
        if (!reportFromStorage && !dataAvailabilityOnly && (apiPublicKey == null || apiPrivateKey == null)) {
            logger.error("Atlas API credentials are required when not using --reportFromStorage or --dataAvailabilityOnly");
            return 1;
        }
        
        // Initialize metrics storage if needed
        boolean enableStorage = (mongodbUri != null && !mongodbUri.isEmpty());
        if (enableStorage || reportFromStorage || dataAvailabilityOnly) {
            if (mongodbUri == null || mongodbUri.isEmpty()) {
                logger.error("MongoDB URI is required for storage operations");
                return 1;
            }
            
            try {
                logger.info("Initializing metrics storage: database={}, collection={}", 
                        mongodbDatabase, mongodbCollection);
                this.metricsStorage = new MetricsStorage(mongodbUri, mongodbDatabase, mongodbCollection, interactive);
            } catch (Exception e) {
                logger.error("Failed to initialize metrics storage: {}", e.getMessage(), e);
                return 1;
            }
        }
        
        // Initialize API client if not using storage-only mode
        if (!reportFromStorage && !dataAvailabilityOnly) {
            int debugLevel = debug ? 1 : 0;
            this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, debugLevel);
        }
        
        Map<String, ProjectMetricsResult> results = null;
        
        
        // Data availability only mode
        if (dataAvailabilityOnly) {
            logger.info("Running data availability report only...");
            
            if (metricsStorage == null) {
                logger.error("Storage not initialized - cannot generate data availability report");
                return 1;
            }
            
            this.metricsReporter = new MetricsReporter(metricsStorage, metrics, false);
            
            logger.info("Generating data availability report...");
            metricsReporter.generateDataAvailabilityReport(includeProjectNames);
            
            logger.info("Data availability report complete");
            return 0;
        }
        
        // Report from storage mode
        else if (reportFromStorage) {
            logger.info("Generating report from stored data...");
            
            this.metricsReporter = new MetricsReporter(metricsStorage, metrics, false);
            
            // Use reportPeriod if specified, otherwise use all available data (null)
            String effectiveReportPeriod = reportPeriod != null ? reportPeriod : null;
            if (effectiveReportPeriod != null) {
                logger.info("Using specified report period: {}", effectiveReportPeriod);
            } else {
                logger.info("Using all available data in storage for reporting");
            }
            
            results = metricsReporter.generateProjectMetricsReport(includeProjectNames, effectiveReportPeriod);
            
            
            // Generate charts from stored data if requested
            if (generateCharts) {
                logger.info("Generating visualizations from stored data...");
                StorageVisualReporter storageReporter = new StorageVisualReporter(
                        metricsStorage, chartOutputDir, chartWidth, chartHeight, darkMode);
                
                // Generate charts for each project and metric
                for (ProjectMetricsResult projectResult : results.values()) {
                    logger.info("Generating charts from storage for project: {}", projectResult.getProjectName());
                    for (String metric : projectResult.getMetrics()) {
                        if (projectResult.hasMetricData(metric)) {
                            storageReporter.generateCombinedMetricChart(projectResult, metric, effectiveReportPeriod, null);
                        }
                    }
                }
                
                // Generate HTML index
                logger.info("Generating HTML index from stored data");
                storageReporter.createHtmlIndex(results);
                
                logger.info("Visualizations from stored data generated in directory: {}", chartOutputDir);
            }
            
            // Generate data availability report when storage is available
            if (metricsStorage != null) {
                logger.info("Generating data availability report...");
                metricsReporter.generateDataAvailabilityReport(includeProjectNames);
            }
        } 
        // API-based collection and processing mode
        else {
            // Validate period and granularity against Atlas retention limits
            String validationError = MetricsUtils.validateAtlasRetentionLimits(period, granularity);
            boolean shouldUseStorage = MetricsUtils.shouldUseStorageForPeriod(period, granularity, enableStorage);
            
            if (validationError != null) {
                if (shouldUseStorage) {
                    logger.warn("⚠️  Atlas API limitation: {}", validationError);
                    logger.info("🔄 Switching to storage-based reporting for this period/granularity combination");
                    
                    // Switch to storage-based reporting
                    if (metricsStorage == null) {
                        logger.error("❌ Cannot use storage-based reporting: MongoDB URI not provided");
                        logger.error("💡 To analyze data beyond Atlas retention limits, provide --mongodbUri");
                        logger.error("💡 Or adjust to compatible period/granularity:");
                        logger.error("   • For PT1M granularity: Use period ≤ PT48H (48 hours)");
                        logger.error("   • For PT1H granularity: Use period ≤ PT1512H (63 days)");  
                        logger.error("   • For P1D granularity: Any period supported");
                        logger.error("   • Or try: --granularity={}", MetricsUtils.getOptimalGranularityForPeriod(period));
                        return 1;
                    }
                    
                    // Use storage-based reporting instead
                    logger.info("Generating report from stored data...");
                    this.metricsReporter = new MetricsReporter(metricsStorage, metrics, false);
                    results = metricsReporter.generateProjectMetricsReport(includeProjectNames, period);
                } else {
                    logger.error("❌ {}", validationError);
                    logger.error("💡 Use compatible period/granularity combinations:");
                    logger.error("   • For PT1M granularity: Use period ≤ PT48H (48 hours)");
                    logger.error("   • For PT1H granularity: Use period ≤ PT1512H (63 days)");
                    logger.error("   • For P1D granularity: Any period supported");
                    logger.error("   • Or try: --granularity={}", MetricsUtils.getOptimalGranularityForPeriod(period));
                    return 1;
                }
            } else {
                // Initialize the metrics collector with storage option
                this.metricsCollector = new MetricsCollector(apiClient, metrics, period, granularity, 
                        metricsStorage, enableStorage, collect);
                
                // Collect metrics for all projects
                results = metricsCollector.collectMetrics(includeProjectNames);
            }
            
            // If in collect-only mode, generate data availability report if storage is enabled, then exit
            if (collect) {
                logger.info("Collection complete. Skipping processing and reporting in collect-only mode.");
                
                // Generate data availability report when storage is available
                if (metricsStorage != null) {
                    logger.info("Generating data availability report...");
                    this.metricsReporter = new MetricsReporter(metricsStorage, metrics, false);
                    metricsReporter.generateDataAvailabilityReport(includeProjectNames);
                }
                
                // Close the metrics storage if used
                if (metricsStorage != null) {
                    metricsStorage.close();
                }
                
                return 0;
            }
            
            
            // Generate charts if requested
            if (generateCharts) {
                // Check if we used storage-based reporting (indicated by fake project IDs)
                boolean usedStorageReporting = results.values().stream()
                        .anyMatch(result -> "from-storage".equals(result.getProjectId()));
                
                if (usedStorageReporting) {
                    logger.info("Generating visualizations from stored data...");
                    StorageVisualReporter storageReporter = new StorageVisualReporter(
                            metricsStorage, chartOutputDir, chartWidth, chartHeight, darkMode);
                    
                    // Generate charts for each project and metric from storage
                    for (ProjectMetricsResult projectResult : results.values()) {
                        logger.info("Generating charts from storage for project: {}", projectResult.getProjectName());
                        for (String metric : projectResult.getMetrics()) {
                            if (projectResult.hasMetricData(metric)) {
                                storageReporter.generateCombinedMetricChart(projectResult, metric, period, null);
                            }
                        }
                    }
                } else {
                    logger.info("Generating visualizations from API data...");
                    ApiVisualReporter reporter = new ApiVisualReporter(apiClient, chartOutputDir, chartWidth, chartHeight, darkMode);
                    
                    // Generate combined charts for each project and metric
                    for (ProjectMetricsResult projectResult : results.values()) {
                        logger.info("Generating charts for project: {}", projectResult.getProjectName());
                        for (String metric : projectResult.getMetrics()) {
                            reporter.generateCombinedMetricChart(projectResult, metric, period, granularity);
                        }
                    }
                }
                
                // Generate HTML index
                logger.info("Generating HTML index");
                if (usedStorageReporting) {
                    StorageVisualReporter storageReporter = new StorageVisualReporter(
                            metricsStorage, chartOutputDir, chartWidth, chartHeight, darkMode);
                    storageReporter.createHtmlIndex(results);
                } else {
                    ApiVisualReporter reporter = new ApiVisualReporter(apiClient, chartOutputDir, chartWidth, chartHeight, darkMode);
                    reporter.createHtmlIndex(results);
                }
                
                logger.info("Visualizations generated in directory: {} (chart dimensions: {}x{}, dark mode: {})", 
                        chartOutputDir, chartWidth, chartHeight, darkMode ? "enabled" : "disabled");
            }
            
            // Generate data availability report when storage is available
            if (metricsStorage != null) {
                logger.info("Generating data availability report...");
                this.metricsReporter = new MetricsReporter(metricsStorage, metrics, false);
                metricsReporter.generateDataAvailabilityReport(includeProjectNames);
            }
        }
        
        // CHANGED: Export summary CSV with default filename
        if (results != null && exportCsv) {
            String defaultSummaryFilename = "atlas-metrics-summary.csv";
            CsvExporter exporter = new CsvExporter(metrics, false);
            exporter.exportProjectMetricsToCSV(results, defaultSummaryFilename);
            logger.info("Project summary exported to: {}", defaultSummaryFilename);
        }
        
        // NEW: Export detailed metrics CSV for each project
        if (results != null && exportDetailedMetricsCsv) {
            DetailedMetricsCsvExporter detailedExporter = new DetailedMetricsCsvExporter(
                    metricsStorage != null ? metricsStorage : null, 
                    apiClient, 
                    metrics, 
                    reportFromStorage ? (reportPeriod != null ? reportPeriod : null) : period,  // Use appropriate period
                    granularity);
            
            for (ProjectMetricsResult projectResult : results.values()) {
                String projectFilename = "atlas-metrics-detailed-" + 
                    projectResult.getProjectName().replaceAll("[^a-zA-Z0-9]", "_") + ".csv";
                
                try {
                    if (reportFromStorage && metricsStorage != null) {
                        // Export from storage
                        detailedExporter.exportProjectDetailedMetricsFromStorage(
                            projectResult.getProjectName(), projectFilename);
                    } else if (!reportFromStorage && apiClient != null) {
                        // Export from API
                        detailedExporter.exportProjectDetailedMetricsFromApi(
                            projectResult.getProjectName(), projectResult.getProjectId(), projectFilename);
                    }
                    
                    logger.info("Detailed metrics for project '{}' exported to: {}", 
                        projectResult.getProjectName(), projectFilename);
                } catch (Exception e) {
                    logger.error("Failed to export detailed metrics for project '{}': {}", 
                        projectResult.getProjectName(), e.getMessage());
                }
            }
        }
        
        // Close the metrics storage if used
        if (metricsStorage != null) {
            metricsStorage.close();
        }
        
        return 0;
    }
    
    public static void main(String[] args) {
        AtlasMetricsAnalyzer client = new AtlasMetricsAnalyzer();
        Logger logger = LoggerFactory.getLogger(AtlasMetricsAnalyzer.class);

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