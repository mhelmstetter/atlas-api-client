package com.mongodb.atlas.autoscaler;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.AtlasApiClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

/**
 * Main entry point for the MongoDB Atlas Autoscaler daemon
 */
@Command(name = "AutoscalerMain", mixinStandardHelpOptions = true, 
    description = "MongoDB Atlas Autoscaler daemon with configurable scaling rules",
    defaultValueProvider = PropertiesDefaultProvider.class)
public class AutoscalerMain implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(AutoscalerMain.class);
    
    @Option(names = { "--apiPublicKey" }, description = "Atlas API public key", required = true)
    private String apiPublicKey;
    
    @Option(names = { "--apiPrivateKey" }, description = "Atlas API private key", required = true)
    private String apiPrivateKey;
    
    @Option(names = { "--includeProjectNames" }, description = "Project names to monitor for autoscaling", 
            required = true, split = ",")
    private Set<String> includeProjectNames;
    
    @Option(names = { "--config", "-c" }, description = "Config file", 
            required = false, defaultValue = "autoscaler.properties")
    private File configFile;
    
    @Option(names = { "--monitoringInterval" }, description = "Monitoring interval in seconds", 
            required = false, defaultValue = "300")
    private int monitoringIntervalSeconds;
    
    @Option(names = { "--dryRun" }, description = "Enable dry run mode (no actual scaling)", 
            required = false, defaultValue = "false")
    private boolean dryRun;
    
    // CPU scaling rule configuration
    @Option(names = { "--cpuScaleUpThreshold" }, description = "CPU threshold for scaling up (%)", 
            required = false, defaultValue = "90.0")
    private double cpuScaleUpThreshold;
    
    @Option(names = { "--cpuScaleUpDuration" }, description = "Duration CPU must exceed threshold (minutes)", 
            required = false, defaultValue = "5")
    private int cpuScaleUpDurationMinutes;
    
    @Option(names = { "--cpuScaleDownThreshold" }, description = "CPU threshold for scaling down (%)", 
            required = false, defaultValue = "30.0")
    private double cpuScaleDownThreshold;
    
    @Option(names = { "--cpuScaleDownDuration" }, description = "Duration CPU must be below threshold (minutes)", 
            required = false, defaultValue = "30")
    private int cpuScaleDownDurationMinutes;
    
    @Option(names = { "--scaleCooldown" }, description = "Cooldown period between scaling actions (minutes)", 
            required = false, defaultValue = "30")
    private int scaleCooldownMinutes;
    
    @Option(names = { "--enableCpuScaleUp" }, description = "Enable CPU-based scale up", 
            required = false, defaultValue = "true")
    private boolean enableCpuScaleUp;
    
    @Option(names = { "--enableCpuScaleDown" }, description = "Enable CPU-based scale down", 
            required = false, defaultValue = "false")
    private boolean enableCpuScaleDown;
    
    // Memory scaling rule configuration
    @Option(names = { "--memoryScaleUpThreshold" }, description = "Memory usage threshold for scaling up (%)", 
            required = false, defaultValue = "85.0")
    private double memoryScaleUpThreshold;
    
    @Option(names = { "--memoryScaleUpDuration" }, description = "Duration memory must exceed threshold (minutes)", 
            required = false, defaultValue = "10")
    private int memoryScaleUpDurationMinutes;
    
    @Option(names = { "--enableMemoryScaleUp" }, description = "Enable memory-based scale up", 
            required = false, defaultValue = "false")
    private boolean enableMemoryScaleUp;
    
    // Shard scaling configuration
    @Option(names = { "--scaleAllShardsInUnison" }, description = "Scale all shards together", 
            required = false, defaultValue = "true")
    private boolean scaleAllShardsInUnison;
    
    @Option(names = { "--allowPerShardScaling" }, description = "Allow per-shard scaling rules", 
            required = false, defaultValue = "false")
    private boolean allowPerShardScaling;
    
    @Option(names = { "--defaultNodeType" }, description = "Default node type to scale (ELECTABLE, ANALYTICS, READ_ONLY)", 
            required = false, defaultValue = "ELECTABLE")
    private String defaultNodeType;
    
    private Autoscaler autoscaler;
    
    @Override
    public Integer call() throws Exception {
        logger.info("Starting MongoDB Atlas Autoscaler...");
        
        // Validate configuration
        if (includeProjectNames.isEmpty()) {
            logger.error("At least one project name must be specified");
            return 1;
        }
        
        if (dryRun) {
            logger.warn("DRY RUN MODE ENABLED - No actual scaling will occur");
        }
        
        try {
            // Build scaling rules based on configuration
            List<ScalingRule> scalingRules = buildScalingRules();
            
            if (scalingRules.isEmpty()) {
                logger.error("No scaling rules are enabled. Enable at least one scaling rule.");
                return 1;
            }
            
            logger.info("Configured {} scaling rules:", scalingRules.size());
            for (ScalingRule rule : scalingRules) {
                logger.info("  - {}: {} {} {} for {} (cooldown: {})", 
                        rule.getName(),
                        rule.getMetricName(),
                        rule.getCondition(),
                        rule.getThreshold(),
                        rule.getDuration(),
                        rule.getCooldownPeriod());
            }
            
            // Create autoscaler configuration
            AutoscalerConfig config = new AutoscalerConfig(
                    includeProjectNames,
                    scalingRules,
                    monitoringIntervalSeconds,
                    dryRun,
                    scaleAllShardsInUnison,
                    allowPerShardScaling
            );
            
            // Create the autoscaler with credentials
            autoscaler = new Autoscaler(config, apiPublicKey, apiPrivateKey);
            
            // Add shutdown hook to gracefully stop the autoscaler
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping autoscaler...");
                if (autoscaler != null) {
                    autoscaler.stop();
                }
            }));
            
            // Start the autoscaler daemon
            autoscaler.start();
            
            logger.info("Autoscaler started successfully. Monitoring {} projects with {} second intervals.",
                    includeProjectNames.size(), monitoringIntervalSeconds);
            
            // Keep the main thread alive while the autoscaler runs
            try {
                while (autoscaler.getStatus().isRunning()) {
                    Thread.sleep(10000); // Check every 10 seconds
                    
                    // Log status periodically
                    Autoscaler.AutoscalerStatus status = autoscaler.getStatus();
                    logger.debug("Autoscaler status: running={}, clusters={}, recent_actions={}", 
                            status.isRunning(), status.getClustersMonitored(), status.getRecentScaleActions());
                }
            } catch (InterruptedException e) {
                logger.info("Main thread interrupted, stopping autoscaler...");
                Thread.currentThread().interrupt();
            }
            
            return 0;
            
        } catch (Exception e) {
            logger.error("Error starting autoscaler: {}", e.getMessage(), e);
            return 1;
        }
    }
    
    /**
     * Build scaling rules based on configuration
     */
    private List<ScalingRule> buildScalingRules() {
        List<ScalingRule> rules = new ArrayList<>();
        
        Duration cooldown = Duration.ofMinutes(scaleCooldownMinutes);
        ClusterTierInfo.NodeType nodeType;
        
        // Parse node type
        try {
            nodeType = ClusterTierInfo.NodeType.valueOf(defaultNodeType.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid node type '{}', using ELECTABLE", defaultNodeType);
            nodeType = ClusterTierInfo.NodeType.ELECTABLE;
        }
        
        // CPU-based scaling rules
        if (enableCpuScaleUp) {
            ScalingRule cpuScaleUpRule = new ScalingRule(
                    "CPU Scale Up",
                    "SYSTEM_NORMALIZED_CPU_USER",
                    RuleCondition.GREATER_THAN,
                    cpuScaleUpThreshold,
                    Duration.ofMinutes(cpuScaleUpDurationMinutes),
                    ClusterTierInfo.ScaleDirection.UP,
                    cooldown,
                    nodeType,
                    scaleAllShardsInUnison,
                    0 // First shard if not scaling all
            );
            rules.add(cpuScaleUpRule);
        }
        
        if (enableCpuScaleDown) {
            ScalingRule cpuScaleDownRule = new ScalingRule(
                    "CPU Scale Down",
                    "SYSTEM_NORMALIZED_CPU_USER", 
                    RuleCondition.LESS_THAN,
                    cpuScaleDownThreshold,
                    Duration.ofMinutes(cpuScaleDownDurationMinutes),
                    ClusterTierInfo.ScaleDirection.DOWN,
                    cooldown,
                    nodeType,
                    scaleAllShardsInUnison,
                    0 // First shard if not scaling all
            );
            rules.add(cpuScaleDownRule);
        }
        
        // Memory-based scaling rules
        if (enableMemoryScaleUp) {
            ScalingRule memoryScaleUpRule = new ScalingRule(
                    "Memory Scale Up",
                    "SYSTEM_MEMORY_USED",
                    RuleCondition.GREATER_THAN,
                    memoryScaleUpThreshold,
                    Duration.ofMinutes(memoryScaleUpDurationMinutes),
                    ClusterTierInfo.ScaleDirection.UP,
                    cooldown,
                    nodeType,
                    scaleAllShardsInUnison,
                    0 // First shard if not scaling all
            );
            rules.add(memoryScaleUpRule);
        }
        
        return rules;
    }
    
    public static void main(String[] args) {
        AutoscalerMain main = new AutoscalerMain();
        Logger logger = LoggerFactory.getLogger(AutoscalerMain.class);

        int exitCode = 0;
        try {
            CommandLine cmd = new CommandLine(main);
            ParseResult parseResult = cmd.parseArgs(args);

            File defaultsFile;
            if (main.configFile != null) {
                defaultsFile = main.configFile;
            } else {
                defaultsFile = new File("autoscaler.properties");
            }

            if (defaultsFile.exists()) {
                logger.info("Loading configuration from {}", defaultsFile.getAbsolutePath());
                cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
            } else {
                logger.warn("Configuration file {} not found, using command line options and defaults", 
                        defaultsFile.getAbsolutePath());
            }
            parseResult = cmd.parseArgs(args);

            if (!CommandLine.printHelpIfRequested(parseResult)) {
                logger.info("Starting MongoDB Atlas Autoscaler daemon");
                exitCode = main.call();
                logger.info("MongoDB Atlas Autoscaler daemon completed with exit code {}", exitCode);
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