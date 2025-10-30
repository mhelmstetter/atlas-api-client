package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasNetworkAccessClient;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI commands for Atlas network access management
 */
@Command(
    name = "network-access",
    description = "Manage Atlas network access and IP whitelisting",
    mixinStandardHelpOptions = true,
    subcommands = {
        NetworkAccessCommand.ListCommand.class,
        NetworkAccessCommand.GetCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class NetworkAccessCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'network-access --help' to see available subcommands");
        return 0;
    }

    @Command(name = "list", description = "List IP access list entries for a project")
    static class ListCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project ID (optional if using config)", arity = "0..1")
        private String projectId;

        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;

        @Option(names = {"--debug"}, description = "Enable debug logging")
        private boolean debug;

        @Override
        public Integer call() throws Exception {
            // Control logging output based on debug flag
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            Logger atlasLogger = (Logger) LoggerFactory.getLogger("com.mongodb.atlas");
            Level originalRootLevel = rootLogger.getLevel();
            Level originalAtlasLevel = atlasLogger.getLevel();

            if (!debug) {
                rootLogger.setLevel(Level.WARN);
                atlasLogger.setLevel(Level.WARN);
            }

            try {
                String apiPublicKey = AtlasCliMain.GlobalConfig.getApiPublicKey();
                String apiPrivateKey = AtlasCliMain.GlobalConfig.getApiPrivateKey();

                if (apiPublicKey == null || apiPrivateKey == null) {
                    System.err.println("Error: API credentials not configured. Use --apiPublicKey and --apiPrivateKey options or set them in the config file.");
                    return 1;
                }

                AtlasApiBase apiBase = new AtlasApiBase(apiPublicKey, apiPrivateKey);

                // Get projects to process
                List<String> projectsToProcess = new ArrayList<>();

                if (debug) {
                    System.err.println("DEBUG: projectId parameter: " + projectId);
                    System.err.println("DEBUG: GlobalConfig.getProjectIds(): " + AtlasCliMain.GlobalConfig.getProjectIds());
                    System.err.println("DEBUG: GlobalConfig.getIncludeProjectNames(): " + AtlasCliMain.GlobalConfig.getIncludeProjectNames());
                }

                if (projectId != null) {
                    projectsToProcess.add(projectId);
                    if (debug) System.err.println("DEBUG: Using single project ID: " + projectId);
                } else if (AtlasCliMain.GlobalConfig.getProjectIds() != null && !AtlasCliMain.GlobalConfig.getProjectIds().isEmpty()) {
                    projectsToProcess.addAll(AtlasCliMain.GlobalConfig.getProjectIds());
                    if (debug) System.err.println("DEBUG: Using project IDs from GlobalConfig: " + projectsToProcess);
                } else if (AtlasCliMain.GlobalConfig.getIncludeProjectNames() != null && !AtlasCliMain.GlobalConfig.getIncludeProjectNames().isEmpty()) {
                    AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                    List<Map<String, Object>> projects = projectsClient.getAllProjects();

                    if (debug) System.err.println("DEBUG: Resolving project names to IDs...");
                    for (Map<String, Object> project : projects) {
                        String projectName = (String) project.get("name");
                        if (AtlasCliMain.GlobalConfig.getIncludeProjectNames().contains(projectName)) {
                            projectsToProcess.add((String) project.get("id"));
                            if (debug) System.err.println("DEBUG: Added project: " + projectName + " -> " + project.get("id"));
                        }
                    }

                    if (projectsToProcess.isEmpty()) {
                        System.err.println("No matching projects found for names: " + AtlasCliMain.GlobalConfig.getIncludeProjectNames());
                        return 1;
                    }
                } else {
                    System.err.println("No project specified. Use --projectIds, --includeProjectNames, or provide a project ID.");
                    return 1;
                }

                // Get project names for display
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> allProjects = projectsClient.getAllProjects();
                Map<String, String> projectIdToName = new HashMap<>();
                for (Map<String, Object> project : allProjects) {
                    projectIdToName.put((String) project.get("id"), (String) project.get("name"));
                }

                // Process network access for all projects
                AtlasNetworkAccessClient networkClient = new AtlasNetworkAccessClient(apiBase);
                List<Map<String, Object>> allEntries = new ArrayList<>();

                if (debug) {
                    System.err.println("DEBUG: Processing network access for " + projectsToProcess.size() + " projects:");
                    for (String pid : projectsToProcess) {
                        System.err.println("DEBUG: - " + pid + " (" + projectIdToName.get(pid) + ")");
                    }
                }

                for (String pid : projectsToProcess) {
                    try {
                        List<Map<String, Object>> projectEntries = networkClient.getIpAccessList(pid);
                        if (debug) {
                            System.err.println("DEBUG: Got " + projectEntries.size() + " network access entries from project " + pid + " (" + projectIdToName.get(pid) + ")");
                        }
                        // Add project context to each entry
                        for (Map<String, Object> entry : projectEntries) {
                            entry.put("_projectId", pid);
                            entry.put("_projectName", projectIdToName.get(pid));
                        }
                        allEntries.addAll(projectEntries);
                    } catch (Exception e) {
                        if (debug) {
                            System.err.println("Warning: Failed to get network access for project " + pid + ": " + e.getMessage());
                        }
                    }
                }

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(allEntries));
                } else {
                    if (debug && projectsToProcess.size() > 1) {
                        System.out.println("Showing network access entries for " + projectsToProcess.size() + " projects");
                    }
                    printNetworkAccessTable(allEntries);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error listing network access: " + e.getMessage());
                return 1;
            } finally {
                if (!debug) {
                    rootLogger.setLevel(originalRootLevel);
                    atlasLogger.setLevel(originalAtlasLevel);
                }
            }
        }

        private void printNetworkAccessTable(List<Map<String, Object>> entries) {
            if (entries.isEmpty()) {
                System.out.println("No network access entries found.");
                return;
            }

            // Check if we have entries from multiple projects
            boolean multiProject = entries.stream().anyMatch(entry -> entry.containsKey("_projectId"));

            if (multiProject) {
                System.out.printf("%-20s %-18s %-18s %-40s %-15s%n",
                    "PROJECT", "IP ADDRESS", "CIDR BLOCK", "COMMENT", "STATUS");
                System.out.println("─".repeat(115));

                for (Map<String, Object> entry : entries) {
                    String projectName = truncate((String) entry.get("_projectName"), 19);
                    String ipAddress = truncate((String) entry.get("ipAddress"), 17);
                    String cidrBlock = truncate((String) entry.get("cidrBlock"), 17);
                    String comment = truncate((String) entry.get("comment"), 39);
                    String status = truncate((String) entry.get("status"), 14);

                    System.out.printf("%-20s %-18s %-18s %-40s %-15s%n",
                        projectName,
                        ipAddress != null ? ipAddress : "",
                        cidrBlock != null ? cidrBlock : "",
                        comment != null ? comment : "",
                        status != null ? status : "");
                }
            } else {
                System.out.printf("%-18s %-18s %-40s %-15s%n",
                    "IP ADDRESS", "CIDR BLOCK", "COMMENT", "STATUS");
                System.out.println("─".repeat(95));

                for (Map<String, Object> entry : entries) {
                    String ipAddress = truncate((String) entry.get("ipAddress"), 17);
                    String cidrBlock = truncate((String) entry.get("cidrBlock"), 17);
                    String comment = truncate((String) entry.get("comment"), 39);
                    String status = truncate((String) entry.get("status"), 14);

                    System.out.printf("%-18s %-18s %-40s %-15s%n",
                        ipAddress != null ? ipAddress : "",
                        cidrBlock != null ? cidrBlock : "",
                        comment != null ? comment : "",
                        status != null ? status : "");
                }
            }
        }

        private String truncate(String str, int maxLength) {
            if (str == null) return "";
            return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
        }
    }

    @Command(name = "get", description = "Get details of a specific IP access list entry")
    static class GetCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project ID")
        private String projectId;

        @Parameters(index = "1", description = "IP address or CIDR block")
        private String entryValue;

        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasNetworkAccessClient networkClient = new AtlasNetworkAccessClient(apiBase);

                Map<String, Object> entry = networkClient.getIpAccessListEntry(projectId, entryValue);

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entry));
                } else {
                    printEntryDetails(entry);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error getting network access entry: " + e.getMessage());
                return 1;
            }
        }

        private void printEntryDetails(Map<String, Object> entry) {
            System.out.println("Network Access Entry Details:");
            System.out.println("─".repeat(50));

            if (entry.get("ipAddress") != null) {
                System.out.println("IP Address: " + entry.get("ipAddress"));
            }
            if (entry.get("cidrBlock") != null) {
                System.out.println("CIDR Block: " + entry.get("cidrBlock"));
            }
            if (entry.get("comment") != null) {
                System.out.println("Comment: " + entry.get("comment"));
            }
            if (entry.get("status") != null) {
                System.out.println("Status: " + entry.get("status"));
            }
            if (entry.get("deleteAfterDate") != null) {
                System.out.println("Delete After: " + entry.get("deleteAfterDate"));
            }
        }
    }
}