package com.mongodb.atlas.api.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

import com.mongodb.atlas.api.cli.commands.*;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *   atlas-cli alerts list               # List alerts
 */
@Command(
    name = "atlas-cli",
    description = "MongoDB Atlas API Client - Comprehensive command-line interface for Atlas operations",
    mixinStandardHelpOptions = true,
    version = "Atlas CLI 1.0.0",
    defaultValueProvider = PropertiesDefaultProvider.class,
    subcommands = {
        AlertConfigsCommand.class,
        AlertsCommand.class,
        ApiKeysCommand.class,
        BackupsCommand.class,
        ClustersCommand.class,
        ConfigCommand.class,
        DatabaseUsersCommand.class,
        FlexClustersCommand.class,
        InteractiveCommand.class,
        LogsCommand.class,
        MetricsCommand.class,
        NetworkAccessCommand.class,
        ProjectsCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class AtlasCliMain implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, 
            description = "Configuration file", 
            defaultValue = "atlas-client.properties")
    private File configFile;

    @Option(names = {"-v", "--verbose"}, 
            description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--apiPublicKey"}, 
            description = "Atlas API public key (overrides config)")
    private String apiPublicKey;

    @Option(names = {"--apiPrivateKey"}, 
            description = "Atlas API private key (overrides config)")
    private String apiPrivateKey;

    @Option(names = {"--projectIds"}, 
            description = "Atlas project IDs (comma-separated, overrides config)", split = ",")
    private List<String> projectIds;

    @Option(names = {"--includeProjectNames"}, 
            description = "Atlas project names to include (comma-separated, overrides config)", split = ",")
    private List<String> includeProjectNames;

    @Option(names = {"--orgId"}, 
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
        
        int exitCode = 0;
        try {
            CommandLine cmd = new CommandLine(main);
            ParseResult parseResult = cmd.parseArgs(args);

            File defaultsFile;
            if (main.configFile != null) {
                defaultsFile = main.configFile;
            } else {
                defaultsFile = new File("atlas-client.properties");
            }

            if (defaultsFile.exists()) {
                System.out.println("Loading configuration from " + defaultsFile.getAbsolutePath());
                cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
            } else {
                System.out.println("Configuration file " + defaultsFile.getAbsolutePath() + " not found");
            }
            parseResult = cmd.parseArgs(args);

            if (!CommandLine.printHelpIfRequested(parseResult)) {
                // Set global configuration values before executing subcommands
                GlobalConfig.setConfigFile(main.configFile);
                GlobalConfig.setVerbose(main.verbose);
                GlobalConfig.setApiPublicKey(main.apiPublicKey);
                GlobalConfig.setApiPrivateKey(main.apiPrivateKey);
                GlobalConfig.setProjectIds(main.projectIds);
                GlobalConfig.setIncludeProjectNames(main.includeProjectNames);
                GlobalConfig.setOrgId(main.orgId);
                GlobalConfig.setFormat(main.format);
                
                exitCode = cmd.execute(args);
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            exitCode = 1;
        }
        
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Set global configuration values from command line options
        GlobalConfig.setConfigFile(configFile);
        GlobalConfig.setVerbose(verbose);
        GlobalConfig.setApiPublicKey(apiPublicKey);
        GlobalConfig.setApiPrivateKey(apiPrivateKey);
        GlobalConfig.setProjectIds(projectIds);
        GlobalConfig.setIncludeProjectNames(includeProjectNames);
        GlobalConfig.setOrgId(orgId);
        GlobalConfig.setFormat(format);
        
        // Check if help was explicitly requested
        boolean helpRequested = false;
        if (args != null) {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg) || "help".equals(arg)) {
                    helpRequested = true;
                    break;
                }
            }
        }
        
        if (helpRequested) {
            // Show help when explicitly requested
            CommandLine.usage(this, System.out);
            return 0;
        } else {
            // Enter interactive mode when no subcommand is specified
            return runInteractiveMode();
        }
    }

    private Integer runInteractiveMode() {
        System.out.println("🍃 MongoDB Atlas CLI - Interactive Mode");
        System.out.println("=====================================");
        
        Scanner scanner = new Scanner(System.in);
        
        // Show current configuration
        showCurrentConfiguration();
        
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
            System.out.println("  9. Alerts");
            System.out.println(" 10. Alert Configurations");
            System.out.println(" 11. Configuration");
            System.out.println(" 12. Select Projects");
            System.out.println("  0. Exit");
            System.out.println("\nTip: You can type '0' or 'back' to go back, 'quit' or 'exit' to quit");
            System.out.print("\nSelect an option [0-12]: ");
            
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
                        runSubCommand("alerts", scanner);
                        break;
                    case "10":
                        runSubCommand("alert-configs", scanner);
                        break;
                    case "11":
                        runSubCommand("config", scanner);
                        break;
                    case "12":
                        selectProjects(scanner);
                        break;
                    case "0":
                    case "exit":
                    case "quit":
                    case "back":
                        System.out.println("Goodbye! 👋");
                        return 0;
                    default:
                        System.out.println("❌ Invalid option. Please select 0-12.");
                }
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showCurrentConfiguration() {
        System.out.println("\n📊 Current Configuration:");
        System.out.println("  API Key: " + (apiPublicKey != null ? "✅ Set" : "❌ Not set"));
        System.out.println("  Projects: " + getProjectDisplayString());
        System.out.println("  Config File: " + (configFile != null ? configFile.getPath() : "Default"));
        System.out.println("  Output Format: " + format);
    }
    
    private String getProjectDisplayString() {
        if (projectIds != null && !projectIds.isEmpty()) {
            return projectIds.size() + " project ID(s) selected";
        } else if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
            return includeProjectNames.size() + " project name(s): " + String.join(", ", includeProjectNames);
        } else {
            return "❌ None selected";
        }
    }
    
    private void selectProjects(Scanner scanner) {
        System.out.println("\n🎯 Project Selection");
        System.out.println("===================");
        
        try {
            // Get API credentials
            String publicKey = apiPublicKey != null ? apiPublicKey : System.getProperty("atlas.api.public.key");
            String privateKey = apiPrivateKey != null ? apiPrivateKey : System.getProperty("atlas.api.private.key");
            
            if (publicKey == null || privateKey == null) {
                System.out.println("❌ API credentials not configured. Please set them first.");
                return;
            }
            
            // Fetch available projects
            System.out.println("🔄 Fetching your Atlas projects...");
            AtlasApiBase apiBase = new AtlasApiBase(publicKey, privateKey);
            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
            List<Map<String, Object>> projects = projectsClient.getAllProjects();
            
            if (projects.isEmpty()) {
                System.out.println("❌ No projects found in your Atlas organization.");
                return;
            }
            
            // Display projects
            System.out.println("\n📋 Available Projects:");
            for (int i = 0; i < projects.size(); i++) {
                Map<String, Object> project = projects.get(i);
                String id = (String) project.get("id");
                String name = (String) project.get("name");
                System.out.printf("  %2d. %s (%s)\n", i + 1, name, id);
            }
            
            System.out.println("\nSelection Options:");
            System.out.println("  a. Select All Projects");
            System.out.println("  c. Clear Selection");
            System.out.println("  0. Back to Main Menu");
            System.out.print("\nEnter project numbers (comma-separated), option letter, or 0: ");
            
            String input = scanner.nextLine().trim();
            
            if ("0".equals(input)) {
                return;
            } else if ("a".equalsIgnoreCase(input)) {
                // Select all projects
                List<String> allProjectIds = new ArrayList<>();
                List<String> allProjectNames = new ArrayList<>();
                for (Map<String, Object> project : projects) {
                    allProjectIds.add((String) project.get("id"));
                    allProjectNames.add((String) project.get("name"));
                }
                GlobalConfig.setProjectIds(allProjectIds);
                GlobalConfig.setIncludeProjectNames(allProjectNames);
                System.out.println("✅ Selected all " + projects.size() + " projects.");
            } else if ("c".equalsIgnoreCase(input)) {
                // Clear selection
                GlobalConfig.setProjectIds(null);
                GlobalConfig.setIncludeProjectNames(null);
                System.out.println("✅ Project selection cleared.");
            } else {
                // Parse comma-separated numbers
                String[] indices = input.split(",");
                List<String> selectedProjectIds = new ArrayList<>();
                List<String> selectedProjectNames = new ArrayList<>();
                
                for (String indexStr : indices) {
                    try {
                        int index = Integer.parseInt(indexStr.trim()) - 1;
                        if (index >= 0 && index < projects.size()) {
                            Map<String, Object> project = projects.get(index);
                            selectedProjectIds.add((String) project.get("id"));
                            selectedProjectNames.add((String) project.get("name"));
                        } else {
                            System.out.println("❌ Invalid project number: " + (index + 1));
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("❌ Invalid input: " + indexStr);
                    }
                }
                
                if (!selectedProjectIds.isEmpty()) {
                    GlobalConfig.setProjectIds(selectedProjectIds);
                    GlobalConfig.setIncludeProjectNames(selectedProjectNames);
                    System.out.println("✅ Selected " + selectedProjectIds.size() + " project(s): " + 
                        String.join(", ", selectedProjectNames));
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error fetching projects: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private void runSubCommand(String command, Scanner scanner) {
        System.out.println("\n📋 " + command.toUpperCase() + " - Available Operations:");
        
        switch (command) {
            case "alerts":
                runAlertsSubCommand(scanner);
                break;
            case "clusters":
                runClustersSubCommand(scanner);
                break;
            case "projects":
                runProjectsSubCommand(scanner);
                break;
            default:
                // For other commands, show basic menu
                System.out.println("  1. List");
                System.out.println("  2. Get Details");
                System.out.println("  0. Back");
                System.out.print("\nSelect operation: ");
                String choice = scanner.nextLine().trim();
                
                if (!"0".equals(choice) && !"back".equalsIgnoreCase(choice)) {
                    System.out.println("\n" + "=".repeat(50));
                    String operation = "1".equals(choice) ? "list" : "get";
                    
                    // Prepare complete command with global options
                    List<String> fullArgs = new ArrayList<>();
                    if (configFile != null) {
                        fullArgs.add("--config");
                        fullArgs.add(configFile.getPath());
                    }
                    if (projectIds != null && !projectIds.isEmpty()) {
                        fullArgs.add("--projectIds");
                        fullArgs.add(String.join(",", projectIds));
                    } else if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
                        fullArgs.add("--includeProjectNames");
                        fullArgs.add(String.join(",", includeProjectNames));
                    }
                    fullArgs.add(command);
                    fullArgs.add(operation);
                    
                    new CommandLine(new AtlasCliMain()).execute(fullArgs.toArray(new String[0]));
                    System.out.println("\n" + "=".repeat(50));
                    System.out.print("Press Enter to continue...");
                    scanner.nextLine();
                }
        }
    }
    
    private void runAlertsSubCommand(Scanner scanner) {
        System.out.println("  1. List Alerts (Table)");
        System.out.println("  2. List Alerts (Terminal UI)");
        System.out.println("  3. List Open Alerts Only");
        System.out.println("  4. List Closed Alerts Only");
        System.out.println("  5. Get Alert Details");
        System.out.println("  6. Acknowledge Alert");
        System.out.println("  0. Back");
        System.out.print("\nSelect operation: ");
        
        String choice = scanner.nextLine().trim();
        List<String> args = new ArrayList<>();
        args.add("alerts");
        
        switch (choice) {
            case "1":
                // Ask for status filter
                args.add("list");
                addStatusFilter(scanner, args);
                break;
            case "2":
                // Terminal UI with optional status filter
                args.add("list");
                args.add("--ui");
                addStatusFilter(scanner, args);
                break;
            case "3":
                // Open alerts only
                args.add("list");
                args.add("--status");
                args.add("OPEN");
                break;
            case "4":
                // Closed alerts only
                args.add("list");
                args.add("--status");
                args.add("CLOSED");
                break;
            case "5":
                System.out.print("Enter project ID: ");
                String projectId = scanner.nextLine().trim();
                System.out.print("Enter alert ID: ");
                String alertId = scanner.nextLine().trim();
                args.add("get");
                args.add(projectId);
                args.add(alertId);
                break;
            case "6":
                System.out.print("Enter project ID: ");
                String ackProjectId = scanner.nextLine().trim();
                System.out.print("Enter alert ID: ");
                String ackAlertId = scanner.nextLine().trim();
                args.add("acknowledge");
                args.add(ackProjectId);
                args.add(ackAlertId);
                break;
            case "0":
                return;
            default:
                System.out.println("❌ Invalid option. Please select 0-6.");
                return;
        }
        
        if (!"0".equals(choice) && !"back".equalsIgnoreCase(choice)) {
            // Prepare complete command with project selection
            List<String> fullArgs = new ArrayList<>();
            
            // Add global options first (for the main CLI)
            if (configFile != null) {
                fullArgs.add("--config");
                fullArgs.add(configFile.getPath());
            }
            if (projectIds != null && !projectIds.isEmpty()) {
                fullArgs.add("--projectIds");
                fullArgs.add(String.join(",", projectIds));
                System.out.println("🎯 Using selected projects: " + String.join(", ", 
                    (includeProjectNames != null && !includeProjectNames.isEmpty()) ? includeProjectNames : projectIds));
            } else if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
                fullArgs.add("--includeProjectNames");
                fullArgs.add(String.join(",", includeProjectNames));
                System.out.println("🎯 Using selected projects: " + String.join(", ", includeProjectNames));
            } else {
                System.out.println("⚠️  No projects selected - results may be limited");
            }
            
            // Add the subcommand and its arguments
            fullArgs.addAll(args);
            
            System.out.println("\n" + "=".repeat(50));
            new CommandLine(new AtlasCliMain()).execute(fullArgs.toArray(new String[0]));
            System.out.println("\n" + "=".repeat(50));
            System.out.print("Press Enter to continue...");
            scanner.nextLine();
        }
    }
    
    private void addStatusFilter(Scanner scanner, List<String> args) {
        System.out.println("\nStatus Filter Options:");
        System.out.println("  1. All alerts (default)");
        System.out.println("  2. Open alerts only");
        System.out.println("  3. Closed alerts only");
        System.out.print("Select status filter [1-3]: ");
        
        String statusChoice = scanner.nextLine().trim();
        switch (statusChoice) {
            case "2":
                args.add("--status");
                args.add("OPEN");
                break;
            case "3":
                args.add("--status");
                args.add("CLOSED");
                break;
            case "1":
            case "":
            default:
                // No filter - show all alerts
                break;
        }
    }
    
    private void runClustersSubCommand(Scanner scanner) {
        System.out.println("  1. List Clusters");
        System.out.println("  2. Get Cluster Details");
        System.out.println("  3. Create Cluster");
        System.out.println("  0. Back");
        System.out.print("\nSelect operation: ");
        
        String choice = scanner.nextLine().trim();
        // Implementation similar to alerts...
        if (!"0".equals(choice) && !"back".equalsIgnoreCase(choice)) {
            System.out.println("\n" + "=".repeat(50));
            
            // Prepare complete command with global options
            List<String> fullArgs = new ArrayList<>();
            if (configFile != null) {
                fullArgs.add("--config");
                fullArgs.add(configFile.getPath());
            }
            if (projectIds != null && !projectIds.isEmpty()) {
                fullArgs.add("--projectIds");
                fullArgs.add(String.join(",", projectIds));
            } else if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
                fullArgs.add("--includeProjectNames");
                fullArgs.add(String.join(",", includeProjectNames));
            }
            fullArgs.add("clusters");
            fullArgs.add("list");
            
            new CommandLine(new AtlasCliMain()).execute(fullArgs.toArray(new String[0]));
            System.out.println("\n" + "=".repeat(50));
            System.out.print("Press Enter to continue...");
            scanner.nextLine();
        }
    }
    
    private void runProjectsSubCommand(Scanner scanner) {
        System.out.println("  1. List Projects");
        System.out.println("  2. Get Project Details");
        System.out.println("  0. Back");
        System.out.print("\nSelect operation: ");
        
        String choice = scanner.nextLine().trim();
        if ("1".equals(choice)) {
            System.out.println("\n" + "=".repeat(50));
            String[] args = {"projects", "list"};
            new CommandLine(new AtlasCliMain()).execute(args);
            System.out.println("\n" + "=".repeat(50));
            System.out.print("Press Enter to continue...");
            scanner.nextLine();
        } else if ("2".equals(choice)) {
            System.out.print("Enter project ID: ");
            String projectId = scanner.nextLine().trim();
            if (!"0".equals(projectId) && !"back".equalsIgnoreCase(projectId)) {
                System.out.println("\n" + "=".repeat(50));
                String[] args = {"projects", "get", projectId};
                new CommandLine(new AtlasCliMain()).execute(args);
                System.out.println("\n" + "=".repeat(50));
                System.out.print("Press Enter to continue...");
                scanner.nextLine();
            }
        }
    }

    public enum OutputFormat {
        TABLE, JSON, CSV, YAML
    }

    // Global configuration access
    public static class GlobalConfig {
        private static File configFile;
        private static boolean verbose;
        private static String apiPublicKey;
        private static String apiPrivateKey;
        private static List<String> projectIds;
        private static List<String> includeProjectNames;
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
            if (projectIds != null && !projectIds.isEmpty()) {
                // Use the first project ID as the default test project ID
                System.setProperty(AtlasTestConfig.TEST_PROJECT_ID, projectIds.get(0));
            }
            if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
                // Set project names as comma-separated list
                System.setProperty("includeProjectNames", String.join(",", includeProjectNames));
            }
            if (orgId != null) {
                System.setProperty(AtlasTestConfig.TEST_ORG_ID, orgId);
            }
            
            return config;
        }

        // Getters and setters
        public static File getConfigFile() { return configFile; }
        public static void setConfigFile(File configFile) { GlobalConfig.configFile = configFile; }
        
        public static boolean isVerbose() { return verbose; }
        public static void setVerbose(boolean verbose) { GlobalConfig.verbose = verbose; }
        
        public static String getApiPublicKey() { return apiPublicKey; }
        public static void setApiPublicKey(String apiPublicKey) { GlobalConfig.apiPublicKey = apiPublicKey; }
        
        public static String getApiPrivateKey() { return apiPrivateKey; }
        public static void setApiPrivateKey(String apiPrivateKey) { GlobalConfig.apiPrivateKey = apiPrivateKey; }
        
        public static List<String> getProjectIds() { return projectIds; }
        public static void setProjectIds(List<String> projectIds) { GlobalConfig.projectIds = projectIds; }
        
        public static List<String> getIncludeProjectNames() { return includeProjectNames; }
        public static void setIncludeProjectNames(List<String> includeProjectNames) { GlobalConfig.includeProjectNames = includeProjectNames; }
        
        // Legacy support - returns first project ID if available
        public static String getProjectId() { 
            return (projectIds != null && !projectIds.isEmpty()) ? projectIds.get(0) : null; 
        }
        public static void setProjectId(String projectId) { 
            GlobalConfig.projectIds = projectId != null ? List.of(projectId) : null; 
        }
        
        public static String getOrgId() { return orgId; }
        public static void setOrgId(String orgId) { GlobalConfig.orgId = orgId; }
        
        public static OutputFormat getFormat() { return format; }
        public static void setFormat(OutputFormat format) { GlobalConfig.format = format; }
    }
}