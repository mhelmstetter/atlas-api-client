package com.mongodb.atlas.api.cli.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.atlas.api.cli.AtlasCliMain.OutputFormat;

import java.util.List;
import java.util.Map;

/**
 * Utility class for formatting CLI output in various formats
 */
public class OutputFormatter {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void printClusters(List<Map<String, Object>> clusters, OutputFormat format) {
        switch (format) {
            case JSON:
                printJson(clusters);
                break;
            case CSV:
                printClustersCSV(clusters);
                break;
            case TABLE:
            default:
                printClustersTable(clusters);
                break;
        }
    }
    
    public static void printClusterDetails(Map<String, Object> cluster, OutputFormat format) {
        switch (format) {
            case JSON:
                printJson(cluster);
                break;
            case TABLE:
            default:
                printClusterDetailsTable(cluster);
                break;
        }
    }
    
    public static void printApiKeys(List<Map<String, Object>> apiKeys, OutputFormat format) {
        switch (format) {
            case JSON:
                printJson(apiKeys);
                break;
            case CSV:
                printApiKeysCSV(apiKeys);
                break;
            case TABLE:
            default:
                printApiKeysTable(apiKeys);
                break;
        }
    }
    
    public static void printApiKeyDetails(Map<String, Object> apiKey, OutputFormat format) {
        switch (format) {
            case JSON:
                printJson(apiKey);
                break;
            case TABLE:
            default:
                printApiKeyDetailsTable(apiKey);
                break;
        }
    }
    
    public static void printAccessLogs(List<Map<String, Object>> accessLogs, OutputFormat format) {
        switch (format) {
            case JSON:
                printJson(accessLogs);
                break;
            case CSV:
                printAccessLogsCSV(accessLogs);
                break;
            case TABLE:
            default:
                printAccessLogsTable(accessLogs);
                break;
        }
    }
    
    private static void printClustersTable(List<Map<String, Object>> clusters) {
        if (clusters.isEmpty()) {
            return;
        }
        
        // Sort clusters by name
        clusters.sort((c1, c2) -> {
            String name1 = getString(c1, "name", "");
            String name2 = getString(c2, "name", "");
            return name1.compareToIgnoreCase(name2);
        });
        
        // Calculate maximum tag length for dynamic column sizing
        int maxTagsLength = "TAGS".length(); // Minimum header width
        for (Map<String, Object> cluster : clusters) {
            String tags = formatTags(cluster);
            maxTagsLength = Math.max(maxTagsLength, tags.length());
        }
        
        // Set minimum and reasonable maximum for tags column
        maxTagsLength = Math.max(maxTagsLength, 20); // Minimum 20 chars
        maxTagsLength = Math.min(maxTagsLength, 80); // Maximum 80 chars for readability
        
        System.out.println();
        String headerFormat = "%-20s %-15s %-12s %-15s %-10s %-15s %-" + maxTagsLength + "s%n";
        String rowFormat = "%-20s %-15s %-12s %-15s %-10s %-15s %-" + maxTagsLength + "s%n";
        
        System.out.printf(headerFormat, "NAME", "STATE", "TYPE", "VERSION", "SIZE", "PROVIDER", "TAGS");
        System.out.println("─".repeat(20 + 15 + 12 + 15 + 10 + 15 + maxTagsLength + 6)); // +6 for spacing
        
        for (Map<String, Object> cluster : clusters) {
            String name = getString(cluster, "name", "");
            String state = getString(cluster, "stateName", "");
            String type = extractClusterType(cluster);
            String version = getString(cluster, "mongoDBVersion", "");
            
            // Extract size and provider from replicationSpecs structure
            String size = extractInstanceSize(cluster);
            String provider = extractProviderName(cluster);
            
            // Format tags (no truncation now)
            String tags = formatTags(cluster);
            
            System.out.printf(rowFormat, 
                             truncate(name, 20), truncate(state, 15), truncate(type, 12),
                             truncate(version, 15), truncate(size, 10), truncate(provider, 15),
                             truncate(tags, maxTagsLength));
        }
        System.out.println();
    }
    
