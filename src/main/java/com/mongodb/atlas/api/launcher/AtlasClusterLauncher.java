package com.mongodb.atlas.api.launcher;

import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;

/**
 * Interactive Atlas cluster launcher that prompts for credentials and cluster configuration
 */
public class AtlasClusterLauncher {
    
    private static final Logger logger = LoggerFactory.getLogger(AtlasClusterLauncher.class);
    private final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        AtlasClusterLauncher launcher = new AtlasClusterLauncher();
        launcher.runInteractive();
    }
    
    public void runInteractive() {
        System.out.println("🍃 Welcome to MongoDB Atlas Cluster Launcher");
        System.out.println("============================================");
        System.out.println();
        
        try {
            // Prompt for Atlas API credentials
            String apiPublicKey = promptForInput("Atlas API Public Key", null);
            String apiPrivateKey = promptForInput("Atlas API Private Key", null);
            
            // Create API client and test connection
            System.out.println("🔑 Authenticating with Atlas API...");
            AtlasApiClient apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey);
            
            // Prompt for project ID
            String projectId = promptForInput("Atlas Project ID", null);
            
            // Test API connectivity by listing projects
            try {
                System.out.println("✅ Testing API connection...");
                Map<String, String> projects = apiClient.clusters().getProjects(java.util.Set.of("*"));
                System.out.println("✅ Successfully connected to Atlas API");
            } catch (Exception e) {
                System.err.println("❌ Failed to connect to Atlas API: " + e.getMessage());
                System.err.println("Please check your API credentials and try again.");
                return;
            }
            
            // Prompt for cluster details
            System.out.println();
            System.out.println("📋 Cluster Configuration");
            System.out.println("------------------------");
            
            String clusterName = promptForInput("Cluster Name", "test-cluster");
            String instanceSize = promptForChoice("Instance Size", 
                new String[]{"M0", "M2", "M5", "M10", "M20", "M30"}, "M10");
            String mongoVersion = promptForChoice("MongoDB Version", 
                new String[]{"6.0", "7.0", "8.0"}, "7.0");
            String cloudProvider = promptForChoice("Cloud Provider", 
                new String[]{"AWS", "GCP", "AZURE"}, "AWS");
            String region = promptForChoice("Region", 
                new String[]{"US_EAST_1", "US_WEST_2", "EU_WEST_1", "AP_SOUTHEAST_1"}, "US_EAST_1");
            
            // Display summary
            System.out.println();
            System.out.println("📊 Cluster Summary");
            System.out.println("------------------");
            System.out.println("Name: " + clusterName);
            System.out.println("Instance Size: " + instanceSize);
            System.out.println("MongoDB Version: " + mongoVersion);
            System.out.println("Cloud Provider: " + cloudProvider);
            System.out.println("Region: " + region);
            System.out.println("Project ID: " + projectId);
            System.out.println();
            
            if (!promptForConfirmation("Proceed with cluster creation?", true)) {
                System.out.println("❌ Cluster creation cancelled.");
                return;
            }
            
            // Create the cluster
            System.out.println("🚀 Creating Atlas cluster '" + clusterName + "'...");
            System.out.println("This may take several minutes...");
            
            try {
                Map<String, Object> clusterResponse = apiClient.clusters().createCluster(
                    projectId, clusterName, instanceSize, mongoVersion, region, cloudProvider);
                
                System.out.println("✅ Cluster creation initiated successfully!");
                System.out.println("Cluster ID: " + clusterResponse.get("id"));
                System.out.println("Status: " + clusterResponse.get("stateName"));
                
                // Wait for cluster to be ready
                if (promptForConfirmation("Wait for cluster to be ready?", true)) {
                    System.out.println("⏳ Waiting for cluster to reach IDLE state...");
                    System.out.println("This typically takes 7-10 minutes for new clusters.");
                    
                    boolean isReady = apiClient.clusters().waitForClusterState(
                        projectId, clusterName, "IDLE", 900); // 15 minute timeout
                    
                    if (isReady) {
                        System.out.println("🎉 Cluster is ready!");
                        
                        // Get final cluster details
                        Map<String, Object> finalCluster = apiClient.clusters().getCluster(projectId, clusterName);
                        String connectionString = (String) finalCluster.get("connectionStrings");
                        if (connectionString != null) {
                            System.out.println("Connection String: " + connectionString);
                        }
                    } else {
                        System.out.println("⏰ Timeout waiting for cluster to be ready.");
                        System.out.println("The cluster is still being created. Check Atlas console for status.");
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ Failed to create cluster: " + e.getMessage());
                logger.error("Cluster creation failed", e);
            }
            
        } catch (Exception e) {
            System.err.println("❌ An error occurred: " + e.getMessage());
            logger.error("Application error", e);
        } finally {
            scanner.close();
        }
    }
    
    private String promptForInput(String prompt, String defaultValue) {
        if (defaultValue != null) {
            System.out.print(prompt + " [" + defaultValue + "]: ");
        } else {
            System.out.print(prompt + ": ");
        }
        
        String input = scanner.nextLine().trim();
        if (input.isEmpty() && defaultValue != null) {
            return defaultValue;
        }
        return input;
    }
    
    private String promptForChoice(String prompt, String[] options, String defaultValue) {
        System.out.println(prompt + ":");
        for (int i = 0; i < options.length; i++) {
            String marker = options[i].equals(defaultValue) ? " (default)" : "";
            System.out.println("  " + (i + 1) + ". " + options[i] + marker);
        }
        
        while (true) {
            System.out.print("Enter choice (1-" + options.length + ") [" + defaultValue + "]: ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                return defaultValue;
            }
            
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= options.length) {
                    return options[choice - 1];
                }
            } catch (NumberFormatException e) {
                // Fall through to error message
            }
            
            System.out.println("Invalid choice. Please enter a number between 1 and " + options.length);
        }
    }
    
    private boolean promptForConfirmation(String prompt, boolean defaultValue) {
        String defaultStr = defaultValue ? "Y/n" : "y/N";
        System.out.print(prompt + " [" + defaultStr + "]: ");
        
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.isEmpty()) {
            return defaultValue;
        }
        
        return input.startsWith("y");
    }
}