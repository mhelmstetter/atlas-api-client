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
    
    private static void printClustersTable(List<Map<String, Object>> clusters) {
        System.out.println();
        System.out.printf("%-20s %-15s %-12s %-15s %-10s %-15s%n", 
                         "NAME", "STATE", "TYPE", "VERSION", "SIZE", "PROVIDER");
        System.out.println("─".repeat(95));
        
        for (Map<String, Object> cluster : clusters) {
            String name = getString(cluster, "name", "");
            String state = getString(cluster, "stateName", "");
            String type = getString(cluster, "clusterType", "");
            String version = getString(cluster, "mongoDBVersion", "");
            String size = getString(cluster, "instanceSizeName", "");
            String provider = getString(cluster, "providerSettings.providerName", "");
            
            System.out.printf("%-20s %-15s %-12s %-15s %-10s %-15s%n", 
                             truncate(name, 20), truncate(state, 15), truncate(type, 12),
                             truncate(version, 15), truncate(size, 10), truncate(provider, 15));
        }
        System.out.println();
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
    
    private static void printClustersCSV(List<Map<String, Object>> clusters) {
        System.out.println("Name,State,Type,Version,Size,Provider,Region,Created");
        for (Map<String, Object> cluster : clusters) {
            System.out.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                escapeCsv(getString(cluster, "name", "")),
                escapeCsv(getString(cluster, "stateName", "")),
                escapeCsv(getString(cluster, "clusterType", "")),
                escapeCsv(getString(cluster, "mongoDBVersion", "")),
                escapeCsv(getString(cluster, "instanceSizeName", "")),
                escapeCsv(getString(cluster, "providerSettings.providerName", "")),
                escapeCsv(getString(cluster, "providerSettings.regionName", "")),
                escapeCsv(getString(cluster, "createDate", ""))
            );
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
    
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}