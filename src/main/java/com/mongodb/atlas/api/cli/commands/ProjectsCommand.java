package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.AtlasCliMain.OutputFormat;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas project management
 * Provides comprehensive project management including CRUD operations,
 * user management, and team management.
 */
@Command(
    name = "projects",
    description = "Manage Atlas projects - Create, list, delete projects and manage users/teams",
    mixinStandardHelpOptions = true,
    subcommands = {
        ProjectsCommand.ListCommand.class,
        ProjectsCommand.GetCommand.class,
        ProjectsCommand.CreateCommand.class,
        ProjectsCommand.DeleteCommand.class,
        ProjectsCommand.UsersCommand.class,
        ProjectsCommand.TeamsCommand.class,
        ProjectsCommand.SettingsCommand.class
    }
)
public class ProjectsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli projects --help' to see available project commands");
        return 0;
    }

    @Command(name = "list", description = "List all accessible projects", mixinStandardHelpOptions = true)
    static class ListCommand implements Callable<Integer> {
        
        @Option(names = {"-n", "--name"}, description = "Filter by name pattern (partial match, case-insensitive)")
        private String nameFilter;
        
        @Option(names = {"--regex"}, description = "Treat name pattern as regex (default: partial match)")
        private boolean useRegex;
        
        @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
        private OutputFormat format;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                List<Map<String, Object>> projects = client.getAllProjects();
                
                // Apply name filtering if specified
                if (nameFilter != null && !nameFilter.trim().isEmpty()) {
                    String searchPattern = useRegex ? nameFilter : ".*" + nameFilter + ".*";
                    String caseInsensitivePattern = searchPattern.startsWith("(?i)") ? searchPattern : "(?i)" + searchPattern;
                    
                    projects = projects.stream()
                        .filter(project -> {
                            String name = (String) project.get("name");
                            return name != null && name.matches(caseInsensitivePattern);
                        })
                        .collect(java.util.stream.Collectors.toList());
                }

                if (projects.isEmpty()) {
                    if (nameFilter != null) {
                        String searchType = useRegex ? "regex pattern" : "partial name";
                        System.out.println("🔍 No projects found matching " + searchType + " '" + nameFilter + "'");
                    } else {
                        System.out.println("📭 No projects found");
                    }
                    return 0;
                }

                // Display results
                if (nameFilter != null) {
                    String searchType = useRegex ? "regex pattern" : "partial name";
                    System.out.println("🔍 Found " + projects.size() + " project(s) matching " + searchType + " '" + nameFilter + "':");
                } else {
                    System.out.println("🔍 Found " + projects.size() + " project(s):");
                }
                
                OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                printProjectsTable(projects, outputFormat);
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing projects: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get details of a specific project", mixinStandardHelpOptions = true)
    static class GetCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID or name")
        private String projectIdentifier;
        
        @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
        private OutputFormat format;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                Map<String, Object> project = null;
                
                // Try to get by ID first, then by name if that fails
                try {
                    project = client.getProject(projectIdentifier);
                } catch (Exception e) {
                    // Not found by ID, try by name
                    project = client.getProjectByName(projectIdentifier);
                }
                
                if (project == null) {
                    System.err.println("❌ Project '" + projectIdentifier + "' not found");
                    return 1;
                }
                
                System.out.println("📊 Project Details: " + project.get("name"));
                
                OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                printProjectDetails(project, outputFormat);
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting project '" + projectIdentifier + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new project", mixinStandardHelpOptions = true)
    static class CreateCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project name")
        private String projectName;
        
        @Option(names = {"--org-id", "--orgId"}, description = "Organization ID (required)")
        private String orgId;
        
        @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
        private OutputFormat format;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                return 1;
            }

            // Resolve organization ID
            String effectiveOrgId = orgId != null ? orgId : rootCmd.orgId;
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org-id or set orgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                System.out.println("🚀 Creating project '" + projectName + "' in organization " + effectiveOrgId + "...");
                
                Map<String, Object> result = client.createProject(projectName, effectiveOrgId);
                
                System.out.println("✅ Project created successfully!");
                
                OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                printProjectDetails(result, outputFormat);
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error creating project '" + projectName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a project", mixinStandardHelpOptions = true)
    static class DeleteCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID or name")
        private String projectIdentifier;
        
        @Option(names = {"-f", "--force"}, description = "Skip confirmation prompt")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                // Resolve project details for confirmation
                Map<String, Object> project = null;
                String projectId = null;
                String projectName = null;
                
                try {
                    project = client.getProject(projectIdentifier);
                    projectId = projectIdentifier;
                    projectName = (String) project.get("name");
                } catch (Exception e) {
                    // Try by name
                    project = client.getProjectByName(projectIdentifier);
                    if (project != null) {
                        projectId = (String) project.get("id");
                        projectName = (String) project.get("name");
                    }
                }
                
                if (project == null) {
                    System.err.println("❌ Project '" + projectIdentifier + "' not found");
                    return 1;
                }
                
                System.out.println("🗑️  About to delete project:");
                System.out.println("   Name: " + projectName);
                System.out.println("   ID: " + projectId);
                System.out.println("   Organization: " + project.get("orgId"));
                System.out.println();

                if (!force) {
                    System.out.println("⚠️  WARNING: This will permanently delete the project and ALL its resources!");
                    System.out.println("   This includes clusters, users, API keys, and all data.");
                    System.out.print("Type the project name '" + projectName + "' to confirm deletion: ");
                    
                    String confirmation = System.console() != null ? 
                        System.console().readLine() : 
                        new Scanner(System.in).nextLine();
                        
                    if (!projectName.equals(confirmation)) {
                        System.out.println("❌ Operation cancelled - project name did not match");
                        return 0;
                    }
                }

                System.out.println("🗑️  Deleting project '" + projectName + "'...");
                
                Map<String, Object> result = client.deleteProject(projectId);
                
                System.out.println("✅ Project deletion initiated successfully!");
                System.out.println("⏳ The project and all its resources will be permanently removed");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error deleting project '" + projectIdentifier + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "users", description = "Manage project users", mixinStandardHelpOptions = true,
             subcommands = {
                 ProjectsCommand.UsersCommand.ListUsersCommand.class,
                 ProjectsCommand.UsersCommand.AddUserCommand.class,
                 ProjectsCommand.UsersCommand.RemoveUserCommand.class
             })
    static class UsersCommand implements Callable<Integer> {
        
        @Override
        public Integer call() throws Exception {
            System.out.println("Use 'atlas-cli projects users --help' to see available user management commands");
            return 0;
        }

        @Command(name = "list", description = "List users in a project")
        static class ListUsersCommand implements Callable<Integer> {
            
            @Parameters(index = "0", description = "Project ID or name")
            private String projectIdentifier;
            
            @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
            private OutputFormat format;

            @Override
            public Integer call() throws Exception {
                AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
                if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                    System.err.println("❌ Error: Atlas API credentials are required.");
                    return 1;
                }

                try {
                    AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                    AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                    
                    String projectId = resolveProjectId(client, projectIdentifier);
                    if (projectId == null) {
                        System.err.println("❌ Project '" + projectIdentifier + "' not found");
                        return 1;
                    }
                    
                    List<Map<String, Object>> users = client.getProjectUsers(projectId);
                    
                    if (users.isEmpty()) {
                        System.out.println("📭 No users found in project " + projectIdentifier);
                        return 0;
                    }
                    
                    System.out.println("👥 Found " + users.size() + " user(s) in project " + projectIdentifier + ":");
                    
                    OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                    printUsersTable(users, outputFormat);
                    
                    return 0;
                } catch (Exception e) {
                    System.err.println("❌ Error listing users: " + e.getMessage());
                    if (GlobalConfig.isVerbose()) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
        }

        @Command(name = "add", description = "Add a user to a project")
        static class AddUserCommand implements Callable<Integer> {
            
            @Parameters(index = "0", description = "Project ID or name")
            private String projectIdentifier;
            
            @Parameters(index = "1", description = "Username")
            private String username;
            
            @Option(names = {"--roles"}, description = "Roles to assign (comma-separated)", split = ",", required = true)
            private List<String> roles;

            @Override
            public Integer call() throws Exception {
                AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
                if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                    System.err.println("❌ Error: Atlas API credentials are required.");
                    return 1;
                }

                try {
                    AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                    AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                    
                    String projectId = resolveProjectId(client, projectIdentifier);
                    if (projectId == null) {
                        System.err.println("❌ Project '" + projectIdentifier + "' not found");
                        return 1;
                    }
                    
                    System.out.println("👤 Adding user '" + username + "' to project " + projectIdentifier + 
                                     " with roles: " + String.join(", ", roles));
                    
                    Map<String, Object> result = client.addUserToProject(projectId, username, roles);
                    
                    System.out.println("✅ User added successfully!");
                    
                    return 0;
                } catch (Exception e) {
                    System.err.println("❌ Error adding user: " + e.getMessage());
                    if (GlobalConfig.isVerbose()) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
        }

        @Command(name = "remove", description = "Remove a user from a project")
        static class RemoveUserCommand implements Callable<Integer> {
            
            @Parameters(index = "0", description = "Project ID or name")
            private String projectIdentifier;
            
            @Parameters(index = "1", description = "User ID")
            private String userId;

            @Override
            public Integer call() throws Exception {
                AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
                if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                    System.err.println("❌ Error: Atlas API credentials are required.");
                    return 1;
                }

                try {
                    AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                    AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                    
                    String projectId = resolveProjectId(client, projectIdentifier);
                    if (projectId == null) {
                        System.err.println("❌ Project '" + projectIdentifier + "' not found");
                        return 1;
                    }
                    
                    System.out.println("🗑️  Removing user '" + userId + "' from project " + projectIdentifier);
                    
                    Map<String, Object> result = client.removeUserFromProject(projectId, userId);
                    
                    System.out.println("✅ User removed successfully!");
                    
                    return 0;
                } catch (Exception e) {
                    System.err.println("❌ Error removing user: " + e.getMessage());
                    if (GlobalConfig.isVerbose()) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
        }
    }

    @Command(name = "teams", description = "List project teams", mixinStandardHelpOptions = true)
    static class TeamsCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID or name")
        private String projectIdentifier;
        
        @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
        private OutputFormat format;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                String projectId = resolveProjectId(client, projectIdentifier);
                if (projectId == null) {
                    System.err.println("❌ Project '" + projectIdentifier + "' not found");
                    return 1;
                }
                
                List<Map<String, Object>> teams = client.getProjectTeams(projectId);
                
                if (teams.isEmpty()) {
                    System.out.println("👥 No teams found in project " + projectIdentifier);
                    return 0;
                }
                
                System.out.println("👥 Found " + teams.size() + " team(s) in project " + projectIdentifier + ":");
                
                OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                printTeamsTable(teams, outputFormat);
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing teams: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "settings", description = "Get project settings", mixinStandardHelpOptions = true)
    static class SettingsCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Project ID or name")
        private String projectIdentifier;
        
        @Option(names = {"--format"}, description = "Output format: ${COMPLETION-CANDIDATES} (default: TABLE)")
        private OutputFormat format;

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient client = new AtlasProjectsClient(apiBase);
                
                String projectId = resolveProjectId(client, projectIdentifier);
                if (projectId == null) {
                    System.err.println("❌ Project '" + projectIdentifier + "' not found");
                    return 1;
                }
                
                Map<String, Object> settings = client.getProjectSettings(projectId);
                
                System.out.println("⚙️  Project Settings for " + projectIdentifier + ":");
                
                OutputFormat outputFormat = format != null ? format : GlobalConfig.getFormat();
                printSettings(settings, outputFormat);
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting project settings: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    // Utility methods
    
    private static void printProjectsTable(List<Map<String, Object>> projects, OutputFormat format) {
        if (format == OutputFormat.JSON) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(projects);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
            return;
        }
        
        // Table format
        System.out.printf("%-25s %-25s %-15s %-10s%n", 
                         "NAME", "ID", "CREATED", "CLUSTERS");
        System.out.println("─".repeat(80));
        
        for (Map<String, Object> project : projects) {
            String name = getString(project, "name", "");
            String id = getString(project, "id", "");
            String created = formatTimestamp(getString(project, "created", null));
            String clusterCount = project.containsKey("clusterCount") ? 
                project.get("clusterCount").toString() : "N/A";
            
            System.out.printf("%-25s %-25s %-15s %-10s%n", 
                             truncate(name, 24), truncate(id, 24), created, clusterCount);
        }
    }
    
    private static void printProjectDetails(Map<String, Object> project, OutputFormat format) {
        if (format == OutputFormat.JSON) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(project);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
            return;
        }
        
        System.out.println("ID: " + getString(project, "id", "N/A"));
        System.out.println("Name: " + getString(project, "name", "N/A"));
        System.out.println("Organization ID: " + getString(project, "orgId", "N/A"));
        System.out.println("Created: " + formatTimestamp(getString(project, "created", null)));
        
        if (project.containsKey("clusterCount")) {
            System.out.println("Cluster Count: " + project.get("clusterCount"));
        }
        
        if (project.containsKey("links")) {
            System.out.println("Links: " + project.get("links"));
        }
    }
    
    private static void printUsersTable(List<Map<String, Object>> users, OutputFormat format) {
        if (format == OutputFormat.JSON) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(users);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
            return;
        }
        
        System.out.printf("%-25s %-30s %-20s %-30s%n", 
                         "USERNAME", "EMAIL", "ROLES", "ID");
        System.out.println("─".repeat(110));
        
        for (Map<String, Object> user : users) {
            String username = getString(user, "username", "");
            String email = getString(user, "emailAddress", "");
            String roles = formatRoles((List<String>) user.get("roles"));
            String id = getString(user, "id", "");
            
            System.out.printf("%-25s %-30s %-20s %-30s%n", 
                             truncate(username, 24), truncate(email, 29), 
                             truncate(roles, 19), truncate(id, 29));
        }
    }
    
    private static void printTeamsTable(List<Map<String, Object>> teams, OutputFormat format) {
        if (format == OutputFormat.JSON) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(teams);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
            return;
        }
        
        System.out.printf("%-30s %-25s %-20s%n", 
                         "TEAM NAME", "ID", "ROLES");
        System.out.println("─".repeat(80));
        
        for (Map<String, Object> team : teams) {
            String name = getString(team, "name", "");
            String id = getString(team, "id", "");
            String roles = formatRoles((List<String>) team.get("roleNames"));
            
            System.out.printf("%-30s %-25s %-20s%n", 
                             truncate(name, 29), truncate(id, 24), truncate(roles, 19));
        }
    }
    
    private static void printSettings(Map<String, Object> settings, OutputFormat format) {
        if (format == OutputFormat.JSON) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
            return;
        }
        
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
    
    private static String resolveProjectId(AtlasProjectsClient client, String projectIdentifier) {
        try {
            // Try as project ID first
            Map<String, Object> project = client.getProject(projectIdentifier);
            return projectIdentifier;
        } catch (Exception e) {
            // Try as project name
            try {
                Map<String, Object> project = client.getProjectByName(projectIdentifier);
                return project != null ? (String) project.get("id") : null;
            } catch (Exception e2) {
                return null;
            }
        }
    }
    
    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    private static String formatTimestamp(String timestamp) {
        if (timestamp == null) return "N/A";
        try {
            java.time.Instant instant = java.time.Instant.parse(timestamp);
            return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(java.time.ZoneId.systemDefault()));
        } catch (Exception e) {
            return timestamp.length() > 10 ? timestamp.substring(0, 10) : timestamp;
        }
    }
    
    private static String formatRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "N/A";
        return String.join(", ", roles);
    }
}