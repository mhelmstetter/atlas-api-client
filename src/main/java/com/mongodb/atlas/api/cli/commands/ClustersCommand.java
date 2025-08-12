package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasClustersClient;
import com.mongodb.atlas.api.clients.AtlasFlexClustersClient;
import com.mongodb.atlas.api.clients.AtlasProjectsClient;
import com.mongodb.atlas.api.cli.AtlasCliMain;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.AtlasCliMain.OutputFormat;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI commands for Atlas cluster management
 */
@Command(
    name = "clusters",
    description = "Manage Atlas clusters (M10+ dedicated and serverless flex clusters)",
    mixinStandardHelpOptions = true,
    subcommands = {
        ClustersCommand.ListCommand.class,
        ClustersCommand.ListAllCommand.class,
        ClustersCommand.GetCommand.class,
        ClustersCommand.CreateCommand.class,
        ClustersCommand.CreateAsymmetricCommand.class,
        ClustersCommand.UpdateCommand.class,
        ClustersCommand.DeleteCommand.class,
        ClustersCommand.StatusCommand.class
    }
)
public class ClustersCommand implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(ClustersCommand.class);
    
    /**
     * Cluster type enumeration for filtering
     */
    public enum ClusterType {
        ALL("Show all cluster types"),
        DEDICATED("Show only dedicated M10+ clusters"), 
        FLEX("Show only serverless flex clusters");
        
        private final String description;
        
        ClusterType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli clusters --help' to see available cluster commands");
        return 0;
    }

    @Command(name = "list", description = "List all clusters in a project (dedicated M10+ and serverless flex clusters)", mixinStandardHelpOptions = true)
    static class ListCommand implements Callable<Integer> {
        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;
        
        @Option(names = {"-n", "--name"}, description = "Filter by name pattern (partial match, case-insensitive)")
        private String nameFilter;
        
        @Option(names = {"--regex"}, description = "Treat name pattern as regex (default: partial match)")
        private boolean useRegex;
        
        @Option(names = {"-t", "--tag", "--tags"}, description = "Filter by tag (format: 'key=value' or just 'key', can be used multiple times)")
        private List<String> tagFilters = new ArrayList<>();
        
        @Option(names = {"--type"}, description = "Filter by cluster type: ${COMPLETION-CANDIDATES} (default: all)", defaultValue = "ALL")
        private ClusterType clusterType;

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
            
            // 4. Auto-discover if only one project available
            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.size() == 1) {
                    return (String) projects.get(0).get("id");
                }
            } catch (Exception e) {
                // Ignore auto-discovery errors
            }
            
            // 5. No project ID available
            return null;
        }

        @Override
        public Integer call() throws Exception {
            // Get the root command to access credentials (same approach as LogsCommand)
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }
            
            String effectiveProjectId = resolveProjectId(rootCmd);
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set project in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                
                List<Map<String, Object>> allClusters = new ArrayList<>();
                
                // Fetch dedicated clusters if requested
                if (clusterType == ClusterType.ALL || clusterType == ClusterType.DEDICATED) {
                    AtlasClustersClient clustersClient = new AtlasClustersClient(apiBase);
                    List<Map<String, Object>> dedicatedClusters = clustersClient.getClusters(effectiveProjectId);
                    // Mark as dedicated clusters
                    for (Map<String, Object> cluster : dedicatedClusters) {
                        cluster.put("_clusterCategory", "DEDICATED");
                    }
                    allClusters.addAll(dedicatedClusters);
                }
                
                // Fetch flex/serverless clusters if requested
                if (clusterType == ClusterType.ALL || clusterType == ClusterType.FLEX) {
                    try {
                        AtlasFlexClustersClient flexClient = new AtlasFlexClustersClient(apiBase);
                        List<Map<String, Object>> flexClusters = flexClient.getFlexClusters(effectiveProjectId);
                        // Mark as flex clusters
                        for (Map<String, Object> cluster : flexClusters) {
                            cluster.put("_clusterCategory", "FLEX");
                        }
                        allClusters.addAll(flexClusters);
                    } catch (Exception e) {
                        // If flex clusters API fails (maybe no flex clusters or API issue), continue with dedicated only
                        logger.debug("Could not fetch flex clusters: {}", e.getMessage());
                    }
                }
                
                // Apply additional filtering
                List<Map<String, Object>> filteredClusters = allClusters;
                
                if (nameFilter != null && !nameFilter.trim().isEmpty()) {
                    // Filter by name using case-insensitive regex
                    String searchPattern = useRegex ? nameFilter : ".*" + nameFilter + ".*";
                    String caseInsensitivePattern = searchPattern.startsWith("(?i)") ? searchPattern : "(?i)" + searchPattern;
                    
                    filteredClusters = allClusters.stream()
                        .filter(cluster -> {
                            String name = (String) cluster.get("name");
                            return name != null && name.matches(caseInsensitivePattern);
                        })
                        .collect(java.util.stream.Collectors.toList());
                }
                
                if (!tagFilters.isEmpty()) {
                    // Filter by tags (only applies to dedicated clusters - flex clusters don't have tags)
                    Map<String, String> tagMap = new HashMap<>();
                    for (String tag : tagFilters) {
                        if (tag.contains("=")) {
                            String[] parts = tag.split("=", 2);
                            tagMap.put(parts[0], parts[1]);
                        } else {
                            tagMap.put(tag, null); // Match any value for this key
                        }
                    }
                    
                    filteredClusters = filteredClusters.stream()
                        .filter(cluster -> {
                            // Flex clusters don't have tags, so they won't match tag filters
                            if ("FLEX".equals(cluster.get("_clusterCategory"))) {
                                return false;
                            }
                            
                            Object tagsObj = cluster.get("tags");
                            if (!(tagsObj instanceof List)) {
                                return false;
                            }
                            
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> clusterTags = (List<Map<String, Object>>) tagsObj;
                            
                            for (Map<String, Object> clusterTag : clusterTags) {
                                String key = (String) clusterTag.get("key");
                                String value = (String) clusterTag.get("value");
                                
                                for (Map.Entry<String, String> searchTag : tagMap.entrySet()) {
                                    if (key != null && searchTag.getKey().equalsIgnoreCase(key)) {
                                        String searchValue = searchTag.getValue();
                                        if (searchValue == null || (value != null && value.equalsIgnoreCase(searchValue))) {
                                            return true;
                                        }
                                    }
                                }
                            }
                            return false;
                        })
                        .collect(java.util.stream.Collectors.toList());
                }

                if (filteredClusters.isEmpty()) {
                    if (nameFilter != null) {
                        String searchType = useRegex ? "regex pattern" : "partial name";
                        System.out.println("🔍 No clusters found matching " + searchType + " '" + nameFilter + "' in project " + effectiveProjectId);
                    } else if (!tagFilters.isEmpty()) {
                        List<String> tagDisplayList = new ArrayList<>();
                        for (String tag : tagFilters) {
                            tagDisplayList.add(tag.contains("=") ? tag : tag + "=*");
                        }
                        System.out.println("🔍 No clusters found with tags [" + String.join(", ", tagDisplayList) + 
                                         "] in project " + effectiveProjectId);
                    } else {
                        System.out.println("📭 No clusters found in project " + effectiveProjectId);
                    }
                    return 0;
                }

                // Display results with appropriate message
                String clusterTypeDesc = clusterType == ClusterType.ALL ? "cluster(s)" : 
                                        clusterType == ClusterType.DEDICATED ? "dedicated cluster(s)" : "flex cluster(s)";
                
                if (nameFilter != null) {
                    String searchType = useRegex ? "regex pattern" : "partial name";
                    System.out.println("🔍 Found " + filteredClusters.size() + " " + clusterTypeDesc + " matching " + searchType + " '" + nameFilter + "' in project " + effectiveProjectId);
                } else if (!tagFilters.isEmpty()) {
                    List<String> tagDisplayList = new ArrayList<>();
                    for (String tag : tagFilters) {
                        tagDisplayList.add(tag.contains("=") ? tag : tag + "=*");
                    }
                    System.out.println("🔍 Found " + filteredClusters.size() + " " + clusterTypeDesc + " with tags [" + 
                                     String.join(", ", tagDisplayList) + "] in project " + effectiveProjectId);
                } else {
                    System.out.println("🔍 Found " + filteredClusters.size() + " " + clusterTypeDesc + " in project " + effectiveProjectId);
                }
                
                OutputFormatter.printClusters(filteredClusters, GlobalConfig.getFormat());
                
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

    @Command(name = "list-all", description = "List all clusters across all accessible projects")
    static class ListAllCommand implements Callable<Integer> {
        
        @Override
        public Integer call() throws Exception {
            // Get the root command to access credentials
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                AtlasClustersClient clustersClient = new AtlasClustersClient(apiBase);
                
                // Get all projects
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.isEmpty()) {
                    System.out.println("📭 No projects found");
                    return 0;
                }

                // Filter projects if includeProjectNames is specified
                if (rootCmd.includeProjectNames != null && !rootCmd.includeProjectNames.isEmpty()) {
                    projects = projects.stream()
                        .filter(project -> rootCmd.includeProjectNames.contains((String) project.get("name")))
                        .toList();
                }

                int totalClusters = 0;
                System.out.println("🔍 Scanning " + projects.size() + " project(s) for clusters...\n");
                
                // Collect all clusters from all projects for unified table
                List<Map<String, Object>> allClusters = new ArrayList<>();
                
                for (Map<String, Object> project : projects) {
                    String projectId = (String) project.get("id");
                    String projectName = (String) project.get("name");
                    
                    try {
                        List<Map<String, Object>> clusters = clustersClient.getClusters(projectId);
                        
                        if (!clusters.isEmpty()) {
                            // Add project info to each cluster for display
                            for (Map<String, Object> cluster : clusters) {
                                cluster.put("projectName", projectName);
                                cluster.put("projectId", projectId);
                                allClusters.add(cluster);
                            }
                            totalClusters += clusters.size();
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️  Error accessing project " + projectName + ": " + e.getMessage());
                        if (GlobalConfig.isVerbose()) {
                            e.printStackTrace();
                        }
                    }
                }
                
                if (totalClusters == 0) {
                    System.out.println("📭 No clusters found in any accessible projects");
                } else {
                    System.out.println("Found " + totalClusters + " cluster(s) across " + projects.size() + " project(s):\n");
                    
                    // Print unified table with project information
                    printUnifiedClustersTable(allClusters, GlobalConfig.getFormat());
                    
                    System.out.println("\n✅ Total clusters found: " + totalClusters);
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing clusters: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
        
        private void printUnifiedClustersTable(List<Map<String, Object>> clusters, OutputFormat format) {
            if (format == OutputFormat.JSON) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(clusters);
                    System.out.println(json);
                } catch (Exception e) {
                    System.err.println("Error formatting JSON: " + e.getMessage());
                }
                return;
            }
            
            // Enhanced table format with project information
            System.out.printf("%-25s %-20s %-15s %-12s %-15s %-10s %-10s %-8s%n", 
                             "PROJECT", "NAME", "STATE", "TYPE", "VERSION", "SIZE", "PROVIDER", "SHARDS");
            System.out.println("─".repeat(125));
            
            for (Map<String, Object> cluster : clusters) {
                String projectName = getString(cluster, "projectName", "");
                String name = getString(cluster, "name", "");
                String state = getString(cluster, "stateName", "");
                String type = getString(cluster, "clusterType", "");
                String version = getString(cluster, "mongoDBVersion", "");
                
                // Debug: Print available keys for the first cluster to understand structure
                if (GlobalConfig.isVerbose() && cluster == clusters.get(0)) {
                    System.err.println("DEBUG: Available cluster fields: " + cluster.keySet());
                    if (cluster.containsKey("providerSettings")) {
                        System.err.println("DEBUG: providerSettings fields: " + 
                            ((Map<String, Object>) cluster.get("providerSettings")).keySet());
                    }
                }
                
                // Try multiple possible field names for size
                String size = getString(cluster, "instanceSizeName", "");
                if (size.isEmpty()) {
                    size = getString(cluster, "providerSettings.instanceSizeName", "");
                }
                if (size.isEmpty()) {
                    // Check for replicationSpecs array (common in cluster API responses)
                    Object replicationSpecs = cluster.get("replicationSpecs");
                    if (replicationSpecs instanceof List && !((List<?>) replicationSpecs).isEmpty()) {
                        Map<String, Object> firstSpec = (Map<String, Object>) ((List<?>) replicationSpecs).get(0);
                        if (firstSpec.containsKey("regionConfigs")) {
                            List<Map<String, Object>> regionConfigs = (List<Map<String, Object>>) firstSpec.get("regionConfigs");
                            if (!regionConfigs.isEmpty()) {
                                Map<String, Object> firstRegion = regionConfigs.get(0);
                                size = getString(firstRegion, "electableSpecs.instanceSize", "");
                            }
                        }
                    }
                }
                
                // Try multiple possible field names for provider  
                String provider = getString(cluster, "providerSettings.providerName", "");
                if (provider.isEmpty()) {
                    provider = getString(cluster, "providerName", "");
                }
                if (provider.isEmpty()) {
                    // Check for replicationSpecs array
                    Object replicationSpecs = cluster.get("replicationSpecs");
                    if (replicationSpecs instanceof List && !((List<?>) replicationSpecs).isEmpty()) {
                        Map<String, Object> firstSpec = (Map<String, Object>) ((List<?>) replicationSpecs).get(0);
                        if (firstSpec.containsKey("regionConfigs")) {
                            List<Map<String, Object>> regionConfigs = (List<Map<String, Object>>) firstSpec.get("regionConfigs");
                            if (!regionConfigs.isEmpty()) {
                                Map<String, Object> firstRegion = regionConfigs.get(0);
                                provider = getString(firstRegion, "providerName", "");
                            }
                        }
                    }
                }
                
                // Count shards for sharded clusters
                String shards = "";
                if ("SHARDED".equals(type)) {
                    Object replicationSpecs = cluster.get("replicationSpecs");
                    if (replicationSpecs instanceof List) {
                        int shardCount = ((List<?>) replicationSpecs).size();
                        shards = String.valueOf(shardCount);
                    }
                }
                
                System.out.printf("%-25s %-20s %-15s %-12s %-15s %-10s %-10s %-8s%n", 
                                 truncate(projectName, 25), truncate(name, 20), truncate(state, 15), 
                                 truncate(type, 12), truncate(version, 15), truncate(size, 10), 
                                 truncate(provider, 10), truncate(shards, 8));
            }
        }
        
        private String getString(Map<String, Object> map, String key, String defaultValue) {
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                Object nested = map.get(parts[0]);
                if (nested instanceof Map) {
                    return getString((Map<String, Object>) nested, parts[1], defaultValue);
                }
                return defaultValue;
            }
            
            Object value = map.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        
        private String truncate(String str, int maxLength) {
            if (str == null) return "";
            if (str.length() <= maxLength) return str;
            return str.substring(0, maxLength - 3) + "...";
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
        
        @Option(names = {"--asymmetric"}, description = "Enable asymmetric sharding (API v20240805) - allows different sizes per shard")
        private boolean asymmetric;
        
        @Option(names = {"--shard-sizes"}, description = "Comma-separated list of instance sizes for each shard (for asymmetric sharding)", split = ",")
        private List<String> shardSizes;

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
            
            // 4. Auto-discover if only one project available
            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.size() == 1) {
                    return (String) projects.get(0).get("id");
                }
            } catch (Exception e) {
                // Ignore auto-discovery errors
            }
            
            // 5. No project ID available
            return null;
        }

        @Override
        public Integer call() throws Exception {
            // Get the root command to access credentials (same approach as ListCommand)
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }
            
            String effectiveProjectId = resolveProjectId(rootCmd);
            
            if (effectiveProjectId == null) {
                System.err.println("❌ Error: Project ID is required. Use --project or set project in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasClustersClient client = new AtlasClustersClient(apiBase);
                
                String clusterType = sharded ? (asymmetric ? "asymmetric sharded cluster" : "sharded cluster") : "replica set cluster";
                System.out.println("🚀 Creating " + clusterType + " '" + clusterName + "'...");
                System.out.println("   Type: " + (sharded ? (asymmetric ? "Asymmetric Sharded" : "Sharded") : "Replica Set"));
                
                if (sharded) {
                    if (asymmetric) {
                        // Asymmetric sharding validation
                        if (shardSizes == null || shardSizes.isEmpty()) {
                            System.err.println("❌ Error: --shard-sizes is required for asymmetric sharding");
                            return 1;
                        }
                        
                        System.out.println("   Shards: " + shardSizes.size());
                        System.out.println("   Configuration:");
                        for (int i = 0; i < shardSizes.size(); i++) {
                            System.out.println("     Shard " + (i + 1) + ": " + shardSizes.get(i));
                        }
                        
                        // Validate each shard size
                        String[] validSizes = com.mongodb.atlas.api.clients.AtlasClustersClient.getShardedClusterInstanceSizes();
                        for (String shardSize : shardSizes) {
                            boolean validSize = false;
                            for (String validSizeOption : validSizes) {
                                if (validSizeOption.equalsIgnoreCase(shardSize)) {
                                    validSize = true;
                                    break;
                                }
                            }
                            if (!validSize) {
                                System.err.println("❌ Error: Sharded clusters require instance size M30 or larger. Invalid size: " + shardSize);
                                return 1;
                            }
                        }
                        
                        System.out.println("   ⚠️  Using API v20240805 - This change is irreversible!");
                    } else {
                        // Symmetric sharding
                        System.out.println("   Size: " + instanceSize);
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
                } else {
                    System.out.println("   Size: " + instanceSize);
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

    @Command(name = "delete", description = "Delete cluster(s) - supports interactive mode and bulk deletion", mixinStandardHelpOptions = true)
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", arity = "0..1", description = "Cluster name (optional - will prompt if not provided)")
        private String clusterName;

        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Option(names = {"-f", "--force"}, description = "Skip confirmation prompt")
        private boolean force;

        @Option(names = {"--all"}, description = "Delete ALL clusters in the project (requires confirmation)")
        private boolean deleteAll;

        @Option(names = {"-y", "--yes"}, description = "Automatically answer yes to all prompts (dangerous!)")
        private boolean autoYes;

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
                AtlasClustersClient dedicatedClient = new AtlasClustersClient(apiBase);
                AtlasFlexClustersClient flexClient = new AtlasFlexClustersClient(apiBase);

                if (deleteAll) {
                    return deleteAllClusters(effectiveProjectId, dedicatedClient, flexClient);
                } else if (clusterName == null || clusterName.trim().isEmpty()) {
                    return interactiveDelete(effectiveProjectId, dedicatedClient, flexClient);
                } else {
                    return deleteSingleCluster(effectiveProjectId, clusterName.trim(), dedicatedClient, flexClient);
                }
            } catch (Exception e) {
                System.err.println("❌ Error in cluster deletion: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private Integer deleteAllClusters(String projectId, AtlasClustersClient dedicatedClient, AtlasFlexClustersClient flexClient) throws Exception {
            // Get all clusters (dedicated + flex)
            List<Map<String, Object>> dedicatedClusters = dedicatedClient.getClusters(projectId);
            List<Map<String, Object>> flexClusters = flexClient.getFlexClusters(projectId);
            
            int totalClusters = dedicatedClusters.size() + flexClusters.size();
            
            if (totalClusters == 0) {
                System.out.println("📭 No clusters found in project " + projectId);
                return 0;
            }

            System.out.println("⚠️  WARNING: You are about to delete ALL " + totalClusters + " cluster(s) in project " + projectId + ":");
            System.out.println();
            
            // Show dedicated clusters
            if (!dedicatedClusters.isEmpty()) {
                System.out.println("🏗️  Dedicated Clusters (" + dedicatedClusters.size() + "):");
                for (Map<String, Object> cluster : dedicatedClusters) {
                    System.out.println("   • " + cluster.get("name") + " (" + cluster.get("stateName") + ")");
                }
            }
            
            // Show flex clusters  
            if (!flexClusters.isEmpty()) {
                System.out.println("⚡ Flex Clusters (" + flexClusters.size() + "):");
                for (Map<String, Object> cluster : flexClusters) {
                    System.out.println("   • " + cluster.get("name") + " (" + cluster.get("stateName") + ")");
                }
            }
            
            System.out.println();
            
            if (!autoYes) {
                System.out.println("🚨 THIS WILL PERMANENTLY DELETE ALL CLUSTERS AND THEIR DATA!");
                System.out.print("Type 'DELETE ALL CLUSTERS' to confirm (anything else cancels): ");
                
                String confirmation = System.console() != null ? 
                    System.console().readLine() : 
                    new java.util.Scanner(System.in).nextLine();
                    
                if (!"DELETE ALL CLUSTERS".equals(confirmation)) {
                    System.out.println("❌ Operation cancelled - confirmation text did not match");
                    return 0;
                }
            }

            System.out.println();
            System.out.println("🗑️  Starting deletion of all " + totalClusters + " cluster(s)...");
            
            int successCount = 0;
            int errorCount = 0;

            // Delete dedicated clusters
            for (Map<String, Object> cluster : dedicatedClusters) {
                String name = (String) cluster.get("name");
                try {
                    System.out.println("🗑️  Deleting dedicated cluster: " + name);
                    dedicatedClient.deleteCluster(projectId, name);
                    successCount++;
                    System.out.println("✅ " + name + " deletion initiated");
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Failed to delete " + name + ": " + e.getMessage());
                }
            }

            // Delete flex clusters
            for (Map<String, Object> cluster : flexClusters) {
                String name = (String) cluster.get("name");
                try {
                    System.out.println("🗑️  Deleting flex cluster: " + name);
                    flexClient.deleteFlexCluster(projectId, name);
                    successCount++;
                    System.out.println("✅ " + name + " deletion initiated");
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Failed to delete " + name + ": " + e.getMessage());
                }
            }

            System.out.println();
            System.out.println("📊 Deletion Summary:");
            System.out.println("   ✅ Successful: " + successCount);
            System.out.println("   ❌ Failed: " + errorCount);
            System.out.println("   ⏳ Successful deletions will complete in a few minutes");

            return errorCount > 0 ? 1 : 0;
        }

        private Integer interactiveDelete(String projectId, AtlasClustersClient dedicatedClient, AtlasFlexClustersClient flexClient) throws Exception {
            // Get all clusters for selection
            List<Map<String, Object>> dedicatedClusters = dedicatedClient.getClusters(projectId);
            List<Map<String, Object>> flexClusters = flexClient.getFlexClusters(projectId);
            
            int totalClusters = dedicatedClusters.size() + flexClusters.size();
            
            if (totalClusters == 0) {
                System.out.println("📭 No clusters found in project " + projectId);
                return 0;
            }

            System.out.println("🔍 Found " + totalClusters + " cluster(s) in project " + projectId + ":");
            System.out.println();
            
            // Create a combined list with indices
            java.util.List<Map<String, Object>> allClusters = new java.util.ArrayList<>();
            java.util.List<String> clusterTypes = new java.util.ArrayList<>();
            
            int index = 1;
            
            // Add dedicated clusters
            for (Map<String, Object> cluster : dedicatedClusters) {
                allClusters.add(cluster);
                clusterTypes.add("dedicated");
                System.out.println("  " + index + ". " + cluster.get("name") + " (Dedicated - " + cluster.get("stateName") + ")");
                index++;
            }
            
            // Add flex clusters
            for (Map<String, Object> cluster : flexClusters) {
                allClusters.add(cluster);
                clusterTypes.add("flex");
                System.out.println("  " + index + ". " + cluster.get("name") + " (Flex - " + cluster.get("stateName") + ")");
                index++;
            }
            
            System.out.println();
            System.out.print("Enter cluster number to delete (1-" + totalClusters + ") or cluster name: ");
            
            String input = System.console() != null ? 
                System.console().readLine() : 
                new java.util.Scanner(System.in).nextLine();
                
            if (input == null || input.trim().isEmpty()) {
                System.out.println("❌ No selection made");
                return 0;
            }
            
            input = input.trim();
            Map<String, Object> selectedCluster = null;
            String selectedType = null;
            
            // Try to parse as number first
            try {
                int selection = Integer.parseInt(input);
                if (selection >= 1 && selection <= totalClusters) {
                    selectedCluster = allClusters.get(selection - 1);
                    selectedType = clusterTypes.get(selection - 1);
                }
            } catch (NumberFormatException e) {
                // Not a number, try to find by name
                String inputLower = input.toLowerCase();
                for (int i = 0; i < allClusters.size(); i++) {
                    String clusterName = ((String) allClusters.get(i).get("name")).toLowerCase();
                    if (clusterName.equals(inputLower) || clusterName.contains(inputLower)) {
                        selectedCluster = allClusters.get(i);
                        selectedType = clusterTypes.get(i);
                        break;
                    }
                }
            }
            
            if (selectedCluster == null) {
                System.err.println("❌ Invalid selection: " + input);
                return 1;
            }
            
            String clusterName = (String) selectedCluster.get("name");
            return deleteSingleCluster(projectId, clusterName, dedicatedClient, flexClient);
        }

        private Integer deleteSingleCluster(String projectId, String clusterName, AtlasClustersClient dedicatedClient, AtlasFlexClustersClient flexClient) throws Exception {
            // First, determine if it's a dedicated or flex cluster
            boolean isFlexCluster = false;
            Map<String, Object> clusterInfo = null;
            
            // Check dedicated clusters first
            try {
                clusterInfo = dedicatedClient.getCluster(projectId, clusterName);
            } catch (Exception e) {
                // Not a dedicated cluster, try flex
                try {
                    clusterInfo = flexClient.getFlexCluster(projectId, clusterName);
                    isFlexCluster = true;
                } catch (Exception e2) {
                    System.err.println("❌ Cluster '" + clusterName + "' not found in project " + projectId);
                    return 1;
                }
            }

            String clusterType = isFlexCluster ? "Flex" : "Dedicated";
            String state = (String) clusterInfo.get("stateName");
            
            System.out.println("🗑️  About to delete " + clusterType.toLowerCase() + " cluster:");
            System.out.println("   Name: " + clusterName);
            System.out.println("   Type: " + clusterType);
            System.out.println("   State: " + state);
            System.out.println();

            if (!force && !autoYes) {
                System.out.println("⚠️  WARNING: This will permanently delete the cluster and ALL its data!");
                System.out.print("Type the cluster name '" + clusterName + "' to confirm deletion: ");
                
                String confirmation = System.console() != null ? 
                    System.console().readLine() : 
                    new java.util.Scanner(System.in).nextLine();
                    
                if (!clusterName.equals(confirmation)) {
                    System.out.println("❌ Operation cancelled - cluster name did not match");
                    return 0;
                }
            }

            System.out.println("🗑️  Deleting " + clusterType.toLowerCase() + " cluster '" + clusterName + "'...");
            
            if (isFlexCluster) {
                flexClient.deleteFlexCluster(projectId, clusterName);
            } else {
                dedicatedClient.deleteCluster(projectId, clusterName);
            }
            
            System.out.println("✅ " + clusterType + " cluster deletion initiated successfully!");
            System.out.println("⏳ The cluster will be completely removed in a few minutes");
            
            return 0;
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

    @Command(name = "create-asymmetric", description = "Create an asymmetric sharded cluster with different instance sizes per shard")
    static class CreateAsymmetricCommand implements Callable<Integer> {
        @Option(names = {"-p", "--project"}, description = "Project ID (overrides config)")
        private String projectId;

        @Parameters(index = "0", description = "Cluster name")
        private String clusterName;

        @Option(names = {"-v", "--version"}, description = "MongoDB version (default: 7.0)", defaultValue = "7.0")
        private String mongoVersion;

        @Option(names = {"-s", "--shard-sizes"}, description = "Comma-separated list of instance sizes for each shard (e.g., M30,M40,M50)", required = true)
        private String shardSizes;

        @Option(names = {"-r", "--region"}, description = "Cloud region (default: US_EAST_1)", defaultValue = "US_EAST_1")
        private String region;

        @Option(names = {"-c", "--cloud-provider"}, description = "Cloud provider (default: AWS)", defaultValue = "AWS")
        private String cloudProvider;

        @Option(names = {"--wait"}, description = "Wait for cluster to be ready")
        private boolean waitForReady;

        @Option(names = {"--timeout"}, description = "Timeout in seconds when waiting (default: 1800)", defaultValue = "1800")
        private int timeoutSeconds;

        private String resolveProjectId(AtlasCliMain rootCmd) {
            if (projectId != null && !projectId.trim().isEmpty()) {
                return projectId.trim();
            }
            
            if (rootCmd.projectIds != null && !rootCmd.projectIds.isEmpty()) {
                return rootCmd.projectIds.get(0);
            }
            
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
            
            try {
                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasProjectsClient projectsClient = new AtlasProjectsClient(apiBase);
                List<Map<String, Object>> projects = projectsClient.getAllProjects();
                
                if (projects.size() == 1) {
                    return (String) projects.get(0).get("id");
                }
            } catch (Exception e) {
                // Ignore auto-discovery errors
            }
            
            return null;
        }

        @Override
        public Integer call() throws Exception {
            AtlasCliMain rootCmd = GlobalConfig.getRootCommand();
            if (rootCmd == null || rootCmd.apiPublicKey == null || rootCmd.apiPrivateKey == null) {
                System.err.println("❌ Error: Atlas API credentials are required.");
                System.err.println("   Set apiPublicKey and apiPrivateKey in config file");
                return 1;
            }

            String resolvedProjectId = resolveProjectId(rootCmd);
            if (resolvedProjectId == null) {
                System.err.println("❌ Error: Project ID is required.");
                System.err.println("   Use -p/--project option or configure projects in config file");
                return 1;
            }

            try {
                // Parse shard sizes
                String[] instanceSizes = shardSizes.split(",");
                for (int i = 0; i < instanceSizes.length; i++) {
                    instanceSizes[i] = instanceSizes[i].trim();
                }

                // Validate instance sizes
                String[] validSizes = AtlasClustersClient.getShardedClusterInstanceSizes();
                for (String size : instanceSizes) {
                    boolean isValid = false;
                    for (String validSize : validSizes) {
                        if (validSize.equalsIgnoreCase(size)) {
                            isValid = true;
                            break;
                        }
                    }
                    if (!isValid) {
                        System.err.println("❌ Error: Invalid instance size '" + size + "'");
                        System.err.println("   Valid sizes for sharded clusters: " + String.join(", ", validSizes));
                        return 1;
                    }
                }

                System.out.println("🚀 Creating asymmetric sharded cluster '" + clusterName + "'...");
                System.out.println("   Project: " + resolvedProjectId);
                System.out.println("   MongoDB Version: " + mongoVersion);
                System.out.println("   Shard Sizes: " + String.join(", ", instanceSizes));
                System.out.println("   Region: " + region);
                System.out.println("   Cloud Provider: " + cloudProvider);

                AtlasApiBase apiBase = new AtlasApiBase(rootCmd.apiPublicKey, rootCmd.apiPrivateKey);
                AtlasClustersClient clustersClient = new AtlasClustersClient(apiBase);

                Map<String, Object> result = clustersClient.createAsymmetricShardedClusterSimple(
                    resolvedProjectId, clusterName, mongoVersion, instanceSizes, region, cloudProvider);

                System.out.println("✅ Asymmetric sharded cluster creation initiated successfully");
                OutputFormatter.printClusterDetails(result, OutputFormat.JSON);

                if (waitForReady) {
                    System.out.println("⏳ Waiting for cluster to be ready (timeout: " + timeoutSeconds + "s)...");
                    boolean ready = clustersClient.waitForClusterState(resolvedProjectId, clusterName, "IDLE", timeoutSeconds);
                    
                    if (ready) {
                        System.out.println("✅ Cluster '" + clusterName + "' is ready!");
                        
                        // Show final cluster info
                        Map<String, Object> cluster = clustersClient.getClusterSummary(resolvedProjectId, clusterName);
                        OutputFormatter.printClusterDetails(cluster, OutputFormat.JSON);
                    } else {
                        System.out.println("⚠️  Timeout waiting for cluster to be ready. Check status manually.");
                        return 1;
                    }
                }

                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error creating asymmetric sharded cluster '" + clusterName + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }
}