    private static void printClustersCSV(List<Map<String, Object>> clusters) {
        // Sort clusters by name
        clusters.sort((c1, c2) -> {
            String name1 = getString(c1, "name", "");
            String name2 = getString(c2, "name", "");
            return name1.compareToIgnoreCase(name2);
        });
        
        System.out.println("Name,State,Type,Version,Size,Provider,Tags");
        for (Map<String, Object> cluster : clusters) {
            System.out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                escapeCsv(getString(cluster, "name", "")),
                escapeCsv(getString(cluster, "stateName", "")),
                escapeCsv(extractClusterType(cluster)),
                escapeCsv(getString(cluster, "mongoDBVersion", "")),
                escapeCsv(extractInstanceSize(cluster)),
                escapeCsv(extractProviderName(cluster)),
                escapeCsv(formatTags(cluster))
            );
        }
    }
    
    private static void printClusterDetailsTable(Map<String, Object> cluster) {
        System.out.println();
        System.out.println("CLUSTER DETAILS");
        System.out.println("═".repeat(50));
        
        printField("Name", cluster.get("name"));
        printField("ID", cluster.get("id"));
        printField("State", cluster.get("stateName"));
        printField("Type", cluster.get("clusterType"));
        printField("MongoDB Version", cluster.get("mongoDBVersion"));
        printField("Instance Size", cluster.get("instanceSizeName"));
        printField("Disk Size (GB)", cluster.get("diskSizeGB"));
        printField("Backup Enabled", cluster.get("backupEnabled"));
        printField("Created Date", cluster.get("createDate"));
        
        if (cluster.containsKey("providerSettings")) {
            Map<String, Object> provider = (Map<String, Object>) cluster.get("providerSettings");
            printField("Cloud Provider", provider.get("providerName"));
            printField("Region", provider.get("regionName"));
            printField("Instance Size Name", provider.get("instanceSizeName"));
        }
        
        if (cluster.containsKey("connectionStrings")) {
            Map<String, Object> connections = (Map<String, Object>) cluster.get("connectionStrings");
            printField("Standard Connection", connections.get("standard"));
            printField("Standard SRV", connections.get("standardSrv"));
        }
        
        System.out.println();
    }
    
    private static void printApiKeysTable(List<Map<String, Object>> apiKeys) {
        System.out.println();
        System.out.printf("%-25s %-40s %-30s %-15s%n", 
                         "ID", "DESCRIPTION", "ROLES", "PUBLIC KEY");
        System.out.println("─".repeat(115));
        
        for (Map<String, Object> apiKey : apiKeys) {
            String id = getString(apiKey, "id", "");
            String desc = getString(apiKey, "desc", "");
            String roles = formatRoles((List<String>) apiKey.get("roles"));
            String publicKey = getString(apiKey, "publicKey", "");
            
            System.out.printf("%-25s %-40s %-30s %-15s%n", 
                             truncate(id, 25), truncate(desc, 40), 
                             truncate(roles, 30), truncate(publicKey, 15));
        }
        System.out.println();
    }
    
    private static void printApiKeyDetailsTable(Map<String, Object> apiKey) {
        System.out.println();
        System.out.println("API KEY DETAILS");
        System.out.println("═".repeat(50));
        
        printField("ID", apiKey.get("id"));
        printField("Description", apiKey.get("desc"));
        printField("Public Key", apiKey.get("publicKey"));
        if (apiKey.containsKey("privateKey")) {
            printField("Private Key", "***SENSITIVE - SAVE IMMEDIATELY***");
        }
        printField("Roles", formatRoles((List<String>) apiKey.get("roles")));
        
        System.out.println();
        
        if (apiKey.containsKey("privateKey")) {
            System.out.println("🔑 PRIVATE KEY (save this immediately - it cannot be retrieved again):");
            System.out.println("   " + apiKey.get("privateKey"));
            System.out.println();
        }
    }
    
    private static void printApiKeysCSV(List<Map<String, Object>> apiKeys) {
        System.out.println("ID,Description,PublicKey,Roles");
        for (Map<String, Object> apiKey : apiKeys) {
            System.out.printf("%s,%s,%s,%s%n",
                escapeCsv(getString(apiKey, "id", "")),
                escapeCsv(getString(apiKey, "desc", "")),
                escapeCsv(getString(apiKey, "publicKey", "")),
                escapeCsv(formatRoles((List<String>) apiKey.get("roles")))
            );
        }
    }
    
    private static void printJson(Object object) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
            System.out.println(json);
        } catch (Exception e) {
            System.err.println("Error formatting JSON: " + e.getMessage());
        }
    }
    
    private static void printField(String name, Object value) {
        if (value != null) {
            System.out.printf("%-20s: %s%n", name, value);
        }
    }
    
    private static String getString(Map<String, Object> map, String key, String defaultValue) {
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
    
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    private static String formatRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "";
        return String.join(", ", roles);
    }
    
    private static void printAccessLogsTable(List<Map<String, Object>> accessLogs) {
        System.out.println();
        System.out.printf("%-25s %-20s %-15s %-30s %-15s %-10s%n", 
                         "TIMESTAMP", "USERNAME", "IP ADDRESS", "HOSTNAME", "AUTH RESULT", "FAILURE");
        System.out.println("─".repeat(125));
        
        for (Map<String, Object> log : accessLogs) {
            String timestamp = getString(log, "timestamp", "");
            String username = getString(log, "username", "");
            String ipAddress = getString(log, "ipAddress", "");
            String hostname = getString(log, "hostname", "");
            String authResult = getString(log, "authResult", "");
            String failureReason = getString(log, "failureReason", "");
            
            System.out.printf("%-25s %-20s %-15s %-30s %-15s %-10s%n", 
                             truncate(timestamp, 25), truncate(username, 20), truncate(ipAddress, 15),
                             truncate(hostname, 30), truncate(authResult, 15), truncate(failureReason, 10));
        }
        System.out.println();
    }
    
    private static void printAccessLogsCSV(List<Map<String, Object>> accessLogs) {
        System.out.println("Timestamp,Username,IPAddress,Hostname,AuthResult,FailureReason");
        for (Map<String, Object> log : accessLogs) {
            System.out.printf("%s,%s,%s,%s,%s,%s%n",
                escapeCsv(getString(log, "timestamp", "")),
                escapeCsv(getString(log, "username", "")),
                escapeCsv(getString(log, "ipAddress", "")),
                escapeCsv(getString(log, "hostname", "")),
                escapeCsv(getString(log, "authResult", "")),
                escapeCsv(getString(log, "failureReason", ""))
            );
        }
    }
    
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Extract cluster type, properly identifying Flex clusters
     */
    private static String extractClusterType(Map<String, Object> cluster) {
        // Check if it's a Flex cluster by looking at providerSettings.providerName
        if (cluster.containsKey("providerSettings")) {
            Map<String, Object> providerSettings = (Map<String, Object>) cluster.get("providerSettings");
            Object providerName = providerSettings.get("providerName");
            if ("FLEX".equals(providerName)) {
                return "FLEX";
            }
        }
        
        // Default to the clusterType field
        return getString(cluster, "clusterType", "");
    }
    
    /**
     * Extract instance size from cluster's replicationSpecs structure
     */
    private static String extractInstanceSize(Map<String, Object> cluster) {
        // For Flex clusters, there is no instance size - they are serverless
        if (cluster.containsKey("providerSettings")) {
            Map<String, Object> providerSettings = (Map<String, Object>) cluster.get("providerSettings");
            Object providerName = providerSettings.get("providerName");
            if ("FLEX".equals(providerName)) {
                return "Flex"; // Flex clusters don't have traditional instance sizes
            }
        }
        
        // Try dedicated cluster format (replicationSpecs)
        try {
            Object replicationSpecs = cluster.get("replicationSpecs");
            if (replicationSpecs instanceof List && !((List<?>) replicationSpecs).isEmpty()) {
                Map<String, Object> firstSpec = (Map<String, Object>) ((List<?>) replicationSpecs).get(0);
                Object regionConfigs = firstSpec.get("regionConfigs");
                if (regionConfigs instanceof List && !((List<?>) regionConfigs).isEmpty()) {
                    Map<String, Object> firstRegion = (Map<String, Object>) ((List<?>) regionConfigs).get(0);
                    Object electableSpecs = firstRegion.get("electableSpecs");
                    if (electableSpecs instanceof Map) {
                        Object instanceSize = ((Map<String, Object>) electableSpecs).get("instanceSize");
                        return instanceSize != null ? instanceSize.toString() : "";
                    }
                }
            }
        } catch (Exception e) {
            // Fallback if structure is different
        }
        
        // Fallback to legacy format
        return getString(cluster, "instanceSizeName", "");
    }
    
    /**
     * Extract provider name from cluster's replicationSpecs structure
     */
    private static String extractProviderName(Map<String, Object> cluster) {
        // Try Flex cluster format first (providerSettings.providerName or providerSettings.backingProviderName)
        if (cluster.containsKey("providerSettings")) {
            Map<String, Object> providerSettings = (Map<String, Object>) cluster.get("providerSettings");
            Object providerName = providerSettings.get("providerName");
            if (providerName != null) {
                return providerName.toString();
            }
            Object backingProviderName = providerSettings.get("backingProviderName");
            if (backingProviderName != null) {
                return backingProviderName.toString();
            }
        }
        
        // Try dedicated cluster format (replicationSpecs)
        try {
            Object replicationSpecs = cluster.get("replicationSpecs");
            if (replicationSpecs instanceof List && !((List<?>) replicationSpecs).isEmpty()) {
                Map<String, Object> firstSpec = (Map<String, Object>) ((List<?>) replicationSpecs).get(0);
                Object regionConfigs = firstSpec.get("regionConfigs");
                if (regionConfigs instanceof List && !((List<?>) regionConfigs).isEmpty()) {
                    Map<String, Object> firstRegion = (Map<String, Object>) ((List<?>) regionConfigs).get(0);
                    Object providerName = firstRegion.get("providerName");
                    return providerName != null ? providerName.toString() : "";
                }
            }
        } catch (Exception e) {
            // Fallback if structure is different
        }
        
        return "";
    }
    
    /**
     * Format tags as a readable string
     */
    private static String formatTags(Map<String, Object> cluster) {
        Object tagsObj = cluster.get("tags");
        if (!(tagsObj instanceof List)) {
            return "";
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) tagsObj;
        
        if (tags.isEmpty()) {
            return "";
        }
        
        // Format as key=value pairs, comma-separated
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) result.append(", ");
            Map<String, Object> tag = tags.get(i);
            String key = (String) tag.get("key");
            String value = (String) tag.get("value");
            result.append(key);
            if (value != null && !value.isEmpty()) {
                result.append("=").append(value);
            }
        }
        
        return result.toString();
    }
}