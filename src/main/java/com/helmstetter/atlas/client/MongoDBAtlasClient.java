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
    
    // Service components
    private AtlasApiClient apiClient;
    private MetricsProcessor metricsProcessor;
    
    @Override
    public Integer call() throws Exception {
        // Initialize the API client
        this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey);
        
        // Initialize the metrics processor
        this.metricsProcessor = new MetricsProcessor(apiClient, metrics, period, granularity);
        
        // Process metrics for all projects
        Map<String, ProjectMetricsResult> results = metricsProcessor.processProjectMetrics(includeProjectNames);
        
        // Export to CSV if filename was provided
        if (exportCsvFilename != null && !exportCsvFilename.isEmpty()) {
            CsvExporter exporter = new CsvExporter(metrics);
            exporter.exportProjectMetricsToCSV(results, exportCsvFilename);
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