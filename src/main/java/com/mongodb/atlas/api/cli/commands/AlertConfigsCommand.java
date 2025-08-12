package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.clients.AtlasAlertConfigurationsClient;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * CLI command for managing Atlas alert configurations
 * Provides subcommands for creating, updating, and managing alert configurations
 */
@Command(
    name = "alert-configs",
    description = "Manage Atlas alert configurations",
    mixinStandardHelpOptions = true,
    subcommands = {
        AlertConfigsCommand.ListCommand.class,
        AlertConfigsCommand.GetCommand.class,
        AlertConfigsCommand.CreateCommand.class,
        AlertConfigsCommand.UpdateCommand.class,
        AlertConfigsCommand.EnableCommand.class,
        AlertConfigsCommand.DisableCommand.class,
        AlertConfigsCommand.DeleteCommand.class,
        AlertConfigsCommand.FieldNamesCommand.class,
        AlertConfigsCommand.CreateMetricCommand.class,
        AlertConfigsCommand.CreateHostCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class AlertConfigsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'alert-configs --help' to see available subcommands");
        return 0;
    }

    @Command(name = "list", description = "List alert configurations for a project")
    static class ListCommand implements Callable<Integer> {
        
        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;
        
        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
                
                if (effectiveProjectId == null) {
                    System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                    return 1;
                }

                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                List<Map<String, Object>> configs = configClient.getAlertConfigurations(effectiveProjectId);

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configs));
                } else {
                    printConfigsTable(configs);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error listing alert configurations: " + e.getMessage());
                return 1;
            }
        }

        private void printConfigsTable(List<Map<String, Object>> configs) {
            if (configs.isEmpty()) {
                System.out.println("No alert configurations found.");
                return;
            }

            System.out.printf("%-25s %-20s %-15s %-30s%n", 
                "CONFIG ID", "EVENT TYPE", "ENABLED", "NOTIFICATIONS");
            System.out.println("─".repeat(90));

            for (Map<String, Object> config : configs) {
                String configId = truncate((String) config.get("id"), 24);
                String eventType = truncate((String) config.get("eventTypeName"), 19);
                String enabled = String.valueOf(config.get("enabled"));
                
                List<Map<String, Object>> notifications = (List<Map<String, Object>>) config.get("notifications");
                String notificationCount = notifications != null ? notifications.size() + " notification(s)" : "0 notifications";

                System.out.printf("%-25s %-20s %-15s %-30s%n", 
                    configId, eventType, enabled, notificationCount);
            }
        }
    }

    @Command(name = "get", description = "Get details of a specific alert configuration")
    static class GetCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;
        
        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "JSON")
        private String format;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                Map<String, Object> alertConfig = configClient.getAlertConfiguration(projectId, alertConfigId);

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(alertConfig));
                } else {
                    printConfigDetails(alertConfig);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error getting alert configuration: " + e.getMessage());
                return 1;
            }
        }

        private void printConfigDetails(Map<String, Object> config) {
            System.out.println("Alert Configuration Details:");
            System.out.println("─".repeat(50));
            System.out.println("ID: " + config.get("id"));
            System.out.println("Event Type: " + config.get("eventTypeName"));
            System.out.println("Enabled: " + config.get("enabled"));
            
            if (config.get("metricThreshold") != null) {
                Map<String, Object> threshold = (Map<String, Object>) config.get("metricThreshold");
                System.out.println("Metric: " + threshold.get("metricName"));
                System.out.println("Operator: " + threshold.get("operator"));
                System.out.println("Threshold: " + threshold.get("threshold"));
            }
            
            List<Map<String, Object>> notifications = (List<Map<String, Object>>) config.get("notifications");
            if (notifications != null && !notifications.isEmpty()) {
                System.out.println("Notifications: " + notifications.size() + " configured");
            }
        }
    }

    @Command(name = "create", description = "Create a new alert configuration from JSON file")
    static class CreateCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Option(names = {"-f", "--file"}, description = "JSON file containing alert configuration", required = true)
        private File configFile;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                String jsonContent = Files.readString(configFile.toPath());
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> alertConfig = mapper.readValue(jsonContent, Map.class);

                Map<String, Object> result = configClient.createAlertConfiguration(projectId, alertConfig);

                System.out.println("Alert configuration created successfully:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Event Type: " + result.get("eventTypeName"));
                System.out.println("Enabled: " + result.get("enabled"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error creating alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "update", description = "Update an existing alert configuration from JSON file")
    static class UpdateCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;
        
        @Option(names = {"-f", "--file"}, description = "JSON file containing updated alert configuration", required = true)
        private File configFile;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                String jsonContent = Files.readString(configFile.toPath());
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> alertConfig = mapper.readValue(jsonContent, Map.class);

                Map<String, Object> result = configClient.updateAlertConfiguration(projectId, alertConfigId, alertConfig);

                System.out.println("Alert configuration updated successfully:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Event Type: " + result.get("eventTypeName"));
                System.out.println("Enabled: " + result.get("enabled"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error updating alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "enable", description = "Enable an alert configuration")
    static class EnableCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                Map<String, Object> result = configClient.enableAlertConfiguration(projectId, alertConfigId);

                System.out.println("Alert configuration enabled:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Enabled: " + result.get("enabled"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error enabling alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "disable", description = "Disable an alert configuration")
    static class DisableCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                Map<String, Object> result = configClient.disableAlertConfiguration(projectId, alertConfigId);

                System.out.println("Alert configuration disabled:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Enabled: " + result.get("enabled"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error disabling alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete an alert configuration")
    static class DeleteCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;
        
        @Option(names = {"--confirm"}, description = "Skip confirmation prompt")
        private boolean confirm;

        @Override
        public Integer call() throws Exception {
            try {
                if (!confirm) {
                    Scanner scanner = new Scanner(System.in);
                    System.out.print("Are you sure you want to delete alert configuration " + alertConfigId + "? (y/N): ");
                    String response = scanner.nextLine().trim();
                    if (!response.toLowerCase().startsWith("y")) {
                        System.out.println("Operation cancelled.");
                        return 0;
                    }
                }

                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                configClient.deleteAlertConfiguration(projectId, alertConfigId);

                System.out.println("Alert configuration deleted successfully: " + alertConfigId);

                return 0;
            } catch (Exception e) {
                System.err.println("Error deleting alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "field-names", description = "Get available matcher field names")
    static class FieldNamesCommand implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                List<String> fieldNames = configClient.getMatcherFieldNames();

                System.out.println("Available matcher field names:");
                System.out.println("─".repeat(30));
                for (String fieldName : fieldNames) {
                    System.out.println("• " + fieldName);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error getting matcher field names: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create-metric", description = "Create a metric-based alert configuration")
    static class CreateMetricCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Option(names = {"--metric"}, description = "Metric name to monitor", required = true)
        private String metricName;
        
        @Option(names = {"--operator"}, description = "Threshold operator (GREATER_THAN, LESS_THAN)", required = true)
        private String operator;
        
        @Option(names = {"--threshold"}, description = "Threshold value", required = true)
        private Double threshold;
        
        @Option(names = {"--email"}, description = "Email address for notifications")
        private String email;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                List<Map<String, Object>> notifications = List.of();
                if (email != null) {
                    notifications = List.of(Map.of(
                        "typeName", "EMAIL",
                        "emailAddress", email
                    ));
                }

                Map<String, Object> result = configClient.createMetricAlertConfiguration(
                    projectId, "OUTSIDE_METRIC_THRESHOLD", metricName, operator, threshold, notifications);

                System.out.println("Metric alert configuration created successfully:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Metric: " + metricName);
                System.out.println("Operator: " + operator);
                System.out.println("Threshold: " + threshold);

                return 0;
            } catch (Exception e) {
                System.err.println("Error creating metric alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create-host", description = "Create a host-based alert configuration")
    static class CreateHostCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Option(names = {"--event-type"}, description = "Event type name", required = true)
        private String eventTypeName;
        
        @Option(names = {"--hostname"}, description = "Target hostname", required = true)
        private String hostname;
        
        @Option(names = {"--port"}, description = "Target port")
        private Integer port;
        
        @Option(names = {"--email"}, description = "Email address for notifications")
        private String email;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertConfigurationsClient configClient = new AtlasAlertConfigurationsClient(apiBase);

                List<Map<String, Object>> notifications = List.of();
                if (email != null) {
                    notifications = List.of(Map.of(
                        "typeName", "EMAIL",
                        "emailAddress", email
                    ));
                }

                Map<String, Object> result = configClient.createHostAlertConfiguration(
                    projectId, eventTypeName, hostname, port, notifications);

                System.out.println("Host alert configuration created successfully:");
                System.out.println("ID: " + result.get("id"));
                System.out.println("Event Type: " + eventTypeName);
                System.out.println("Hostname: " + hostname);
                if (port != null) {
                    System.out.println("Port: " + port);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error creating host alert configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    // Utility method
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }
}