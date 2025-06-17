package com.mongodb.atlas.api.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.mongodb.atlas.api.cli.commands.*;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Main CLI application for MongoDB Atlas API Client
 * 
 * Provides comprehensive command-line access to all Atlas API functionality:
 * - Cluster management (regular and flex clusters)
 * - API key management 
 * - Database user management
 * - Network access configuration
 * - Backup and restore operations
 * - Metrics and monitoring
 * - Project management
 * - Interactive mode for guided operations
 * 
 * Usage:
 *   atlas-cli --help                    # Show all commands
 *   atlas-cli clusters list             # List clusters
 *   atlas-cli api-keys create           # Create API key
 *   atlas-cli interactive               # Interactive mode
 *   atlas-cli metrics analyze           # Legacy metrics analyzer
 */
@Command(
    name = "atlas-cli",
    description = "MongoDB Atlas API Client - Comprehensive command-line interface for Atlas operations",
    mixinStandardHelpOptions = true,
    version = "Atlas CLI 1.0.0",
    subcommands = {
        // Core management commands
        ClustersCommand.class,
        FlexClustersCommand.class,
        ApiKeysCommand.class,
        DatabaseUsersCommand.class,
        NetworkAccessCommand.class,
        BackupsCommand.class,
        ProjectsCommand.class,
        
        // Monitoring and analysis
        MetricsCommand.class,
        LogsCommand.class,
        
        // Utilities and legacy
        InteractiveCommand.class,
        ConfigCommand.class,
        
        // Legacy compatibility commands
        LegacyMetricsAnalyzerCommand.class,
        LegacyClusterLauncherCommand.class,
        
        CommandLine.HelpCommand.class
    }
)
public class AtlasCliMain implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, 
            description = "Path to configuration file (default: atlas-client.properties)")
    private String configFile;

    @Option(names = {"-v", "--verbose"}, 
            description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--api-public-key"}, 
            description = "Atlas API public key (overrides config)")
    private String apiPublicKey;

    @Option(names = {"--api-private-key"}, 
            description = "Atlas API private key (overrides config)")
    private String apiPrivateKey;

    @Option(names = {"--project-id"}, 
            description = "Atlas project ID (overrides config)")
    private String projectId;

    @Option(names = {"--org-id"}, 
            description = "Atlas organization ID (overrides config)")
    private String orgId;

    @Option(names = {"--format"}, 
            description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)",
            defaultValue = "TABLE")
    private OutputFormat format;

    @Option(names = {"--interactive"}, 
            description = "Start in interactive mode")
    private boolean interactive;

    private String[] args;

    public static void main(String[] args) {
        AtlasCliMain main = new AtlasCliMain();
        main.args = args;
        int exitCode = new CommandLine(main).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (interactive || (args != null && args.length == 0)) {
            return runInteractiveMode();
        } else {
            // Show help when no subcommand is specified
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    private Integer runInteractiveMode() {
        System.out.println("🍃 MongoDB Atlas CLI - Interactive Mode");
        System.out.println("=====================================");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println();
            System.out.println("Available Commands:");
            System.out.println("  1. Cluster Management");
            System.out.println("  2. API Key Management");
            System.out.println("  3. Database Users");
            System.out.println("  4. Network Access");
            System.out.println("  5. Backups & Restore");
            System.out.println("  6. Projects");
            System.out.println("  7. Metrics & Monitoring");
            System.out.println("  8. Logs");
            System.out.println("  9. Configuration");
            System.out.println("  0. Exit");
            System.out.print("\nSelect an option [0-9]: ");
            
            String choice = scanner.nextLine().trim();
            
            try {
                switch (choice) {
                    case "1":
                        runSubCommand("clusters", scanner);
                        break;
                    case "2":
                        runSubCommand("api-keys", scanner);
                        break;
                    case "3":
                        runSubCommand("database-users", scanner);
                        break;
                    case "4":
                        runSubCommand("network-access", scanner);
                        break;
                    case "5":
                        runSubCommand("backups", scanner);
                        break;
                    case "6":
                        runSubCommand("projects", scanner);
                        break;
                    case "7":
                        runSubCommand("metrics", scanner);
                        break;
                    case "8":
                        runSubCommand("logs", scanner);
                        break;
                    case "9":
                        runSubCommand("config", scanner);
                        break;
                    case "0":
                    case "exit":
                    case "quit":
                        System.out.println("Goodbye! 👋");
                        return 0;
                    default:
                        System.out.println("❌ Invalid option. Please select 0-9.");
                }
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void runSubCommand(String command, Scanner scanner) {
        System.out.println("\n📋 " + command.toUpperCase() + " - Available Operations:");
        
        // This will be implemented by each command to show its interactive menu
        String[] args = {command, "--interactive"};
        new CommandLine(new AtlasCliMain()).execute(args);
    }

    public enum OutputFormat {
        TABLE, JSON, CSV, YAML
    }

    // Global configuration access
    public static class GlobalConfig {
        private static String configFile;
        private static boolean verbose;
        private static String apiPublicKey;
        private static String apiPrivateKey;
        private static String projectId;
        private static String orgId;
        private static OutputFormat format = OutputFormat.TABLE;

        public static AtlasTestConfig getAtlasConfig() {
            AtlasTestConfig config = AtlasTestConfig.getInstance();
            
            // Override with command-line values if provided
            if (apiPublicKey != null) {
                System.setProperty(AtlasTestConfig.API_PUBLIC_KEY, apiPublicKey);
            }
            if (apiPrivateKey != null) {
                System.setProperty(AtlasTestConfig.API_PRIVATE_KEY, apiPrivateKey);
            }
            if (projectId != null) {
                System.setProperty(AtlasTestConfig.TEST_PROJECT_ID, projectId);
            }
            if (orgId != null) {
                System.setProperty(AtlasTestConfig.TEST_ORG_ID, orgId);
            }
            
            return config;
        }

        // Getters and setters
        public static String getConfigFile() { return configFile; }
        public static void setConfigFile(String configFile) { GlobalConfig.configFile = configFile; }
        
        public static boolean isVerbose() { return verbose; }
        public static void setVerbose(boolean verbose) { GlobalConfig.verbose = verbose; }
        
        public static String getApiPublicKey() { return apiPublicKey; }
        public static void setApiPublicKey(String apiPublicKey) { GlobalConfig.apiPublicKey = apiPublicKey; }
        
        public static String getApiPrivateKey() { return apiPrivateKey; }
        public static void setApiPrivateKey(String apiPrivateKey) { GlobalConfig.apiPrivateKey = apiPrivateKey; }
        
        public static String getProjectId() { return projectId; }
        public static void setProjectId(String projectId) { GlobalConfig.projectId = projectId; }
        
        public static String getOrgId() { return orgId; }
        public static void setOrgId(String orgId) { GlobalConfig.orgId = orgId; }
        
        public static OutputFormat getFormat() { return format; }
        public static void setFormat(OutputFormat format) { GlobalConfig.format = format; }
    }
}