package com.mongodb.atlas.api.cli.utils;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Utility class for handling project selection in CLI commands
 */
public class ProjectSelectionUtils {

    /**
     * Interactive project selection for single project selection
     * 
     * @param scanner Scanner for user input
     * @param config Atlas configuration
     * @return Selected project ID, or null if cancelled/error
     */
    public static String selectSingleProject(Scanner scanner, AtlasTestConfig config) {
        try {
            System.out.println("🏢 Project Selection");
            System.out.println("─".repeat(30));
            
            AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
            
            List<Map<String, Object>> projects = projectsClient.getAllProjects();
            
            if (projects.isEmpty()) {
                System.err.println("❌ No projects found for this account");
                return null;
            }

            System.out.println("Available projects:");
            for (int i = 0; i < projects.size(); i++) {
                String name = (String) projects.get(i).get("name");
                String id = (String) projects.get(i).get("id");
                System.out.printf("  %d. %s (%s)%n", i + 1, name, id);
            }

            System.out.print("\nSelect project [1-" + projects.size() + "]: ");
            String choice = scanner.nextLine().trim();
            
            try {
                int index = Integer.parseInt(choice) - 1;
                if (index >= 0 && index < projects.size()) {
                    String selectedId = (String) projects.get(index).get("id");
                    String selectedName = (String) projects.get(index).get("name");
                    System.out.println("✅ Selected project: " + selectedName + " (" + selectedId + ")");
                    System.out.println();
                    return selectedId;
                } else {
                    System.err.println("❌ Invalid selection");
                    return null;
                }
            } catch (NumberFormatException e) {
                System.err.println("❌ Invalid number format");
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Error fetching projects: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve effective project ID for a command, handling interactive selection if needed
     * 
     * @param projectIdParam Explicit project ID from command line
     * @param config Atlas configuration
     * @param allowInteractive Whether to allow interactive selection if no project is configured
     * @param scanner Scanner for interactive input (required if allowInteractive is true)
     * @return Effective project ID to use, or null if none could be determined
     */
    public static String resolveProjectId(String projectIdParam, AtlasTestConfig config, 
                                        boolean allowInteractive, Scanner scanner) {
        // 1. Use explicit project parameter
        if (projectIdParam != null && !projectIdParam.trim().isEmpty()) {
            return projectIdParam.trim();
        }
        
        // 2. Use configured project ID
        String configuredProjectId = config.getTestProjectId();
        if (configuredProjectId != null && !configuredProjectId.trim().isEmpty()) {
            return configuredProjectId.trim();
        }
        
        // 3. Use projects from GlobalConfig
        List<String> globalProjectIds = GlobalConfig.getProjectIds();
        if (globalProjectIds != null && !globalProjectIds.isEmpty()) {
            // For single project commands, use first project
            return globalProjectIds.get(0);
        }
        
        // 4. Resolve project names to IDs
        List<String> globalProjectNames = GlobalConfig.getIncludeProjectNames();
        if (globalProjectNames != null && !globalProjectNames.isEmpty()) {
            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                for (Map<String, Object> project : projects) {
                    String projectName = (String) project.get("name");
                    if (globalProjectNames.contains(projectName)) {
                        return (String) project.get("id");
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️  Error resolving project names: " + e.getMessage());
            }
        }
        
        // 5. Interactive selection if allowed
        if (allowInteractive && scanner != null) {
            return selectSingleProject(scanner, config);
        }
        
        // No project could be determined
        return null;
    }

    /**
     * Get list of projects to process for multi-project commands (like AlertsCommand)
     * 
     * @param projectIdParam Explicit project ID from command line  
     * @param config Atlas configuration
     * @return List of project IDs to process
     * @throws Exception if no projects can be determined
     */
    public static List<String> resolveProjectsToProcess(String projectIdParam, AtlasTestConfig config) throws Exception {
        List<String> projectsToProcess = new ArrayList<>();
        
        if (projectIdParam != null) {
            // Use specified project ID
            projectsToProcess.add(projectIdParam);
        } else if (GlobalConfig.getProjectIds() != null && !GlobalConfig.getProjectIds().isEmpty()) {
            // Use project IDs from command line
            projectsToProcess.addAll(GlobalConfig.getProjectIds());
        } else if (GlobalConfig.getIncludeProjectNames() != null && !GlobalConfig.getIncludeProjectNames().isEmpty()) {
            // Use project names - need to resolve to IDs
            AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
            List<Map<String, Object>> projects = projectsClient.getAllProjects();
            
            for (Map<String, Object> project : projects) {
                String projectName = (String) project.get("name");
                if (GlobalConfig.getIncludeProjectNames().contains(projectName)) {
                    projectsToProcess.add((String) project.get("id"));
                }
            }
            
            if (projectsToProcess.isEmpty()) {
                throw new Exception("No matching projects found for names: " + GlobalConfig.getIncludeProjectNames());
            }
        } else {
            throw new Exception("No project specified. Use --project, --projectIds, --includeProjectNames, or configure testProjectId.");
        }
        
        return projectsToProcess;
    }

    /**
     * Create mapping of project IDs to names for display purposes
     * 
     * @param config Atlas configuration
     * @return Map of project ID to project name
     */
    public static Map<String, String> getProjectIdToNameMapping(AtlasTestConfig config) {
        Map<String, String> projectIdToName = new HashMap<>();
        
        try {
            AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
            AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
            List<Map<String, Object>> allProjects = projectsClient.getAllProjects();
            
            for (Map<String, Object> project : allProjects) {
                projectIdToName.put((String) project.get("id"), (String) project.get("name"));
            }
        } catch (Exception e) {
            System.err.println("⚠️  Error fetching project names: " + e.getMessage());
        }
        
        return projectIdToName;
    }

    /**
     * Validate that API credentials are configured
     * 
     * @param config Atlas configuration
     * @return true if credentials are available, false otherwise
     */
    public static boolean validateCredentials(AtlasTestConfig config) {
        if (config.getApiPublicKey() == null || config.getApiPublicKey().trim().isEmpty() ||
            config.getApiPrivateKey() == null || config.getApiPrivateKey().trim().isEmpty()) {
            System.err.println("❌ Error: Atlas API credentials are required.");
            System.err.println("   Set apiPublicKey and apiPrivateKey in atlas-client.properties");
            System.err.println("   or use environment variables ATLAS_API_PUBLIC_KEY and ATLAS_API_PRIVATE_KEY");
            return false;
        }
        return true;
    }
}