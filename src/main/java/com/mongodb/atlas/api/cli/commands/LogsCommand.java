package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.clients.AtlasLogsClient;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.cli.utils.ProjectSelectionUtils;
import com.mongodb.atlas.api.config.AtlasTestConfig;
import com.mongodb.atlas.api.logs.AtlasLogType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI commands for Atlas logs management
 */
@Command(
    name = "logs",
    description = "Access Atlas database and audit logs",
    subcommands = {
        LogsCommand.DownloadCommand.class,
        LogsCommand.ListCommand.class,
        LogsCommand.AccessLogsCommand.class
    }
)
public class LogsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli logs --help' to see available log commands");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  download      Download database logs for a cluster");
        System.out.println("  list          List available log types for a cluster");
        System.out.println("  access-logs   View database access logs (authentication attempts)");
        return 0;
    }


    @Command(name = "download", description = "Download database logs from Atlas clusters")
    static class DownloadCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Cluster name (optional in interactive mode)", arity = "0..1")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-o", "--output"}, description = "Output directory for log files", defaultValue = "./logs")
        private String outputDirectory;

        @Option(names = {"-s", "--start"}, description = "Start date/time (ISO format: 2024-01-15T10:00:00Z or 2024-01-15)")
        private String startDate;

        @Option(names = {"-e", "--end"}, description = "End date/time (ISO format: 2024-01-15T10:00:00Z or 2024-01-15)")
        private String endDate;

        @Option(names = {"-t", "--type"}, description = "Log types to download: mongodb,mongos,mongodb-audit,mongos-audit (comma-separated)", split = ",")
        private List<String> logTypes;

        @Option(names = {"--include-audit"}, description = "Include audit logs in download")
        private boolean includeAudit;

        @Option(names = {"-i", "--interactive"}, description = "Run in interactive mode")
        private boolean interactive;

        private String resolveProjectId(AtlasCliMain rootCmd) {
            // 1. Use explicit project parameter
            if (projectId != null && !projectId.trim().isEmpty()) {
                return projectId.trim();
            }
            
            // 2. Use first project ID from root command
            if (rootCmd.projectIds != null && !rootCmd.projectIds.isEmpty()) {
                return rootCmd.projectIds.get(0);
            }
            
            // 3. Try to resolve project names to IDs
            if (rootCmd.includeProjectNames != null && !rootCmd.includeProjectNames.isEmpty()) {
                try {
                    AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                    AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                    List<Map<String, Object>> projects = projectsClient.getAllProjects();
                    
                    for (Map<String, Object> project : projects) {
                        String projectName = (String) project.get("name");
                        if (rootCmd.includeProjectNames.contains(projectName)) {
                            return (String) project.get("id");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("⚠️  Error resolving project names: " + e.getMessage());
                }
            }
            
            // 4. No project ID available
            return null;
        }

        private String selectProjectInteractively(Scanner scanner, AtlasCliMain rootCmd) {
            try {
                System.out.println("🏢 Project Selection");
                System.out.println("─".repeat(30));
                
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.isEmpty()) {
                    System.err.println("❌ No projects found for this account");
                    return null;
                }

                // Auto-select if only one project
                if (projects.size() == 1) {
                    String selectedId = (String) projects.get(0).get("id");
                    String selectedName = (String) projects.get(0).get("name");
                    System.out.println("✅ Auto-selected project: " + selectedName + " (" + selectedId + ")");
                    System.out.println();
                    return selectedId;
                }

                System.out.println("Available projects:");
                for (int i = 0; i < projects.size(); i++) {
                    String name = (String) projects.get(i).get("name");
                    String id = (String) projects.get(i).get("id");
                    System.out.printf("  %d. %s (%s)%n", i + 1, name, id);
                }

                while (true) {
                    System.out.print("\nSelect project [1-" + projects.size() + "]: ");
                    String choice = scanner.nextLine().trim();
                    
                    if (choice.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        int index = Integer.parseInt(choice) - 1;
                        if (index >= 0 && index < projects.size()) {
                            String selectedId = (String) projects.get(index).get("id");
                            String selectedName = (String) projects.get(index).get("name");
                            System.out.println("✅ Selected project: " + selectedName + " (" + selectedId + ")");
                            System.out.println();
                            return selectedId;
                        } else {
                            System.err.println("❌ Invalid selection. Please choose a number between 1 and " + projects.size());
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("❌ Please enter a valid number");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error fetching projects: " + e.getMessage());
                return null;
            }
        }

        private String selectClusterInteractively(Scanner scanner, AtlasApiClient apiClient, String projectId) {
            try {
                System.out.println("🔍 Available clusters:");
                List<Map<String, Object>> clusters = apiClient.clusters().getClusters(projectId);
                
                if (clusters.isEmpty()) {
                    System.err.println("❌ No clusters found in project " + projectId);
                    return null;
                }

                // Auto-select if only one cluster
                if (clusters.size() == 1) {
                    String clusterName = (String) clusters.get(0).get("name");
                    String state = (String) clusters.get(0).get("stateName");
                    System.out.println("✅ Auto-selected cluster: " + clusterName + " (" + state + ")");
                    return clusterName;
                }

                for (int i = 0; i < clusters.size(); i++) {
                    String name = (String) clusters.get(i).get("name");
                    String state = (String) clusters.get(i).get("stateName");
                    System.out.printf("  %d. %s (%s)%n", i + 1, name, state);
                }

                while (true) {
                    System.out.print("\nSelect cluster [1-" + clusters.size() + "]: ");
                    String choice = scanner.nextLine().trim();
                    
                    if (choice.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        int index = Integer.parseInt(choice) - 1;
                        if (index >= 0 && index < clusters.size()) {
                            return (String) clusters.get(index).get("name");
                        } else {
                            System.err.println("❌ Invalid selection. Please choose a number between 1 and " + clusters.size());
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("❌ Please enter a valid number");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error fetching clusters: " + e.getMessage());
                return null;
            }
        }

        @Override
        public Integer call() throws Exception {
            // Get the root command (AtlasCliMain) to access credentials
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }
            
            
            // Interactive mode handling
            if (interactive || (clusterName == null || clusterName.trim().isEmpty())) {
                return runInteractiveDownload(rootCmd);
            }
            
            // Resolve project ID
            String effectiveProjectId = resolveProjectId(rootCmd);
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiClient apiClient = new AtlasApiClient(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasLogsClient logsClient = apiClient.logs();

                // Parse dates if provided
                Instant startInstant = parseDate(startDate);
                Instant endInstant = parseDate(endDate);

                // Determine which log types to download
                List<String> requestedLogTypes;
                if (logTypes != null && !logTypes.isEmpty()) {
                    // Validate log types
                    requestedLogTypes = logTypes.stream()
                        .map(type -> {
                            String cleanType = type.trim().toLowerCase();
                            switch (cleanType) {
                                case "mongodb": return "mongodb.gz";
                                case "mongos": return "mongos.gz";
                                case "mongodb-audit": return "mongodb-audit-log.gz";
                                case "mongos-audit": return "mongos-audit-log.gz";
                                default: return cleanType.endsWith(".gz") ? cleanType : cleanType + ".gz";
                            }
                        })
                        .collect(Collectors.toList());
                } else if (includeAudit) {
                    requestedLogTypes = AtlasLogsClient.getAllLogTypeFileNames();
                } else {
                    requestedLogTypes = AtlasLogsClient.getDefaultLogTypeFileNames();
                }

                System.out.println("📥 Downloading logs for cluster: " + clusterName);
                System.out.println("📁 Output directory: " + outputDirectory);
                System.out.println("📊 Log types: " + String.join(", ", requestedLogTypes));
                if (startInstant != null) System.out.println("📅 Start date: " + startInstant);
                if (endInstant != null) System.out.println("📅 End date: " + endInstant);
                System.out.println();

                List<Path> downloadedFiles = logsClient.downloadCompressedLogFilesForCluster(
                    effectiveProjectId, clusterName, startInstant, endInstant, outputDirectory, requestedLogTypes);

                if (downloadedFiles.isEmpty()) {
                    System.out.println("⚠️  No log files were downloaded.");
                    System.out.println("   This could mean:");
                    System.out.println("   • No logs available for the specified time range");
                    System.out.println("   • Cluster has no active processes");
                    System.out.println("   • Log types are not compatible with cluster processes");
                    return 0;
                }

                System.out.println("✅ Successfully downloaded " + downloadedFiles.size() + " log file(s):");
                for (Path file : downloadedFiles) {
                    System.out.println("   📄 " + file.toString());
                }

                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error downloading logs: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private Instant parseDate(String dateStr) {
            if (dateStr == null || dateStr.trim().isEmpty()) {
                return null;
            }

            try {
                // Try ISO instant format first
                if (dateStr.endsWith("Z") || dateStr.contains("T")) {
                    return Instant.parse(dateStr);
                }
                
                // Try date-only format (YYYY-MM-DD)
                if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return LocalDateTime.parse(dateStr + "T00:00:00").toInstant(ZoneOffset.UTC);
                }
                
                throw new DateTimeParseException("Unable to parse date", dateStr, 0);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date format: " + dateStr + 
                    ". Use ISO format like '2024-01-15T10:00:00Z' or '2024-01-15'");
            }
        }

        private Integer runInteractiveDownload(AtlasCliMain rootCmd) {
            Scanner scanner = new Scanner(System.in);
            
            try {
                AtlasApiClient apiClient = new AtlasApiClient(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasLogsClient logsClient = apiClient.logs();

                System.out.println("📥 Interactive Log Download");
                System.out.println("═".repeat(50));
                System.out.println();

                // Step 0: Select project
                String selectedProjectId = resolveProjectId(rootCmd);
                if (selectedProjectId == null) {
                    // Interactive project selection
                    selectedProjectId = selectProjectInteractively(scanner, rootCmd);
                    if (selectedProjectId == null) {
                        return 1; // User cancelled or error occurred
                    }
                }

                // Step 1: Select cluster
                String selectedCluster = clusterName;
                if (selectedCluster == null || selectedCluster.trim().isEmpty()) {
                    selectedCluster = selectClusterInteractively(scanner, apiClient, selectedProjectId);
                    if (selectedCluster == null) {
                        return 1; // User cancelled or error occurred
                    }
                }

                System.out.println("✅ Selected cluster: " + selectedCluster);
                System.out.println();

                // Step 2: Select log types
                List<String> selectedLogTypes;
                if (logTypes == null || logTypes.isEmpty()) {
                    System.out.println("📊 Select log types to download:");
                    System.out.println("  1. MongoDB logs only (mongodb.gz)");
                    System.out.println("  2. Mongos logs only (mongos.gz)");
                    System.out.println("  3. Both MongoDB and Mongos logs");
                    System.out.println("  4. All logs including audit logs");
                    System.out.println("  5. Custom selection");

                    System.out.print("\nSelect option [1-5]: ");
                    String logChoice = scanner.nextLine().trim();

                    switch (logChoice) {
                        case "1":
                            selectedLogTypes = List.of("mongodb.gz");
                            break;
                        case "2":
                            selectedLogTypes = List.of("mongos.gz");
                            break;
                        case "3":
                            selectedLogTypes = AtlasLogsClient.getDefaultLogTypeFileNames();
                            break;
                        case "4":
                            selectedLogTypes = AtlasLogsClient.getAllLogTypeFileNames();
                            break;
                        case "5":
                            selectedLogTypes = selectCustomLogTypes(scanner);
                            break;
                        default:
                            System.err.println("❌ Invalid selection");
                            return 1;
                    }
                } else {
                    selectedLogTypes = logTypes.stream()
                        .map(type -> {
                            String cleanType = type.trim().toLowerCase();
                            switch (cleanType) {
                                case "mongodb": return "mongodb.gz";
                                case "mongos": return "mongos.gz";
                                case "mongodb-audit": return "mongodb-audit-log.gz";
                                case "mongos-audit": return "mongos-audit-log.gz";
                                default: return cleanType.endsWith(".gz") ? cleanType : cleanType + ".gz";
                            }
                        })
                        .collect(Collectors.toList());
                }

                System.out.println("✅ Selected log types: " + String.join(", ", selectedLogTypes));
                System.out.println();

                // Step 3: Date range (optional)
                Instant startInstant = null, endInstant = null;
                if (startDate == null && endDate == null) {
                    System.out.println("📅 Time range options:");
                    System.out.println("  1. Last 4 hours");
                    System.out.println("  2. Last 8 hours");
                    System.out.println("  3. Last 12 hours");
                    System.out.println("  4. Last 24 hours");
                    System.out.println("  5. Custom date range");
                    System.out.println("  6. All available logs (no time filter)");

                    while (true) {
                        System.out.print("\nSelect time range [1-6]: ");
                        String timeChoice = scanner.nextLine().trim();
                        
                        if (timeChoice.isEmpty()) {
                            continue;
                        }

                        Instant now = Instant.now();
                        boolean validChoice = true;
                        
                        switch (timeChoice) {
                            case "1":
                                startInstant = now.minusSeconds(4 * 3600); // 4 hours
                                endInstant = now;
                                break;
                            case "2":
                                startInstant = now.minusSeconds(8 * 3600); // 8 hours
                                endInstant = now;
                                break;
                            case "3":
                                startInstant = now.minusSeconds(12 * 3600); // 12 hours
                                endInstant = now;
                                break;
                            case "4":
                                startInstant = now.minusSeconds(24 * 3600); // 24 hours
                                endInstant = now;
                                break;
                            case "5":
                                // Custom date range
                                System.out.print("Start date (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SSZ) [Enter for none]: ");
                                String startInput = scanner.nextLine().trim();
                                if (!startInput.isEmpty()) {
                                    try {
                                        startInstant = parseDate(startInput);
                                    } catch (Exception e) {
                                        System.err.println("❌ Invalid start date format: " + e.getMessage());
                                        validChoice = false;
                                        break;
                                    }
                                }

                                System.out.print("End date (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SSZ) [Enter for none]: ");
                                String endInput = scanner.nextLine().trim();
                                if (!endInput.isEmpty()) {
                                    try {
                                        endInstant = parseDate(endInput);
                                    } catch (Exception e) {
                                        System.err.println("❌ Invalid end date format: " + e.getMessage());
                                        validChoice = false;
                                        break;
                                    }
                                }
                                break;
                            case "6":
                                // No time filter - leave both null
                                break;
                            default:
                                System.err.println("❌ Invalid selection. Please choose a number between 1 and 6");
                                validChoice = false;
                        }
                        
                        if (validChoice) {
                            break;
                        }
                    }
                } else {
                    startInstant = parseDate(startDate);
                    endInstant = parseDate(endDate);
                }

                // Step 4: Output directory
                String selectedOutput = outputDirectory;
                if ("./logs".equals(outputDirectory)) {
                    System.out.print("📁 Output directory [" + outputDirectory + "]: ");
                    String outputInput = scanner.nextLine().trim();
                    if (!outputInput.isEmpty()) {
                        selectedOutput = outputInput;
                    }
                }

                System.out.println("✅ Output directory: " + selectedOutput);
                System.out.println();

                // Step 5: Confirmation and download
                System.out.println("📋 Download Summary:");
                System.out.println("   Cluster: " + selectedCluster);
                System.out.println("   Log types: " + String.join(", ", selectedLogTypes));
                if (startInstant != null) System.out.println("   Start date: " + startInstant);
                if (endInstant != null) System.out.println("   End date: " + endInstant);
                System.out.println("   Output: " + selectedOutput);
                System.out.println();

                System.out.print("🚀 Proceed with download? [Y/n]: ");
                String confirmChoice = scanner.nextLine().trim();
                if ("n".equalsIgnoreCase(confirmChoice) || "no".equalsIgnoreCase(confirmChoice)) {
                    System.out.println("❌ Download cancelled");
                    return 0;
                }

                System.out.println("📥 Starting download...");
                System.out.println();

                List<Path> downloadedFiles = logsClient.downloadCompressedLogFilesForCluster(
                    selectedProjectId, selectedCluster, startInstant, endInstant, selectedOutput, selectedLogTypes);

                if (downloadedFiles.isEmpty()) {
                    System.out.println("⚠️  No log files were downloaded.");
                    System.out.println("   This could mean:");
                    System.out.println("   • No logs available for the specified time range");
                    System.out.println("   • Cluster has no active processes");
                    System.out.println("   • Log types are not compatible with cluster processes");
                    return 0;
                }

                System.out.println("✅ Successfully downloaded " + downloadedFiles.size() + " log file(s):");
                for (Path file : downloadedFiles) {
                    System.out.println("   📄 " + file.toString());
                }

                return 0;

            } catch (Exception e) {
                System.err.println("❌ Error in interactive download: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private List<String> selectCustomLogTypes(Scanner scanner) {
            System.out.println("\n📊 Available log types:");
            String[] allTypes = {"mongodb.gz", "mongos.gz", "mongodb-audit-log.gz", "mongos-audit-log.gz"};
            for (int i = 0; i < allTypes.length; i++) {
                System.out.printf("  %d. %s%n", i + 1, allTypes[i]);
            }

            System.out.print("\nSelect types (comma-separated numbers, e.g., 1,3) [1,2]: ");
            String selection = scanner.nextLine().trim();
            
            if (selection.isEmpty()) {
                return List.of("mongodb.gz", "mongos.gz");
            }

            List<String> selected = new java.util.ArrayList<>();
            try {
                for (String num : selection.split(",")) {
                    int index = Integer.parseInt(num.trim()) - 1;
                    if (index >= 0 && index < allTypes.length) {
                        selected.add(allTypes[index]);
                    }
                }
            } catch (NumberFormatException e) {
                System.err.println("⚠️  Invalid selection, using default (mongodb.gz, mongos.gz)");
                return List.of("mongodb.gz", "mongos.gz");
            }

            return selected.isEmpty() ? List.of("mongodb.gz", "mongos.gz") : selected;
        }
    }

    @Command(name = "list", description = "List available log types for a cluster")
    static class ListCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Cluster name (optional in interactive mode)", arity = "0..1")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-i", "--interactive"}, description = "Run in interactive mode")
        private boolean interactive;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            
            if (!ProjectSelectionUtils.validateCredentials(config)) {
                return 1;
            }
            
            // Interactive mode handling
            if (interactive || (clusterName == null || clusterName.trim().isEmpty())) {
                return runInteractiveList(config);
            }
            
            // Resolve project ID
            String effectiveProjectId = ProjectSelectionUtils.resolveProjectId(
                projectId, config, false, null);
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiClient apiClient = new AtlasApiClient(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasLogsClient logsClient = apiClient.logs();

                System.out.println("📊 Available log types for cluster: " + clusterName);
                System.out.println();

                Map<String, List<Map<String, Object>>> logTypes = logsClient.getLogTypesForCluster(effectiveProjectId, clusterName);

                if (logTypes.isEmpty()) {
                    System.out.println("⚠️  No processes found for cluster: " + clusterName);
                    return 0;
                }

                for (Map.Entry<String, List<Map<String, Object>>> entry : logTypes.entrySet()) {
                    String processId = entry.getKey();
                    List<Map<String, Object>> logs = entry.getValue();

                    System.out.println("🔧 Process: " + processId);
                    if (logs.isEmpty()) {
                        System.out.println("   ⚠️  No logs available");
                    } else {
                        for (Map<String, Object> log : logs) {
                            String logName = (String) log.get("logName");
                            System.out.println("   📄 " + logName);
                        }
                    }
                    System.out.println();
                }

                System.out.println("💡 Available log type shortcuts:");
                System.out.println("   mongodb        -> mongodb.gz");
                System.out.println("   mongos         -> mongos.gz");
                System.out.println("   mongodb-audit  -> mongodb-audit-log.gz");
                System.out.println("   mongos-audit   -> mongos-audit-log.gz");

                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing log types: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private Integer runInteractiveList(AtlasTestConfig config) {
            Scanner scanner = new Scanner(System.in);
            
            try {
                AtlasApiClient apiClient = new AtlasApiClient(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasLogsClient logsClient = apiClient.logs();

                System.out.println("📊 Interactive Log Type Listing");
                System.out.println("═".repeat(50));
                System.out.println();

                // Step 0: Select project
                String selectedProjectId = ProjectSelectionUtils.resolveProjectId(
                    projectId, config, true, scanner);
                if (selectedProjectId == null) {
                    return 1; // User cancelled or error occurred
                }

                // Select cluster
                String selectedCluster = clusterName;
                if (selectedCluster == null || selectedCluster.trim().isEmpty()) {
                    System.out.println("🔍 Available clusters:");
                    try {
                        List<Map<String, Object>> clusters = apiClient.clusters().getClusters(selectedProjectId);
                        if (clusters.isEmpty()) {
                            System.out.println("⚠️  No clusters found in project " + selectedProjectId);
                            return 1;
                        }

                        for (int i = 0; i < clusters.size(); i++) {
                            String name = (String) clusters.get(i).get("name");
                            String state = (String) clusters.get(i).get("stateName");
                            System.out.printf("  %d. %s (%s)%n", i + 1, name, state);
                        }

                        System.out.print("\nSelect cluster [1-" + clusters.size() + "]: ");
                        String choice = scanner.nextLine().trim();
                        
                        try {
                            int index = Integer.parseInt(choice) - 1;
                            if (index >= 0 && index < clusters.size()) {
                                selectedCluster = (String) clusters.get(index).get("name");
                            } else {
                                System.err.println("❌ Invalid selection");
                                return 1;
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("❌ Invalid number format");
                            return 1;
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error fetching clusters: " + e.getMessage());
                        return 1;
                    }
                }

                System.out.println("✅ Selected cluster: " + selectedCluster);
                System.out.println();

                System.out.println("📊 Available log types for cluster: " + selectedCluster);
                System.out.println();

                Map<String, List<Map<String, Object>>> logTypes = logsClient.getLogTypesForCluster(selectedProjectId, selectedCluster);

                if (logTypes.isEmpty()) {
                    System.out.println("⚠️  No processes found for cluster: " + selectedCluster);
                    return 0;
                }

                for (Map.Entry<String, List<Map<String, Object>>> entry : logTypes.entrySet()) {
                    String processId = entry.getKey();
                    List<Map<String, Object>> logs = entry.getValue();

                    System.out.println("🔧 Process: " + processId);
                    if (logs.isEmpty()) {
                        System.out.println("   ⚠️  No logs available");
                    } else {
                        for (Map<String, Object> log : logs) {
                            String logName = (String) log.get("logName");
                            System.out.println("   📄 " + logName);
                        }
                    }
                    System.out.println();
                }

                System.out.println("💡 Available log type shortcuts:");
                System.out.println("   mongodb        -> mongodb.gz");
                System.out.println("   mongos         -> mongos.gz");
                System.out.println("   mongodb-audit  -> mongodb-audit-log.gz");
                System.out.println("   mongos-audit   -> mongos-audit-log.gz");

                return 0;

            } catch (Exception e) {
                System.err.println("❌ Error in interactive list: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "access-logs", description = "View database access logs (authentication attempts)")
    static class AccessLogsCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name (optional in interactive mode)", arity = "0..1")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-s", "--start"}, description = "Start date/time (ISO format: 2024-01-15T10:00:00Z)")
        private String startDate;

        @Option(names = {"-e", "--end"}, description = "End date/time (ISO format: 2024-01-15T10:00:00Z)")
        private String endDate;

        @Option(names = {"-l", "--limit"}, description = "Number of entries to return", defaultValue = "100")
        private int limit;

        @Option(names = {"-i", "--interactive"}, description = "Run in interactive mode")
        private boolean interactive;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            
            if (!ProjectSelectionUtils.validateCredentials(config)) {
                return 1;
            }
            
            // Interactive mode handling
            if (interactive || (clusterName == null || clusterName.trim().isEmpty())) {
                return runInteractiveAccessLogs(config);
            }
            
            // Resolve project ID
            String effectiveProjectId = ProjectSelectionUtils.resolveProjectId(
                projectId, config, false, null);
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiClient apiClient = new AtlasApiClient(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasLogsClient logsClient = apiClient.logs();

                System.out.println("🔍 Fetching database access logs for cluster: " + clusterName);
                if (startDate != null) System.out.println("📅 Start date: " + startDate);
                if (endDate != null) System.out.println("📅 End date: " + endDate);
                System.out.println("📊 Limit: " + limit);
                System.out.println();

                List<Map<String, Object>> accessLogs = logsClient.getAccessLogsForCluster(
                    effectiveProjectId, clusterName, startDate, endDate, limit);

                if (accessLogs.isEmpty()) {
                    System.out.println("📭 No access logs found for the specified criteria.");
                    return 0;
                }

                System.out.println("🔐 Found " + accessLogs.size() + " access log entries:");
                System.out.println();

                OutputFormatter.printAccessLogs(accessLogs, GlobalConfig.getFormat());

                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error fetching access logs: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private Integer runInteractiveAccessLogs(AtlasTestConfig config) {
            Scanner scanner = new Scanner(System.in);
            
            try {
                AtlasApiClient apiClient = new AtlasApiClient(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasLogsClient logsClient = apiClient.logs();

                System.out.println("🔐 Interactive Access Logs Viewer");
                System.out.println("═".repeat(50));
                System.out.println();

                // Step 0: Select project
                String selectedProjectId = ProjectSelectionUtils.resolveProjectId(
                    projectId, config, true, scanner);
                if (selectedProjectId == null) {
                    return 1; // User cancelled or error occurred
                }

                // Step 1: Select cluster
                String selectedCluster = clusterName;
                if (selectedCluster == null || selectedCluster.trim().isEmpty()) {
                    System.out.println("🔍 Available clusters:");
                    try {
                        List<Map<String, Object>> clusters = apiClient.clusters().getClusters(selectedProjectId);
                        if (clusters.isEmpty()) {
                            System.out.println("⚠️  No clusters found in project " + selectedProjectId);
                            return 1;
                        }

                        for (int i = 0; i < clusters.size(); i++) {
                            String name = (String) clusters.get(i).get("name");
                            String state = (String) clusters.get(i).get("stateName");
                            System.out.printf("  %d. %s (%s)%n", i + 1, name, state);
                        }

                        System.out.print("\nSelect cluster [1-" + clusters.size() + "]: ");
                        String choice = scanner.nextLine().trim();
                        
                        try {
                            int index = Integer.parseInt(choice) - 1;
                            if (index >= 0 && index < clusters.size()) {
                                selectedCluster = (String) clusters.get(index).get("name");
                            } else {
                                System.err.println("❌ Invalid selection");
                                return 1;
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("❌ Invalid number format");
                            return 1;
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error fetching clusters: " + e.getMessage());
                        return 1;
                    }
                }

                System.out.println("✅ Selected cluster: " + selectedCluster);
                System.out.println();

                // Step 2: Date range options
                String selectedStartDate = startDate;
                String selectedEndDate = endDate;
                
                if (startDate == null && endDate == null) {
                    System.out.println("📅 Time range options:");
                    System.out.println("  1. Last 4 hours");
                    System.out.println("  2. Last 8 hours");
                    System.out.println("  3. Last 12 hours");
                    System.out.println("  4. Last 24 hours");
                    System.out.println("  5. Last 7 days");
                    System.out.println("  6. Last 30 days");
                    System.out.println("  7. Custom date range");
                    System.out.println("  8. All available logs (no time filter)");

                    while (true) {
                        System.out.print("\nSelect time range [1-8]: ");
                        String timeChoice = scanner.nextLine().trim();
                        
                        if (timeChoice.isEmpty()) {
                            continue;
                        }

                        Instant now = Instant.now();
                        boolean validChoice = true;
                        
                        switch (timeChoice) {
                            case "1":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(4 * 3600).toString();
                                break;
                            case "2":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(8 * 3600).toString();
                                break;
                            case "3":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(12 * 3600).toString();
                                break;
                            case "4":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(24 * 3600).toString();
                                break;
                            case "5":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(7 * 24 * 3600).toString();
                                break;
                            case "6":
                                selectedEndDate = now.toString();
                                selectedStartDate = now.minusSeconds(30 * 24 * 3600).toString();
                                break;
                            case "7":
                                // Custom date range
                                System.out.print("Start date (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SSZ) [Enter for none]: ");
                                String startInput = scanner.nextLine().trim();
                                if (!startInput.isEmpty()) {
                                    selectedStartDate = startInput;
                                }

                                System.out.print("End date (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SSZ) [Enter for none]: ");
                                String endInput = scanner.nextLine().trim();
                                if (!endInput.isEmpty()) {
                                    selectedEndDate = endInput;
                                }
                                break;
                            case "8":
                                // No time filter - leave both null
                                break;
                            default:
                                System.err.println("❌ Invalid selection. Please choose a number between 1 and 8");
                                validChoice = false;
                        }
                        
                        if (validChoice) {
                            break;
                        }
                    }
                }

                // Step 3: Limit
                int selectedLimit = limit;
                if (limit == 100) {
                    System.out.println("\n📊 Number of entries to retrieve:");
                    System.out.println("  1. 50 entries");
                    System.out.println("  2. 100 entries (default)");
                    System.out.println("  3. 500 entries");
                    System.out.println("  4. 1000 entries");
                    System.out.println("  5. Custom number");

                    System.out.print("\nSelect option [1-5]: ");
                    String limitChoice = scanner.nextLine().trim();

                    switch (limitChoice) {
                        case "1":
                            selectedLimit = 50;
                            break;
                        case "2":
                            selectedLimit = 100;
                            break;
                        case "3":
                            selectedLimit = 500;
                            break;
                        case "4":
                            selectedLimit = 1000;
                            break;
                        case "5":
                            System.out.print("Enter number of entries [1-10000]: ");
                            String customLimit = scanner.nextLine().trim();
                            try {
                                int custom = Integer.parseInt(customLimit);
                                if (custom >= 1 && custom <= 10000) {
                                    selectedLimit = custom;
                                } else {
                                    System.err.println("⚠️  Invalid range, using default (100)");
                                    selectedLimit = 100;
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("⚠️  Invalid number, using default (100)");
                                selectedLimit = 100;
                            }
                            break;
                        default:
                            System.err.println("❌ Invalid selection");
                            return 1;
                    }
                }

                // Step 4: Summary and confirmation
                System.out.println("\n📋 Access Logs Query Summary:");
                System.out.println("   Cluster: " + selectedCluster);
                if (selectedStartDate != null) System.out.println("   Start date: " + selectedStartDate);
                if (selectedEndDate != null) System.out.println("   End date: " + selectedEndDate);
                System.out.println("   Limit: " + selectedLimit);
                System.out.println("   Output format: " + GlobalConfig.getFormat());
                System.out.println();

                System.out.print("🚀 Fetch access logs? [Y/n]: ");
                String confirmChoice = scanner.nextLine().trim();
                if ("n".equalsIgnoreCase(confirmChoice) || "no".equalsIgnoreCase(confirmChoice)) {
                    System.out.println("❌ Query cancelled");
                    return 0;
                }

                System.out.println("🔍 Fetching database access logs...");
                System.out.println();

                List<Map<String, Object>> accessLogs = logsClient.getAccessLogsForCluster(
                    selectedProjectId, selectedCluster, selectedStartDate, selectedEndDate, selectedLimit);

                if (accessLogs.isEmpty()) {
                    System.out.println("📭 No access logs found for the specified criteria.");
                    return 0;
                }

                System.out.println("🔐 Found " + accessLogs.size() + " access log entries:");
                System.out.println();

                OutputFormatter.printAccessLogs(accessLogs, GlobalConfig.getFormat());

                return 0;

            } catch (Exception e) {
                System.err.println("❌ Error in interactive access logs: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }
}