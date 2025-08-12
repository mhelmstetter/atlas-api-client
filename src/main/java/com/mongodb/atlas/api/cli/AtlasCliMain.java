package com.mongodb.atlas.api.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

import com.mongodb.atlas.api.cli.commands.AlertConfigsCommand;
import com.mongodb.atlas.api.cli.commands.AlertsCommand;
import com.mongodb.atlas.api.cli.commands.ApiKeysCommand;
import com.mongodb.atlas.api.cli.commands.BackupsCommand;
import com.mongodb.atlas.api.cli.commands.CleanupCommand;
import com.mongodb.atlas.api.cli.commands.ClustersCommand;
import com.mongodb.atlas.api.cli.commands.ConfigCommand;
import com.mongodb.atlas.api.cli.commands.DatabaseUsersCommand;
import com.mongodb.atlas.api.cli.commands.FlexClustersCommand;
import com.mongodb.atlas.api.cli.commands.InteractiveCommand;
import com.mongodb.atlas.api.cli.commands.LogsCommand;
import com.mongodb.atlas.api.cli.commands.LogsTestCommand;
import com.mongodb.atlas.api.cli.commands.MetricsCommand;
import com.mongodb.atlas.api.cli.commands.NetworkAccessCommand;
import com.mongodb.atlas.api.cli.commands.ProjectsCommand;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasClustersClient;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

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
        CleanupCommand.class,
        ClustersCommand.class,
        ConfigCommand.class,
        DatabaseUsersCommand.class,
        FlexClustersCommand.class,
        InteractiveCommand.class,
        LogsCommand.class,
        LogsTestCommand.class,
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
    
    @Option(names = {"--debug"}, 
            description = "Enable debug logging (shows API calls and other debug info)")
    private boolean debug;

    @Option(names = {"--apiPublicKey"}, 
            description = "Atlas API public key (overrides config)")
    public String apiPublicKey;

    @Option(names = {"--apiPrivateKey"}, 
            description = "Atlas API private key (overrides config)")
    public String apiPrivateKey;

    @Option(names = {"--projectIds"}, 
            description = "Atlas project IDs (comma-separated, overrides config)", split = ",")
    public List<String> projectIds;

    @Option(names = {"--includeProjectNames", "--projectNames"}, 
            description = "Atlas project names to include (comma-separated, overrides config)", split = ",")
    public List<String> includeProjectNames;

    @Option(names = {"--orgId"}, 
            description = "Atlas organization ID (overrides config)")
    public String orgId;

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
            // First CommandLine instance: parse to extract config file
            CommandLine tempCmd = new CommandLine(new AtlasCliMain());
            ParseResult tempResult = tempCmd.parseArgs(args);

            File defaultsFile;
            AtlasCliMain tempMain = tempResult.commandSpec().commandLine().getCommand();
            if (tempMain.configFile != null) {
                defaultsFile = tempMain.configFile;
            } else {
                defaultsFile = new File("atlas-client.properties");
            }

            if (defaultsFile.exists()) {
                System.out.println("Loading configuration from " + defaultsFile.getAbsolutePath());
            } else {
                System.out.println("Configuration file " + defaultsFile.getAbsolutePath() + " not found");
            }

            // Second CommandLine instance: parse with defaults loaded
            CommandLine cmd = new CommandLine(main);
            if (defaultsFile.exists()) {
                cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
            }
            
            ParseResult parseResult = cmd.parseArgs(args);

            if (!CommandLine.printHelpIfRequested(parseResult)) {
                // Set global configuration values before executing subcommands
                GlobalConfig.setConfigFile(main.configFile);
                GlobalConfig.setVerbose(main.verbose);
                GlobalConfig.setDebug(main.debug);
                GlobalConfig.setApiPublicKey(main.apiPublicKey);
                GlobalConfig.setApiPrivateKey(main.apiPrivateKey);
                GlobalConfig.setProjectIds(main.projectIds);
                GlobalConfig.setIncludeProjectNames(main.includeProjectNames);
                GlobalConfig.setOrgId(main.orgId);
                GlobalConfig.setFormat(main.format);
                GlobalConfig.setRootCommand(main);
                
                // Configure logging based on debug flag
                if (main.debug) {
                    setLogLevel("com.mongodb.atlas", "INFO");
                } else {
                    setLogLevel("com.mongodb.atlas", "WARN");
                }
                
                exitCode = cmd.execute(args);
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            exitCode = 1;
        }
        
        System.exit(exitCode);
    }
    
    private static void setLogLevel(String loggerName, String levelStr) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        Level level = Level.toLevel(levelStr, Level.INFO);
        logger.setLevel(level);
    }

    @Override
    public Integer call() throws Exception {
        // Set log level early based on debug flag
        if (!debug) {
            setLogLevel("com.mongodb.atlas", "WARN");
        }
        
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
        } else if (apiPublicKey != null && apiPrivateKey != null) {
            // Auto-discover projects if API credentials are available
            try {
                AtlasApiBase apiBase = new AtlasApiBase(apiPublicKey, apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.isEmpty()) {
                    return "⚠️  No projects found";
                } else if (projects.size() == 1) {
                    String name = (String) projects.get(0).get("name");
                    return "✅ Auto-discovered: " + name;
                } else {
                    return "✅ " + projects.size() + " projects available (auto-discovered)";
                }
            } catch (Exception e) {
                return "❌ Error discovering projects: " + e.getMessage();
            }
        } else {
            return "❌ None selected (API credentials required)";
        }
    }
    
    private String suggestClusterName() {
        try {
            // Try to get existing clusters to suggest a unique name
            AtlasApiBase apiBase = new AtlasApiBase(apiPublicKey, apiPrivateKey);
            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
            List<Map<String, Object>> projects = projectsClient.getAllProjects();
            
            if (!projects.isEmpty()) {
                String projectId = null;
                // Use configured project or first available
                if (projectIds != null && !projectIds.isEmpty()) {
                    projectId = projectIds.get(0);
                } else if (projects.size() == 1) {
                    projectId = (String) projects.get(0).get("id");
                }
                
                if (projectId != null) {
                    // Check existing clusters
                    AtlasClustersClient clustersClient = new AtlasClustersClient(apiBase);
                    List<Map<String, Object>> clusters = clustersClient.getClusters(projectId);
                    
                    // Find the next available Cluster number
                    int maxNum = -1;
                    for (Map<String, Object> cluster : clusters) {
                        String name = (String) cluster.get("name");
                        if (name != null && name.matches("Cluster\\d+")) {
                            int num = Integer.parseInt(name.substring(7));
                            maxNum = Math.max(maxNum, num);
                        }
                    }
                    
                    return "Cluster" + (maxNum + 1);
                }
            }
        } catch (Exception e) {
            // Ignore errors, just return default
        }
        
        return "Cluster0";
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
                    if (apiPublicKey != null && !apiPublicKey.trim().isEmpty()) {
                        fullArgs.add("--apiPublicKey");
                        fullArgs.add(apiPublicKey);
                    }
                    if (apiPrivateKey != null && !apiPrivateKey.trim().isEmpty()) {
                        fullArgs.add("--apiPrivateKey");
                        fullArgs.add(apiPrivateKey);
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
                    
                    new CommandLine(this).execute(fullArgs.toArray(new String[0]));
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
            if (apiPublicKey != null && !apiPublicKey.trim().isEmpty()) {
                fullArgs.add("--apiPublicKey");
                fullArgs.add(apiPublicKey);
            }
            if (apiPrivateKey != null && !apiPrivateKey.trim().isEmpty()) {
                fullArgs.add("--apiPrivateKey");
                fullArgs.add(apiPrivateKey);
            }
            // Use GlobalConfig values (which include interactive selections) with fallback to config file values
            List<String> currentProjectIds = GlobalConfig.getProjectIds() != null && !GlobalConfig.getProjectIds().isEmpty() 
                ? GlobalConfig.getProjectIds() : projectIds;
            List<String> currentProjectNames = GlobalConfig.getIncludeProjectNames() != null && !GlobalConfig.getIncludeProjectNames().isEmpty() 
                ? GlobalConfig.getIncludeProjectNames() : includeProjectNames;
            
            if (currentProjectIds != null && !currentProjectIds.isEmpty()) {
                fullArgs.add("--projectIds");
                fullArgs.add(String.join(",", currentProjectIds));
                System.out.println("🎯 Using selected projects: " + String.join(", ", 
                    (currentProjectNames != null && !currentProjectNames.isEmpty()) ? currentProjectNames : currentProjectIds));
            } else if (currentProjectNames != null && !currentProjectNames.isEmpty()) {
                fullArgs.add("--includeProjectNames");
                fullArgs.add(String.join(",", currentProjectNames));
                System.out.println("🎯 Using selected projects: " + String.join(", ", currentProjectNames));
            } else {
                System.out.println("⚠️  No projects selected - results may be limited");
            }
            
            // Add the subcommand and its arguments
            fullArgs.addAll(args);
            
            // Add debug flag for alerts commands to help troubleshoot
            if (args.size() > 0 && "alerts".equals(args.get(0))) {
                fullArgs.add("--debug");
                System.out.println("🐛 Debug mode enabled for troubleshooting");
            }
            
            System.out.println("\n" + "=".repeat(50));
            new CommandLine(this).execute(fullArgs.toArray(new String[0]));
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
        System.out.println("  2. List All Clusters (All Projects)");
        System.out.println("  3. Get Cluster Details");
        System.out.println("  4. Create Cluster");
        System.out.println("  0. Back");
        System.out.print("\nSelect operation: ");
        
        String choice = scanner.nextLine().trim();
        
        String operation = null;
        boolean needsInteraction = false;
        
        switch (choice) {
            case "1":
                operation = "list";
                break;
            case "2":
                operation = "list-all";
                break;
            case "3":
                operation = "get";
                needsInteraction = true; // Will need cluster name
                break;
            case "4":
                // Check if projects are selected before creating clusters
                List<String> currentProjectIds = GlobalConfig.getProjectIds() != null && !GlobalConfig.getProjectIds().isEmpty() 
                    ? GlobalConfig.getProjectIds() : projectIds;
                List<String> currentProjectNames = GlobalConfig.getIncludeProjectNames() != null && !GlobalConfig.getIncludeProjectNames().isEmpty() 
                    ? GlobalConfig.getIncludeProjectNames() : includeProjectNames;
                
                if ((currentProjectIds == null || currentProjectIds.isEmpty()) && 
                    (currentProjectNames == null || currentProjectNames.isEmpty())) {
                    
                    // Try to auto-select if only one project exists
                    try {
                        String publicKey = apiPublicKey != null ? apiPublicKey : System.getProperty("atlas.api.public.key");
                        String privateKey = apiPrivateKey != null ? apiPrivateKey : System.getProperty("atlas.api.private.key");
                        
                        if (publicKey != null && privateKey != null) {
                            AtlasApiBase apiBase = new AtlasApiBase(publicKey, privateKey);
                            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                            List<Map<String, Object>> projects = projectsClient.getAllProjects();
                            
                            if (projects.size() == 1) {
                                // Auto-select the single project silently
                                Map<String, Object> singleProject = projects.get(0);
                                String projectId = (String) singleProject.get("id");
                                String projectName = (String) singleProject.get("name");
                                
                                GlobalConfig.setProjectIds(List.of(projectId));
                                GlobalConfig.setIncludeProjectNames(List.of(projectName));
                                
                                // Continue with cluster creation - no need to inform user
                            } else if (projects.size() > 1) {
                                // Multiple projects - ask user to select
                                System.out.println("\n❌ No projects selected. You have multiple projects available.");
                                System.out.print("Would you like to select projects now? [Y/n]: ");
                                String selectNow = scanner.nextLine().trim();
                                
                                if (!"n".equalsIgnoreCase(selectNow) && !"no".equalsIgnoreCase(selectNow)) {
                                    selectProjects(scanner);
                                    // Check again after selection
                                    currentProjectIds = GlobalConfig.getProjectIds() != null && !GlobalConfig.getProjectIds().isEmpty() 
                                        ? GlobalConfig.getProjectIds() : projectIds;
                                    currentProjectNames = GlobalConfig.getIncludeProjectNames() != null && !GlobalConfig.getIncludeProjectNames().isEmpty() 
                                        ? GlobalConfig.getIncludeProjectNames() : includeProjectNames;
                                    
                                    if ((currentProjectIds == null || currentProjectIds.isEmpty()) && 
                                        (currentProjectNames == null || currentProjectNames.isEmpty())) {
                                        System.out.println("❌ No projects selected. Cannot create clusters without a project.");
                                        return;
                                    }
                                } else {
                                    System.out.println("❌ Cannot create clusters without selecting a project.");
                                    return;
                                }
                            } else {
                                System.out.println("❌ No projects found in your Atlas organization.");
                                return;
                            }
                        } else {
                            System.out.println("❌ API credentials not configured. Cannot create clusters.");
                            return;
                        }
                    } catch (Exception e) {
                        System.out.println("❌ Error checking projects: " + e.getMessage());
                        return;
                    }
                }
                operation = "create";
                break;
            case "0":
            case "back":
                return;
            default:
                System.out.println("❌ Invalid option. Please select 0-4.");
                return;
        }
        
        if (operation != null) {
            System.out.println("\n" + "=".repeat(50));
            
            // Prepare complete command with global options
            List<String> fullArgs = new ArrayList<>();
            if (configFile != null) {
                fullArgs.add("--config");
                fullArgs.add(configFile.getPath());
            }
            if (debug) {
                fullArgs.add("--debug");
            }
            if (apiPublicKey != null && !apiPublicKey.trim().isEmpty()) {
                fullArgs.add("--apiPublicKey");
                fullArgs.add(apiPublicKey);
            }
            if (apiPrivateKey != null && !apiPrivateKey.trim().isEmpty()) {
                fullArgs.add("--apiPrivateKey");
                fullArgs.add(apiPrivateKey);
            }
            // Use GlobalConfig values (which include interactive selections) with fallback to config file values
            List<String> currentProjectIds = GlobalConfig.getProjectIds() != null && !GlobalConfig.getProjectIds().isEmpty() 
                ? GlobalConfig.getProjectIds() : projectIds;
            List<String> currentProjectNames = GlobalConfig.getIncludeProjectNames() != null && !GlobalConfig.getIncludeProjectNames().isEmpty() 
                ? GlobalConfig.getIncludeProjectNames() : includeProjectNames;
            
            if (currentProjectIds != null && !currentProjectIds.isEmpty()) {
                // Use --projectIds for all operations
                fullArgs.add("--projectIds");
                fullArgs.add(String.join(",", currentProjectIds));
                System.out.println("🎯 Using selected projects: " + String.join(", ", 
                    (currentProjectNames != null && !currentProjectNames.isEmpty()) ? currentProjectNames : currentProjectIds));
            } else if (currentProjectNames != null && !currentProjectNames.isEmpty()) {
                fullArgs.add("--includeProjectNames");
                fullArgs.add(String.join(",", currentProjectNames));
                System.out.println("🎯 Using selected projects: " + String.join(", ", currentProjectNames));
            } else {
                System.out.println("⚠️  No projects selected - results may be limited");
            }
            fullArgs.add("clusters");
            fullArgs.add(operation);
            
            // Add interactive flag for operations that need it
            if (needsInteraction && operation.equals("get")) {
                System.out.print("Enter cluster name: ");
                String clusterName = scanner.nextLine().trim();
                if (!clusterName.isEmpty()) {
                    fullArgs.add(clusterName);
                }
            } else if (operation.equals("create")) {
                // For create, prompt for cluster name and basic settings
                System.out.println("\n🚀 Create New Cluster");
                System.out.println("─".repeat(30));
                
                // Suggest a default cluster name
                String suggestedName = suggestClusterName();
                System.out.print("Cluster name [" + suggestedName + "]: ");
                String clusterName = scanner.nextLine().trim();
                if (clusterName.isEmpty()) {
                    clusterName = suggestedName;
                }
                fullArgs.add(clusterName);
                
                // Ask for basic configuration
                System.out.println("\nCluster size options:");
                System.out.println("  1. M10 (General - smallest production instance)");
                System.out.println("  2. M20 (General)");
                System.out.println("  3. M30 (General)");
                System.out.println("  4. M40 (General)");
                System.out.println("  5. Custom size");
                System.out.print("\nSelect size [1-5]: ");
                String sizeChoice = scanner.nextLine().trim();
                
                String size = "M10"; // default
                switch (sizeChoice) {
                    case "1": size = "M10"; break;
                    case "2": size = "M20"; break;
                    case "3": size = "M30"; break;
                    case "4": size = "M40"; break;
                    case "5":
                        System.out.print("Enter size (M10, M20, M30, M40, M50, M60, M80, etc.): ");
                        size = scanner.nextLine().trim();
                        if (size.isEmpty()) size = "M10";
                        break;
                }
                fullArgs.add("--size=" + size);
                
                System.out.print("\nCreate sharded cluster? [y/N]: ");
                String shardedChoice = scanner.nextLine().trim();
                if ("y".equalsIgnoreCase(shardedChoice) || "yes".equalsIgnoreCase(shardedChoice)) {
                    fullArgs.add("--sharded");
                    
                    System.out.println("\nSharding configuration:");
                    System.out.println("  1. Symmetric sharding (all shards same size)");
                    System.out.println("  2. Asymmetric sharding (different sizes per shard) - NEW!");
                    System.out.print("\nSelect sharding type [1-2]: ");
                    String shardingType = scanner.nextLine().trim();
                    
                    if ("2".equals(shardingType)) {
                        // Asymmetric sharding
                        System.out.println("\n⚠️  Asymmetric sharding uses API v20240805 and is irreversible!");
                        System.out.println("   Once enabled, you cannot manage this cluster with older API versions.");
                        System.out.print("\nContinue with asymmetric sharding? [y/N]: ");
                        String confirmAsym = scanner.nextLine().trim();
                        
                        if ("y".equalsIgnoreCase(confirmAsym) || "yes".equalsIgnoreCase(confirmAsym)) {
                            fullArgs.add("--asymmetric");
                            
                            System.out.print("\nNumber of shards [2]: ");
                            String numShardsStr = scanner.nextLine().trim();
                            int numShards = numShardsStr.isEmpty() ? 2 : Integer.parseInt(numShardsStr);
                            
                            // Collect size for each shard
                            List<String> shardSizes = new ArrayList<>();
                            for (int i = 1; i <= numShards; i++) {
                                System.out.print("Size for shard " + i + " [" + size + "]: ");
                                String shardSize = scanner.nextLine().trim();
                                if (shardSize.isEmpty()) {
                                    shardSize = size;
                                }
                                shardSizes.add(shardSize);
                            }
                            
                            // Pass shard sizes as comma-separated list
                            fullArgs.add("--shard-sizes=" + String.join(",", shardSizes));
                        } else {
                            // Fall back to symmetric
                            System.out.print("Number of shards [2]: ");
                            String numShards = scanner.nextLine().trim();
                            if (!numShards.isEmpty()) {
                                fullArgs.add("--num-shards=" + numShards);
                            }
                        }
                    } else {
                        // Symmetric sharding (default)
                        System.out.print("Number of shards [2]: ");
                        String numShards = scanner.nextLine().trim();
                        if (!numShards.isEmpty()) {
                            fullArgs.add("--num-shards=" + numShards);
                        }
                    }
                }
                
                System.out.print("\nWait for cluster to be ready? [Y/n]: ");
                String waitChoice = scanner.nextLine().trim();
                if (!"n".equalsIgnoreCase(waitChoice) && !"no".equalsIgnoreCase(waitChoice)) {
                    fullArgs.add("--wait");
                }
                
                System.out.println("\n" + "─".repeat(30));
            }
            
            new CommandLine(this).execute(fullArgs.toArray(new String[0]));
            
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
        private static boolean debug;
        private static String apiPublicKey;
        private static String apiPrivateKey;
        private static List<String> projectIds;
        private static List<String> includeProjectNames;
        private static String orgId;
        private static OutputFormat format = OutputFormat.TABLE;
        private static AtlasCliMain rootCommand;

        public static AtlasTestConfig getAtlasConfig() {
            boolean systemPropertiesChanged = false;
            
            // Load properties from config file if specified and values not already set
            if (configFile != null && configFile.exists()) {
                try {
                    java.util.Properties fileProps = new java.util.Properties();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                        fileProps.load(fis);
                    }
                    
                    // Set system properties from config file if not already overridden
                    if (apiPublicKey == null && fileProps.getProperty(AtlasTestConfig.API_PUBLIC_KEY) != null) {
                        System.setProperty(AtlasTestConfig.API_PUBLIC_KEY, fileProps.getProperty(AtlasTestConfig.API_PUBLIC_KEY));
                        systemPropertiesChanged = true;
                    }
                    if (apiPrivateKey == null && fileProps.getProperty(AtlasTestConfig.API_PRIVATE_KEY) != null) {
                        System.setProperty(AtlasTestConfig.API_PRIVATE_KEY, fileProps.getProperty(AtlasTestConfig.API_PRIVATE_KEY));
                        systemPropertiesChanged = true;
                    }
                    if (projectIds == null && fileProps.getProperty(AtlasTestConfig.TEST_PROJECT_ID) != null) {
                        System.setProperty(AtlasTestConfig.TEST_PROJECT_ID, fileProps.getProperty(AtlasTestConfig.TEST_PROJECT_ID));
                        systemPropertiesChanged = true;
                    }
                    if (orgId == null && fileProps.getProperty(AtlasTestConfig.TEST_ORG_ID) != null) {
                        System.setProperty(AtlasTestConfig.TEST_ORG_ID, fileProps.getProperty(AtlasTestConfig.TEST_ORG_ID));
                        systemPropertiesChanged = true;
                    }
                } catch (java.io.IOException e) {
                    System.err.println("Warning: Could not load config file " + configFile + ": " + e.getMessage());
                }
            }
            
            // Override with command-line values if provided
            if (apiPublicKey != null) {
                System.setProperty(AtlasTestConfig.API_PUBLIC_KEY, apiPublicKey);
                systemPropertiesChanged = true;
            }
            if (apiPrivateKey != null) {
                System.setProperty(AtlasTestConfig.API_PRIVATE_KEY, apiPrivateKey);
                systemPropertiesChanged = true;
            }
            if (projectIds != null && !projectIds.isEmpty()) {
                // Use the first project ID as the default test project ID
                System.setProperty(AtlasTestConfig.TEST_PROJECT_ID, projectIds.get(0));
                systemPropertiesChanged = true;
            }
            if (includeProjectNames != null && !includeProjectNames.isEmpty()) {
                // Set project names as comma-separated list
                System.setProperty("includeProjectNames", String.join(",", includeProjectNames));
                systemPropertiesChanged = true;
            }
            if (orgId != null) {
                System.setProperty(AtlasTestConfig.TEST_ORG_ID, orgId);
                systemPropertiesChanged = true;
            }
            
            // Refresh the AtlasTestConfig singleton if we changed system properties
            if (systemPropertiesChanged) {
                AtlasTestConfig.refreshInstance();
            }
            
            return AtlasTestConfig.getInstance();
        }

        // Getters and setters
        public static File getConfigFile() { return configFile; }
        public static void setConfigFile(File configFile) { GlobalConfig.configFile = configFile; }
        
        public static boolean isVerbose() { return verbose; }
        public static void setVerbose(boolean verbose) { GlobalConfig.verbose = verbose; }
        
        public static boolean isDebug() { return debug; }
        public static void setDebug(boolean debug) { GlobalConfig.debug = debug; }
        
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
        
        public static AtlasCliMain getRootCommand() { return rootCommand; }
        public static void setRootCommand(AtlasCliMain rootCommand) { GlobalConfig.rootCommand = rootCommand; }
    }
}