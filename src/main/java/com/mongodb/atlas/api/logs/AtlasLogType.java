package com.mongodb.atlas.api.logs;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum representing the 4 valid MongoDB Atlas log types
 * Based on actual Atlas API log names and real process types from Atlas
 */
public enum AtlasLogType {
    
    // The 4 valid Atlas API log types
    MONGODB("mongodb.gz", 27017, false),
    MONGODB_AUDIT("mongodb-audit-log.gz", 27017, true),
    MONGOS("mongos.gz", 27016, false),
    MONGOS_AUDIT("mongos-audit-log.gz", 27016, true);
    
    private final String fileName;
    private final int standardPort;
    private final boolean isAuditLog;
    
    AtlasLogType(String fileName, int standardPort, boolean isAuditLog) {
        this.fileName = fileName;
        this.standardPort = standardPort;
        this.isAuditLog = isAuditLog;
    }
    
    // Getters
    public String getFileName() {
        return fileName;
    }
    
    public int getStandardPort() {
        return standardPort;
    }
    
    public boolean isAuditLog() {
        return isAuditLog;
    }
    
    /**
     * Get the clean log type name for filenames (removes .gz and hyphens)
     */
    public String getCleanName() {
        return fileName.replace(".gz", "").replace("-", "");
    }
    
    /**
     * Check if this log type is compatible with a given Atlas process type
     * Based on real Atlas process types like SHARD_PRIMARY, SHARD_MONGOS, etc.
     */
    public boolean isCompatibleWith(String atlasProcessType) {
        if (atlasProcessType == null) {
            return false;
        }
        
        String processTypeUpper = atlasProcessType.toUpperCase();
        
        // MongoDB logs (mongodb.gz, mongodb-audit-log.gz) come from mongod processes
        if (this == MONGODB || this == MONGODB_AUDIT) {
            return processTypeUpper.contains("PRIMARY") || 
                   processTypeUpper.contains("SECONDARY") ||
                   processTypeUpper.equals("MONGOD") ||
                   // Exclude mongos processes
                   (processTypeUpper.contains("SHARD") && !processTypeUpper.contains("MONGOS"));
        }
        
        // Mongos logs (mongos.gz, mongos-audit-log.gz) come from mongos processes  
        if (this == MONGOS || this == MONGOS_AUDIT) {
            return processTypeUpper.contains("MONGOS");
        }
        
        return false;
    }
    
    /**
     * Check if this log type is compatible with a given port (fallback method)
     */
    public boolean isCompatibleWithPort(int port) {
        return port == standardPort;
    }
    
    /**
     * Find AtlasLogType by filename
     */
    public static AtlasLogType fromFileName(String fileName) {
        for (AtlasLogType logType : values()) {
            if (logType.fileName.equals(fileName)) {
                return logType;
            }
        }
        throw new IllegalArgumentException("Unknown log type filename: " + fileName);
    }
    
    /**
     * Get all non-audit log types (default set)
     */
    public static List<AtlasLogType> getDefaultLogTypes() {
        return Arrays.stream(values())
                .filter(logType -> !logType.isAuditLog)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all log types including audit logs
     */
    public static List<AtlasLogType> getAllLogTypes() {
        return Arrays.asList(values());
    }
    
    /**
     * Get log types compatible with a specific Atlas process type
     */
    public static List<AtlasLogType> getCompatibleLogTypes(String atlasProcessType, boolean includeAudit) {
        return Arrays.stream(values())
                .filter(logType -> includeAudit || !logType.isAuditLog)
                .filter(logType -> logType.isCompatibleWith(atlasProcessType))
                .collect(Collectors.toList());
    }
    
    /**
     * Get log types compatible with a specific port (fallback method)
     */
    public static List<AtlasLogType> getCompatibleLogTypesByPort(int port, boolean includeAudit) {
        return Arrays.stream(values())
                .filter(logType -> includeAudit || !logType.isAuditLog)
                .filter(logType -> logType.isCompatibleWithPort(port))
                .collect(Collectors.toList());
    }
    
    /**
     * Convert list of filenames to AtlasLogType list
     */
    public static List<AtlasLogType> fromFileNames(List<String> fileNames) {
        return fileNames.stream()
                .map(AtlasLogType::fromFileName)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert AtlasLogType list to filenames
     */
    public static List<String> toFileNames(List<AtlasLogType> logTypes) {
        return logTypes.stream()
                .map(AtlasLogType::getFileName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get default log type filenames (non-audit)
     */
    public static List<String> getDefaultLogTypeFileNames() {
        return toFileNames(getDefaultLogTypes());
    }
    
    /**
     * Get all log type filenames including audit logs
     */
    public static List<String> getAllLogTypeFileNames() {
        return toFileNames(getAllLogTypes());
    }
    
    @Override
    public String toString() {
        return String.format("%s (port %d, audit: %s)", fileName, standardPort, isAuditLog);
    }
}