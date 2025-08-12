package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.clients.AtlasLogsClient;
import com.mongodb.atlas.api.cli.AtlasCliMain;

import java.util.concurrent.Callable;

/**
 * Simple test command to verify config loading works
 */
@Command(name = "logs-test", description = "Test logs command with simplified config")
public class LogsTestCommand implements Callable<Integer> {

    @ParentCommand
    private AtlasCliMain parent;

    @Option(names = {"-p", "--project"}, description = "Project ID")
    private String projectId;

    @Override
    public Integer call() throws Exception {
        System.out.println("🔧 Testing configuration loading...");
        
        // Check if parent is populated
        if (parent == null) {
            System.err.println("❌ Parent command not found");
            return 1;
        }
        
        // Check if API keys are populated from config file
        System.out.println("API Public Key: " + (parent.apiPublicKey != null ? "✅ Set" : "❌ Missing"));
        System.out.println("API Private Key: " + (parent.apiPrivateKey != null ? "✅ Set" : "❌ Missing"));
        System.out.println("Project IDs: " + parent.projectIds);
        System.out.println("Project Names: " + parent.includeProjectNames);
        
        if (parent.apiPublicKey == null || parent.apiPrivateKey == null) {
            System.err.println("❌ API credentials missing");
            return 1;
        }
        
        // Try to create API client
        try {
            AtlasApiClient apiClient = new AtlasApiClient(parent.apiPublicKey, parent.apiPrivateKey);
            System.out.println("✅ API client created successfully");
            
            // Try to get a logs client
            AtlasLogsClient logsClient = apiClient.logs();
            System.out.println("✅ Logs client created successfully");
            
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Error creating API client: " + e.getMessage());
            return 1;
        }
    }
}