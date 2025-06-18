package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasClustersClient;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas cluster management
 */
@Command(
    name = "clusters",
    description = "Manage Atlas clusters (M10+ dedicated clusters)",
    subcommands = {
        ClustersCommand.ListCommand.class,
        ClustersCommand.GetCommand.class,
        ClustersCommand.CreateCommand.class,
        ClustersCommand.UpdateCommand.class,
        ClustersCommand.DeleteCommand.class,
        ClustersCommand.StatusCommand.class
    }
)
public class ClustersCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli clusters --help' to see available cluster commands");
        return 0;
    }

    @Command(name = "list", description = "List all clusters in a project")
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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                List<Map<String, Object>> clusters = client.getClusters(effectiveProjectId);
                
                if (clusters.isEmpty()) {
                    System.out.println("📭 No clusters found in project " + effectiveProjectId);
                    return 0;
                }

                System.out.println("🔍 Found " + clusters.size() + " cluster(s) in project " + effectiveProjectId);
                OutputFormatter.printClusters(clusters, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing clusters: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get details of a specific cluster")
    static class GetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                Map<String, Object> cluster = client.getCluster(effectiveProjectId, clusterName);
                
                System.out.println("📊 Cluster Details: " + clusterName);
                OutputFormatter.printClusterDetails(cluster, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new cluster")
    static class CreateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
        private String clusterName;

        @Option(names = {"-s", "--size"}, 
                description = "Instance size (M10, M20, M30, M40, M50, M60, M80, M140, M200, M300)",
                defaultValue = "M10")
        private String instanceSize;

        @Option(names = {"-v", "--version"}, 
                description = "MongoDB version",
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

        @Option(names = {"--sharded"}, description = "Create a sharded cluster")
        private boolean sharded;

        @Option(names = {"--num-shards"}, description = "Number of shards (1-70, only for sharded clusters)", defaultValue = "2")
        private int numShards;

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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                String clusterType = sharded ? "sharded cluster" : "replica set cluster";
                System.out.println("🚀 Creating " + clusterType + " '" + clusterName + "'...");
                System.out.println("   Type: " + (sharded ? "Sharded" : "Replica Set"));
                System.out.println("   Size: " + instanceSize);
                if (sharded) {
                    System.out.println("   Shards: " + numShards);
                    System.out.println("   Total Nodes: " + (numShards * 3) + " (3 per shard)");
                    
                    // Validate instance size for sharded clusters
                    String[] validSizes = com.mongodb.atlas.api.clients.AtlasClustersClient.getShardedClusterInstanceSizes();
                    boolean validSize = false;
                    for (String validSize1 : validSizes) {
                        if (validSize1.equalsIgnoreCase(instanceSize)) {
                            validSize = true;
                            break;
                        }
                    }
                    if (!validSize) {
                        System.err.println("❌ Error: Sharded clusters require instance size M30 or larger. Provided: " + instanceSize);
                        return 1;
                    }
                }
                System.out.println("   Version: " + mongoVersion);
                System.out.println("   Region: " + region);
                System.out.println("   Provider: " + cloudProvider);
                
                Map<String, Object> cluster;
                if (sharded) {
                    cluster = client.createShardedCluster(
                        effectiveProjectId, clusterName, instanceSize, mongoVersion, region, cloudProvider, numShards);
                } else {
                    cluster = client.createCluster(
                        effectiveProjectId, clusterName, instanceSize, mongoVersion, region, cloudProvider);
                }
                
                System.out.println("✅ Cluster creation initiated successfully!");
                OutputFormatter.printClusterDetails(cluster, GlobalConfig.getFormat());
                
                if (waitForReady) {
                    System.out.println("⏳ Waiting for cluster to be ready...");
                    if (client.waitForClusterState(effectiveProjectId, clusterName, "IDLE", 1800)) {
                        System.out.println("✅ Cluster '" + clusterName + "' is ready!");
                    } else {
                        System.out.println("⚠️ Timeout waiting for cluster to be ready");
                        return 2;
                    }
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error creating cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "update", description = "Update cluster configuration")
    static class UpdateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
        private String clusterName;

        @Option(names = {"-s", "--size"}, description = "New instance size")
        private String instanceSize;

        @Option(names = {"--disk-size"}, description = "Disk size in GB")
        private Integer diskSizeGB;

        @Option(names = {"--enable-backup"}, description = "Enable backup")
        private Boolean enableBackup;

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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                System.out.println("🔧 Updating cluster '" + clusterName + "'...");
                
                Map<String, Object> updateSpec = new java.util.HashMap<>();
                if (instanceSize != null) {
                    updateSpec.put("instanceSizeName", instanceSize);
                    System.out.println("   New size: " + instanceSize);
                }
                if (diskSizeGB != null) {
                    updateSpec.put("diskSizeGB", diskSizeGB);
                    System.out.println("   New disk size: " + diskSizeGB + "GB");
                }
                if (enableBackup != null) {
                    updateSpec.put("backupEnabled", enableBackup);
                    System.out.println("   Backup enabled: " + enableBackup);
                }
                
                if (updateSpec.isEmpty()) {
                    System.err.println("❌ Error: No update parameters specified");
                    return 1;
                }
                
                Map<String, Object> result = client.modifyCluster(effectiveProjectId, clusterName, updateSpec);
                
                System.out.println("✅ Cluster update initiated successfully!");
                OutputFormatter.printClusterDetails(result, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error updating cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a cluster")
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
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
                System.out.print("⚠️ Are you sure you want to delete cluster '" + clusterName + "'? [y/N]: ");
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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                System.out.println("🗑️ Deleting cluster '" + clusterName + "'...");
                
                Map<String, Object> result = client.deleteCluster(effectiveProjectId, clusterName);
                
                System.out.println("✅ Cluster deletion initiated successfully!");
                System.out.println("⏳ The cluster will be completely removed in a few minutes");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error deleting cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "status", description = "Get cluster status and wait for specific state")
    static class StatusCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-w", "--wait"}, description = "Wait for specific state (IDLE, CREATING, UPDATING, etc.)")
        private String waitForState;

        @Option(names = {"--timeout"}, description = "Timeout in seconds", defaultValue = "1800")
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
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                Map<String, Object> cluster = client.getCluster(effectiveProjectId, clusterName);
                String currentState = (String) cluster.get("stateName");
                
                System.out.println("📊 Cluster Status: " + clusterName);
                System.out.println("   Current State: " + currentState);
                System.out.println("   MongoDB Version: " + cluster.get("mongoDBVersion"));
                System.out.println("   Instance Size: " + cluster.get("instanceSizeName"));
                
                if (waitForState != null) {
                    if (waitForState.equals(currentState)) {
                        System.out.println("✅ Cluster is already in state: " + waitForState);
                        return 0;
                    }
                    
                    System.out.println("⏳ Waiting for cluster to reach state: " + waitForState);
                    System.out.println("   Timeout: " + timeoutSeconds + " seconds");
                    
                    if (client.waitForClusterState(effectiveProjectId, clusterName, waitForState, timeoutSeconds)) {
                        System.out.println("✅ Cluster reached state: " + waitForState);
                        return 0;
                    } else {
                        System.out.println("⚠️ Timeout waiting for cluster to reach state: " + waitForState);
                        return 2;
                    }
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting cluster status '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }
}