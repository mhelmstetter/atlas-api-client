package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasFlexClustersClient;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas Flex cluster management (serverless clusters)
 */
@Command(
    name = "flex-clusters",
    description = "Manage Atlas Flex clusters (serverless, pay-as-you-go)",
    mixinStandardHelpOptions = true,
    subcommands = {
        FlexClustersCommand.ListCommand.class,
        FlexClustersCommand.GetCommand.class,
        FlexClustersCommand.CreateCommand.class,
        FlexClustersCommand.DeleteCommand.class,
        FlexClustersCommand.StatusCommand.class
    }
)
public class FlexClustersCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli flex-clusters --help' to see available Flex cluster commands");
        return 0;
    }

    @Command(name = "list", description = "List all Flex clusters in a project")
    static class ListCommand implements Callable<Integer> {
        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasFlexClustersClient client = new AtlasFlexClustersClient(apiBase);
                
                List<Map<String, Object>> clusters = client.getFlexClusters(effectiveProjectId);
                
                if (clusters.isEmpty()) {
                    System.out.println("📭 No Flex clusters found in project " + effectiveProjectId);
                    return 0;
                }

                System.out.println("🔍 Found " + clusters.size() + " Flex cluster(s) in project " + effectiveProjectId);
                OutputFormatter.printClusters(clusters, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing Flex clusters: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get details of a specific Flex cluster")
    static class GetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Flex cluster name")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasFlexClustersClient client = new AtlasFlexClustersClient(apiBase);
                
                Map<String, Object> cluster = client.getFlexCluster(effectiveProjectId, clusterName);
                
                System.out.println("📊 Flex Cluster Details: " + clusterName);
                OutputFormatter.printClusterDetails(cluster, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting Flex cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new Flex cluster")
    static class CreateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Flex cluster name")
        private String clusterName;

        @Option(names = {"-v", "--version"}, 
                description = "MongoDB version (optional for Flex)",
                defaultValue = "7.0")
        private String mongoVersion;

        @Option(names = {"-r", "--region"}, 
                description = "Cloud region",
                defaultValue = "US_EAST_1")
        private String region;

        @Option(names = {"--provider"}, 
                description = "Cloud provider (AWS, GCP, AZURE)",
                defaultValue = "AWS")
        private String cloudProvider;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"--wait"}, description = "Wait for cluster to be ready")
        private boolean waitForReady;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasFlexClustersClient client = new AtlasFlexClustersClient(apiBase);
                
                System.out.println("🚀 Creating Flex cluster '" + clusterName + "'...");
                System.out.println("   Type: Flex (serverless, pay-as-you-go)");
                System.out.println("   Version: " + mongoVersion);
                System.out.println("   Region: " + region);
                System.out.println("   Provider: " + cloudProvider);
                
                Map<String, Object> cluster = client.createFlexCluster(
                    effectiveProjectId, clusterName, mongoVersion, region, cloudProvider);
                
                System.out.println("✅ Flex cluster creation initiated successfully!");
                OutputFormatter.printClusterDetails(cluster, GlobalConfig.getFormat());
                
                if (waitForReady) {
                    System.out.println("⏳ Waiting for Flex cluster to be ready...");
                    if (client.waitForFlexClusterState(effectiveProjectId, clusterName, "IDLE", 600)) {
                        System.out.println("✅ Flex cluster '" + clusterName + "' is ready!");
                    } else {
                        System.out.println("⚠️ Timeout waiting for Flex cluster to be ready");
                        return 2;
                    }
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error creating Flex cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a Flex cluster")
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Flex cluster name")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-f", "--force"}, description = "Skip confirmation prompt")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            if (!force) {
                System.out.print("⚠️ Are you sure you want to delete Flex cluster '" + clusterName + "'? [y/N]: ");
                String confirmation = System.console() != null ? 
                    System.console().readLine() : 
                    new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirmation) && !"yes".equalsIgnoreCase(confirmation)) {
                    System.out.println("❌ Operation cancelled");
                    return 0;
                }
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasFlexClustersClient client = new AtlasFlexClustersClient(apiBase);
                
                System.out.println("🗑️ Deleting Flex cluster '" + clusterName + "'...");
                
                client.deleteFlexCluster(effectiveProjectId, clusterName);
                
                System.out.println("✅ Flex cluster deletion initiated successfully!");
                System.out.println("⏳ The cluster will be completely removed in a few minutes");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error deleting Flex cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "status", description = "Get Flex cluster status and wait for specific state")
    static class StatusCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Flex cluster name")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-w", "--wait"}, description = "Wait for specific state (IDLE, CREATING, etc.)")
        private String waitForState;

        @Option(names = {"--timeout"}, description = "Timeout in seconds", defaultValue = "600")
        private int timeoutSeconds;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveProjectId = projectId != null ? projectId : config.getTestProjectId();
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set testProjectId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasFlexClustersClient client = new AtlasFlexClustersClient(apiBase);
                
                Map<String, Object> cluster = client.getFlexCluster(effectiveProjectId, clusterName);
                String currentState = (String) cluster.get("stateName");
                
                System.out.println("📊 Flex Cluster Status: " + clusterName);
                System.out.println("   Current State: " + currentState);
                System.out.println("   Type: Flex (serverless)");
                
                if (waitForState != null) {
                    if (waitForState.equals(currentState)) {
                        System.out.println("✅ Flex cluster is already in state: " + waitForState);
                        return 0;
                    }
                    
                    System.out.println("⏳ Waiting for Flex cluster to reach state: " + waitForState);
                    System.out.println("   Timeout: " + timeoutSeconds + " seconds");
                    
                    if (client.waitForFlexClusterState(effectiveProjectId, clusterName, waitForState, timeoutSeconds)) {
                        System.out.println("✅ Flex cluster reached state: " + waitForState);
                        return 0;
                    } else {
                        System.out.println("⚠️ Timeout waiting for Flex cluster to reach state: " + waitForState);
                        return 2;
                    }
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting Flex cluster status '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }
}