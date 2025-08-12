package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.clients.AtlasAlertsClient;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

/**
 * CLI command for managing Atlas alerts
 * Provides subcommands for viewing and acknowledging alerts
 */
@Command(
    name = "alerts",
    description = "Manage Atlas alerts",
    mixinStandardHelpOptions = true,
    subcommands = {
        AlertsCommand.ListCommand.class,
        AlertsCommand.GetCommand.class,
        AlertsCommand.AcknowledgeCommand.class,
        AlertsCommand.UnacknowledgeCommand.class,
        AlertsCommand.ForConfigCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class AlertsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'alerts --help' to see available subcommands");
        return 0;
    }

    @Command(name = "list", description = "List alerts for a project")
    static class ListCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID (optional if using config)", arity = "0..1")
        private String projectId;
        
        @Option(names = {"--status"}, description = "Filter by status: OPEN, CLOSED")
        private String status;
        
        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;
        
        @Option(names = {"--page-size"}, description = "Number of alerts per page for table output", defaultValue = "25")
        private int pageSize;
        
        @Option(names = {"--no-paging"}, description = "Disable pagination and show all alerts")
        private boolean noPaging;
        
        @Option(names = {"--debug"}, description = "Enable debug logging")
        private boolean debug;
        
        @Option(names = {"--ui", "--tui"}, description = "Use terminal UI (ncurses-style interface)")
        private boolean terminalUI;

        @Override
        public Integer call() throws Exception {
            // Control logging output based on debug flag
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            Logger atlasLogger = (Logger) LoggerFactory.getLogger("com.mongodb.atlas");
            Level originalRootLevel = rootLogger.getLevel();
            Level originalAtlasLevel = atlasLogger.getLevel();
            
            if (!debug) {
                // Suppress INFO/DEBUG logs when debug is disabled, only show WARN and above
                rootLogger.setLevel(Level.WARN);
                atlasLogger.setLevel(Level.WARN);
            }
            
            try {
                // Use GlobalConfig values directly instead of AtlasTestConfig
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
                    // Use specified project ID
                    projectsToProcess.add(projectId);
                    if (debug) System.err.println("DEBUG: Using single project ID: " + projectId);
                } else if (AtlasCliMain.GlobalConfig.getProjectIds() != null && !AtlasCliMain.GlobalConfig.getProjectIds().isEmpty()) {
                    // Use project IDs from command line
                    projectsToProcess.addAll(AtlasCliMain.GlobalConfig.getProjectIds());
                    if (debug) System.err.println("DEBUG: Using project IDs from GlobalConfig: " + projectsToProcess);
                } else if (AtlasCliMain.GlobalConfig.getIncludeProjectNames() != null && !AtlasCliMain.GlobalConfig.getIncludeProjectNames().isEmpty()) {
                    // Use project names - need to resolve to IDs
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

                // Process alerts for all projects
                AtlasAlertsClient alertsClient = new AtlasAlertsClient(apiBase);
                List<Map<String, Object>> allAlerts = new ArrayList<>();
                
                if (debug) {
                    System.err.println("DEBUG: Processing alerts for " + projectsToProcess.size() + " projects:");
                    for (String pid : projectsToProcess) {
                        System.err.println("DEBUG: - " + pid + " (" + projectIdToName.get(pid) + ")");
                    }
                }
                
                for (String pid : projectsToProcess) {
                    try {
                        List<Map<String, Object>> projectAlerts = alertsClient.getProjectAlerts(pid, status);
                        if (debug) {
                            System.err.println("DEBUG: Got " + projectAlerts.size() + " alerts from project " + pid + " (" + projectIdToName.get(pid) + ")");
                        }
                        // Add project context to each alert
                        for (Map<String, Object> alert : projectAlerts) {
                            alert.put("_projectId", pid);
                            alert.put("_projectName", projectIdToName.get(pid));
                        }
                        allAlerts.addAll(projectAlerts);
                    } catch (Exception e) {
                        if (debug) {
                            System.err.println("Warning: Failed to get alerts for project " + pid + ": " + e.getMessage());
                        }
                    }
                }

                // Sort alerts by created timestamp (descending - newest first)
                Collections.sort(allAlerts, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> a1, Map<String, Object> a2) {
                        String created1 = (String) a1.get("created");
                        String created2 = (String) a2.get("created");
                        if (created1 == null && created2 == null) return 0;
                        if (created1 == null) return 1;
                        if (created2 == null) return -1;
                        try {
                            Instant instant1 = Instant.parse(created1);
                            Instant instant2 = Instant.parse(created2);
                            return instant2.compareTo(instant1); // Descending order
                        } catch (Exception e) {
                            return created2.compareTo(created1); // Fallback string comparison
                        }
                    }
                });

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(allAlerts));
                } else if (terminalUI) {
                    showInteractiveAlertsTable(allAlerts, projectsToProcess.size() > 1);
                } else {
                    if (debug && projectsToProcess.size() > 1) {
                        System.out.println("Showing alerts for " + projectsToProcess.size() + " projects");
                    }
                    if (noPaging) {
                        printAlertsTable(allAlerts);
                    } else {
                        printAlertsTableWithPagination(allAlerts);
                    }
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error listing alerts: " + e.getMessage());
                return 1;
            } finally {
                // Restore original logging levels
                if (!debug) {
                    rootLogger.setLevel(originalRootLevel);
                    atlasLogger.setLevel(originalAtlasLevel);
                }
            }
        }

        private void printAlertsTableWithPagination(List<Map<String, Object>> alerts) {
            if (alerts.isEmpty()) {
                System.out.println("No alerts found.");
                return;
            }

            int totalAlerts = alerts.size();
            int totalPages = (int) Math.ceil((double) totalAlerts / pageSize);
            int currentPage = 1;
            Scanner scanner = new Scanner(System.in);

            while (true) {
                // Clear screen (ANSI escape sequence)
                System.out.print("\033[2J\033[H");
                
                // Calculate page boundaries
                int startIndex = (currentPage - 1) * pageSize;
                int endIndex = Math.min(startIndex + pageSize, totalAlerts);
                List<Map<String, Object>> pageAlerts = alerts.subList(startIndex, endIndex);

                // Check if we have alerts from multiple projects for width calculation
                boolean multiProject = alerts.stream().anyMatch(alert -> alert.containsKey("_projectId"));
                // Calculate actual table width based on column widths:
                // Multi-project: PROJECT(20) + CLUSTER(20) + METRIC(35) + STATUS(8) + VALUE(12) + SHARD(12) + CREATED(19) + spaces(6) = 132
                // Single-project: CLUSTER(20) + METRIC(35) + STATUS(8) + VALUE(12) + SHARD(12) + CREATED(19) + spaces(5) = 111
                int tableWidth = multiProject ? 132 : 111;
                
                // Print header with pagination info
                System.out.println(String.format("Atlas Alerts (Page %d of %d) - %d total alerts - Sorted by Created (newest first)", 
                    currentPage, totalPages, totalAlerts));
                System.out.println("─".repeat(tableWidth));
                
                // Print the table for current page
                printAlertsTable(pageAlerts);
                
                // Print pagination controls
                System.out.println();
                System.out.println("─".repeat(tableWidth));
                System.out.print("Navigation: ");
                if (currentPage < totalPages) System.out.print("[SPACE]next ");
                if (currentPage > 1) System.out.print("[b]ack ");
                System.out.print("[g]o to page [ESC/q]uit: ");
                
                char input;
                try {
                    input = (char) readSingleChar();
                } catch (IOException e) {
                    input = 'q'; // Exit on IO error
                }
                
                switch (input) {
                    case ' ': // Space for next page (like less)
                    case 'f': // Forward page (like less)
                    case 'j': // Down (like less/vi)
                        if (currentPage < totalPages) currentPage++;
                        break;
                    case 'b': // Back page (like less)
                    case 'u': // Up (like less)
                    case 'k': // Up (like less/vi)
                        if (currentPage > 1) currentPage--;
                        break;
                    case 'q': // Quit (like less)
                    case 'Q': // Quit (like less)
                    case 27:  // ESC key
                        System.out.println(); // Add newline before exit
                        return;
                    case 'g': // Go to page (like less)
                    case 'G': // Go to page (like less)
                        System.out.print("\nEnter page number (1-" + totalPages + "): ");
                        try {
                            String pageInput = scanner.nextLine().trim();
                            if (!pageInput.isEmpty()) {
                                int targetPage = Integer.parseInt(pageInput);
                                if (targetPage >= 1 && targetPage <= totalPages) {
                                    currentPage = targetPage;
                                } else {
                                    System.out.println("Invalid page number. Press any key to continue...");
                                    readSingleChar();
                                }
                            }
                        } catch (NumberFormatException | IOException e) {
                            try {
                                System.out.println("Invalid input. Press any key to continue...");
                                readSingleChar();
                            } catch (IOException ignored) {}
                        }
                        break;
                    case 'h': // Help (like less)
                    case '?': // Help (like less)
                        showPaginationHelp();
                        try {
                            readSingleChar(); // Wait for key press
                        } catch (IOException ignored) {}
                        break;
                    case 'r': // Refresh (like less)
                        // Just refresh current page
                        break;
                    default:
                        // Invalid key, just refresh
                        break;
                }
            }
        }

        private void printAlertsTable(List<Map<String, Object>> alerts) {
            if (alerts.isEmpty()) {
                System.out.println("No alerts found.");
                return;
            }

            // Check if we have alerts from multiple projects
            boolean multiProject = alerts.stream().anyMatch(alert -> alert.containsKey("_projectId"));

            if (multiProject) {
                System.out.printf("%-20s %-20s %-35s %-8s %-12s %-12s %-19s%n", 
                    "PROJECT", "CLUSTER", "METRIC", "STATUS", "VALUE", "SHARD", "CREATED");
                System.out.println("─".repeat(132));

                for (Map<String, Object> alert : alerts) {
                    String projectName = truncate((String) alert.get("_projectName"), 19);
                    String clusterName = getClusterNameOrDefault((String) alert.get("clusterName"), 19);
                    String metricName = formatMetricName((String) alert.get("metricName"));
                    String status = truncate((String) alert.get("status"), 7);
                    String value = formatCurrentValue(alert.get("currentValue"));
                    String shard = extractShardInfo((String) alert.get("hostnameAndPort"));
                    String created = formatTimestamp((String) alert.get("created"));

                    System.out.printf("%-20s %-20s %-35s %-8s %-12s %-12s %-19s%n", 
                        projectName, clusterName, metricName, status, value, shard, created);
                }
            } else {
                System.out.printf("%-20s %-35s %-8s %-12s %-12s %-19s%n", 
                    "CLUSTER", "METRIC", "STATUS", "VALUE", "SHARD", "CREATED");
                System.out.println("─".repeat(111));

                for (Map<String, Object> alert : alerts) {
                    String clusterName = getClusterNameOrDefault((String) alert.get("clusterName"), 19);
                    String metricName = formatMetricName((String) alert.get("metricName"));
                    String status = truncate((String) alert.get("status"), 7);
                    String value = formatCurrentValue(alert.get("currentValue"));
                    String shard = extractShardInfo((String) alert.get("hostnameAndPort"));
                    String created = formatTimestamp((String) alert.get("created"));

                    System.out.printf("%-20s %-35s %-8s %-12s %-12s %-19s%n", 
                        clusterName, metricName, status, value, shard, created);
                }
            }
        }

        private String formatCurrentValue(Object currentValue) {
            if (currentValue == null) return "N/A";
            
            if (currentValue instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) currentValue;
                Object number = valueMap.get("number");
                Object units = valueMap.get("units");
                
                if (number != null) {
                    String numStr = String.format("%.2f", ((Number) number).doubleValue());
                    String unitsStr = units != null ? units.toString() : "";
                    // Abbreviate common units
                    switch (unitsStr) {
                        case "MILLISECONDS": unitsStr = "ms"; break;
                        case "SECONDS": unitsStr = "s"; break;
                        case "GIGABYTES": unitsStr = "GB"; break;
                        case "MEGABYTES": unitsStr = "MB"; break;
                        case "BYTES": unitsStr = "B"; break;
                        case "PERCENT": unitsStr = "%"; break;
                    }
                    return unitsStr.isEmpty() ? numStr : numStr + unitsStr;
                }
            }
            
            return currentValue.toString();
        }

        private String extractShardInfo(String hostnameAndPort) {
            if (hostnameAndPort == null) return "N/A";
            
            // Extract shard info from hostname like "atlas-dzu2kh-shard-06-00.8fthp.mongodb.net:27016"
            if (hostnameAndPort.contains("shard-")) {
                String[] parts = hostnameAndPort.split("-");
                for (int i = 0; i < parts.length - 2; i++) {
                    if ("shard".equals(parts[i])) {
                        return "shard-" + parts[i + 1] + "-" + parts[i + 2].split("\\.")[0];
                    }
                }
            }
            
            // Fallback to truncated hostname
            return truncate(hostnameAndPort.split(":")[0], 11);
        }

        private String getClusterNameOrDefault(String clusterName, int maxLength) {
            if (clusterName == null || clusterName.trim().isEmpty()) {
                return "[no cluster]";
            }
            return truncate(clusterName, maxLength);
        }

        private String formatMetricName(String metricName) {
            if (metricName == null) return "N/A";
            
            // Common metric name abbreviations for better readability
            String formatted = metricName
                    .replace("NORMALIZED_SYSTEM_CPU_USER", "CPU_USER")
                    .replace("QUERY_TARGETING_SCANNED_OBJECTS_PER_RETURNED", "QUERY_TARGETING_SCANNED")
                    .replace("DISK_PARTITION_READ_LATENCY", "DISK_READ_LATENCY")
                    .replace("DISK_PARTITION_WRITE_LATENCY", "DISK_WRITE_LATENCY")
                    .replace("SYSTEM_MEMORY_AVAILABLE", "MEM_AVAILABLE")
                    .replace("SYSTEM_MEMORY_USED", "MEM_USED")
                    .replace("CONNECTIONS_CURRENT", "CONNECTIONS")
                    .replace("OPCOUNTER_", "OPS_");
            
            return formatted;
        }
        
        private int readSingleChar() throws IOException {
            // Set terminal to raw mode to read single characters
            String[] cmd = {"/bin/sh", "-c", "stty -echo raw < /dev/tty"};
            try {
                Runtime.getRuntime().exec(cmd).waitFor();
                int ch = System.in.read();
                return ch;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading input", e);
            } finally {
                // Restore terminal settings
                String[] restoreCmd = {"/bin/sh", "-c", "stty echo cooked < /dev/tty"};
                try {
                    Runtime.getRuntime().exec(restoreCmd).waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        private void showPaginationHelp() {
            System.out.println("\n\nPagination Commands (like 'less'):");
            System.out.println("  SPACE, f, j  - Next page");
            System.out.println("  b, u, k      - Previous page");
            System.out.println("  g, G         - Go to specific page");
            System.out.println("  r            - Refresh current page");
            System.out.println("  h, ?         - Show this help");
            System.out.println("  q, Q, ESC    - Quit");
            System.out.println("\nPress any key to continue...");
        }
        
        private void showInteractiveAlertsTable(List<Map<String, Object>> alerts, boolean multiProject) {
            try {
                Terminal terminal = new DefaultTerminalFactory().createTerminal();
                Screen screen = new TerminalScreen(terminal);
                screen.startScreen();

                // Create the GUI
                MultiWindowTextGUI textGUI = new MultiWindowTextGUI(screen);
                
                // Create main window
                BasicWindow mainWindow = new BasicWindow("Atlas Alerts - Interactive View");
                mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN));

                // Create panels
                Panel mainPanel = new Panel(new BorderLayout());
                Panel headerPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                Panel tablePanel = new Panel(new LinearLayout(Direction.VERTICAL));

                // Header with info
                Label titleLabel = new Label(String.format("Atlas Alerts (%d total) - Use Arrow Keys, Enter for details, Q to quit", alerts.size()));
                titleLabel.setForegroundColor(TextColor.ANSI.CYAN);
                headerPanel.addComponent(titleLabel);

                // Create table
                Table<String> alertTable = new Table<>(getTableColumns(multiProject));
                alertTable.setSelectAction(() -> showAlertDetails(textGUI, alerts.get(alertTable.getSelectedRow())));
                
                // Populate table
                for (Map<String, Object> alert : alerts) {
                    alertTable.getTableModel().addRow(formatAlertRow(alert, multiProject));
                }

                // Add components to panels
                tablePanel.addComponent(alertTable.withBorder(Borders.singleLine("Alerts")));
                
                mainPanel.addComponent(headerPanel, BorderLayout.Location.TOP);
                mainPanel.addComponent(tablePanel, BorderLayout.Location.CENTER);
                
                // Instructions panel
                Panel instructionsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                instructionsPanel.addComponent(new Label("Controls: ↑↓ Navigate | Enter: Details | Q/ESC: Quit | R: Refresh"));
                mainPanel.addComponent(instructionsPanel, BorderLayout.Location.BOTTOM);
                
                mainWindow.setComponent(mainPanel);

                // Custom key handling
                mainWindow.addWindowListener(new WindowListenerAdapter() {
                    @Override
                    public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                        if (keyStroke.getKeyType() == KeyType.Character) {
                            switch (Character.toLowerCase(keyStroke.getCharacter())) {
                                case 'q':
                                    mainWindow.close();
                                    hasBeenHandled.set(true);
                                    break;
                                case 'r':
                                    // Refresh would require re-fetching data
                                    MessageDialog.showMessageDialog(textGUI, "Refresh", "Refresh functionality would re-fetch alerts from API");
                                    hasBeenHandled.set(true);
                                    break;
                            }
                        } else if (keyStroke.getKeyType() == KeyType.Escape) {
                            // ESC key to quit
                            mainWindow.close();
                            hasBeenHandled.set(true);
                        }
                    }
                });

                // Run the GUI
                textGUI.addWindowAndWait(mainWindow);
                
                screen.stopScreen();
                terminal.close();
                
            } catch (IOException e) {
                System.err.println("Error creating interactive UI: " + e.getMessage());
                // Fallback to regular table
                printAlertsTable(alerts);
            }
        }
        
        private String[] getTableColumns(boolean multiProject) {
            if (multiProject) {
                return new String[]{"Project", "Cluster", "Metric", "Status", "Value", "Shard", "Created"};
            } else {
                return new String[]{"Cluster", "Metric", "Status", "Value", "Shard", "Created"};
            }
        }
        
        private String[] formatAlertRow(Map<String, Object> alert, boolean multiProject) {
            List<String> row = new ArrayList<>();
            
            if (multiProject) {
                row.add(truncate((String) alert.get("_projectName"), 19));
            }
            
            row.add(getClusterNameOrDefault((String) alert.get("clusterName"), 19));
            row.add(formatMetricName((String) alert.get("metricName")));
            row.add(truncate((String) alert.get("status"), 7));
            row.add(formatCurrentValue(alert.get("currentValue")));
            row.add(extractShardInfo((String) alert.get("hostnameAndPort")));
            row.add(formatTimestamp((String) alert.get("created")));
            
            return row.toArray(new String[0]);
        }
        
        private void showAlertDetails(MultiWindowTextGUI textGUI, Map<String, Object> alert) {
            StringBuilder details = new StringBuilder();
            details.append("Alert ID: ").append(alert.get("id")).append("\n");
            details.append("Status: ").append(alert.get("status")).append("\n");
            details.append("Event Type: ").append(alert.get("eventTypeName")).append("\n");
            details.append("Metric: ").append(alert.get("metricName")).append("\n");
            details.append("Created: ").append(formatTimestamp((String) alert.get("created"))).append("\n");
            details.append("Updated: ").append(formatTimestamp((String) alert.get("updated"))).append("\n");
            
            if (alert.get("clusterName") != null) {
                details.append("Cluster: ").append(alert.get("clusterName")).append("\n");
            }
            
            if (alert.get("hostnameAndPort") != null) {
                details.append("Host: ").append(alert.get("hostnameAndPort")).append("\n");
            }
            
            if (alert.get("currentValue") != null) {
                details.append("Current Value: ").append(formatCurrentValue(alert.get("currentValue"))).append("\n");
            }
            
            if (alert.get("acknowledgedUntil") != null) {
                details.append("Acknowledged Until: ").append(formatTimestamp((String) alert.get("acknowledgedUntil"))).append("\n");
                details.append("Acknowledged By: ").append(alert.get("acknowledgingUsername")).append("\n");
                if (alert.get("acknowledgementComment") != null) {
                    details.append("Comment: ").append(alert.get("acknowledgementComment")).append("\n");
                }
            }
            
            MessageDialog.showMessageDialog(textGUI, "Alert Details", details.toString(), MessageDialogButton.OK);
        }
    }

    @Command(name = "get", description = "Get details of a specific alert")
    static class GetCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert ID")
        private String alertId;
        
        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertsClient alertsClient = new AtlasAlertsClient(apiBase);

                Map<String, Object> alert = alertsClient.getAlert(projectId, alertId);

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(alert));
                } else {
                    printAlertDetails(alert);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error getting alert: " + e.getMessage());
                return 1;
            }
        }

        private void printAlertDetails(Map<String, Object> alert) {
            System.out.println("Alert Details:");
            System.out.println("─".repeat(50));
            System.out.println("ID: " + alert.get("id"));
            System.out.println("Status: " + alert.get("status"));
            System.out.println("Event Type: " + alert.get("eventTypeName"));
            System.out.println("Created: " + formatTimestamp((String) alert.get("created")));
            System.out.println("Updated: " + formatTimestamp((String) alert.get("updated")));
            
            if (alert.get("acknowledgedUntil") != null) {
                System.out.println("Acknowledged Until: " + formatTimestamp((String) alert.get("acknowledgedUntil")));
                System.out.println("Acknowledged By: " + alert.get("acknowledgingUsername"));
                if (alert.get("acknowledgementComment") != null) {
                    System.out.println("Acknowledgment Comment: " + alert.get("acknowledgementComment"));
                }
            }
            
            if (alert.get("lastNotified") != null) {
                System.out.println("Last Notified: " + formatTimestamp((String) alert.get("lastNotified")));
            }
        }
    }

    @Command(name = "acknowledge", description = "Acknowledge an alert")
    static class AcknowledgeCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert ID")
        private String alertId;
        
        @Option(names = {"--until"}, description = "Acknowledge until timestamp (ISO 8601 format). Use 'permanent' for permanent acknowledgment")
        private String acknowledgedUntil;
        
        @Option(names = {"--comment"}, description = "Acknowledgment comment (max 200 characters)")
        private String comment;
        
        @Option(names = {"--permanent"}, description = "Permanently acknowledge the alert")
        private boolean permanent;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertsClient alertsClient = new AtlasAlertsClient(apiBase);

                Map<String, Object> result;
                
                if (permanent || "permanent".equalsIgnoreCase(acknowledgedUntil)) {
                    result = alertsClient.acknowledgeAlertPermanently(projectId, alertId, comment);
                    System.out.println("Alert permanently acknowledged.");
                } else {
                    String until = acknowledgedUntil;
                    if (until == null) {
                        // Default to 24 hours from now
                        until = Instant.now().plus(24, ChronoUnit.HOURS).toString();
                    }
                    result = alertsClient.acknowledgeAlert(projectId, alertId, until, comment);
                    System.out.println("Alert acknowledged until: " + until);
                }

                System.out.println("Alert ID: " + result.get("id"));
                System.out.println("Status: " + result.get("status"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error acknowledging alert: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "unacknowledge", description = "Unacknowledge a previously acknowledged alert")
    static class UnacknowledgeCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert ID")
        private String alertId;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertsClient alertsClient = new AtlasAlertsClient(apiBase);

                Map<String, Object> result = alertsClient.unacknowledgeAlert(projectId, alertId);

                System.out.println("Alert unacknowledged.");
                System.out.println("Alert ID: " + result.get("id"));
                System.out.println("Status: " + result.get("status"));

                return 0;
            } catch (Exception e) {
                System.err.println("Error unacknowledging alert: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "for-config", description = "Get alerts for a specific alert configuration")
    static class ForConfigCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID")
        private String projectId;
        
        @Parameters(index = "1", description = "Alert Configuration ID")
        private String alertConfigId;
        
        @Option(names = {"--format"}, description = "Output format: TABLE, JSON", defaultValue = "TABLE")
        private String format;

        @Override
        public Integer call() throws Exception {
            try {
                AtlasTestConfig config = AtlasCliMain.GlobalConfig.getAtlasConfig();
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasAlertsClient alertsClient = new AtlasAlertsClient(apiBase);

                List<Map<String, Object>> alerts = alertsClient.getAlertsForConfiguration(projectId, alertConfigId);

                if ("JSON".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(alerts));
                } else {
                    System.out.println("Alerts for configuration " + alertConfigId + ":");
                    new ListCommand().printAlertsTable(alerts);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error getting alerts for configuration: " + e.getMessage());
                return 1;
            }
        }
    }

    // Utility methods
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    private static String formatTimestamp(String timestamp) {
        if (timestamp == null) return "N/A";
        try {
            Instant instant = Instant.parse(timestamp);
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant.atZone(java.time.ZoneId.systemDefault()));
        } catch (Exception e) {
            return timestamp;
        }
    }
}