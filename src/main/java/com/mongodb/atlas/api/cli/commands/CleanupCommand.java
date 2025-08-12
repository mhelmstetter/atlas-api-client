package com.mongodb.atlas.api.cli.commands;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Callable;

import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.metrics.DuplicateCleanupUtility;
import com.mongodb.atlas.api.metrics.DuplicateCleanupUtility.CleanupResult;
import com.mongodb.atlas.api.metrics.DuplicateCleanupUtility.DetailedDuplicateGroup;
import com.mongodb.atlas.api.metrics.DuplicateCleanupUtility.DuplicateGroup;
import com.mongodb.atlas.api.metrics.DuplicateCleanupUtility.DuplicateStats;

import org.bson.Document;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * CLI command for cleaning up duplicate metrics in the atlas_metrics collection
 */
@Command(
    name = "cleanup", 
    description = "Clean up duplicate metrics measurements in atlas_metrics collection",
    subcommands = {
        CleanupCommand.StatsCommand.class,
        CleanupCommand.SampleCommand.class,
        CleanupCommand.RemoveCommand.class
    }
)
public class CleanupCommand implements Callable<Integer> {
    
    @Option(names = {"-c", "--connection"}, 
            description = "MongoDB connection string (default: mongodb://localhost:27017)")
    private String connectionString = "mongodb://localhost:27017";
    
    @Option(names = {"-d", "--database"}, 
            description = "Database name (default: atlas_metrics)")
    private String databaseName = "atlas_metrics";
    
    @Option(names = {"--collection"}, 
            description = "Collection name (default: metrics)")
    private String collectionName = "metrics";
    
    @Override
    public Integer call() throws Exception {
        System.out.println("🧹 Atlas Metrics Duplicate Cleanup Utility");
        System.out.println("==========================================");
        System.out.println();
        System.out.println("Available subcommands:");
        System.out.println("  stats   - Show duplicate statistics");
        System.out.println("  sample  - Show sample duplicates");  
        System.out.println("  remove  - Remove duplicates");
        System.out.println();
        System.out.println("Use 'atlas-cli cleanup <subcommand> --help' for more information");
        return 0;
    }
    
    /**
     * Show duplicate statistics
     */
    @Command(name = "stats", description = "Show statistics about duplicate measurements")
    public static class StatsCommand implements Callable<Integer> {
        
        @Option(names = {"-c", "--connection"}, 
                description = "MongoDB connection string (default: mongodb://localhost:27017)")
        private String connectionString = "mongodb://localhost:27017";
        
        @Option(names = {"-d", "--database"}, 
                description = "Database name (default: atlas_metrics)")
        private String databaseName = "atlas_metrics";
        
        @Option(names = {"--collection"}, 
                description = "Collection name (default: metrics)")
        private String collectionName = "metrics";
        
        @Override
        public Integer call() throws Exception {
            loadConnectionFromConfig();
            
            System.out.println("📊 Analyzing duplicate statistics...");
            System.out.println("Connection: " + connectionString);
            System.out.println("Database: " + databaseName);
            System.out.println("Collection: " + collectionName);
            System.out.println();
            
            try (DuplicateCleanupUtility cleanup = new DuplicateCleanupUtility(connectionString, databaseName, collectionName)) {
                DuplicateStats stats = cleanup.getDuplicateStats();
                
                System.out.println("📈 Duplicate Analysis Results");
                System.out.println("═".repeat(50));
                System.out.printf("Total documents:           %,d%n", stats.getTotalDocuments());
                System.out.printf("Duplicate groups:          %,d%n", stats.getDuplicateGroups());
                System.out.printf("Total duplicate documents: %,d%n", stats.getTotalDuplicateDocuments());
                System.out.printf("Documents to be removed:   %,d%n", stats.getDocumentsThatWouldBeRemoved());
                System.out.printf("Duplicate percentage:      %.2f%%%n", stats.getDuplicatePercentage());
                System.out.printf("Worst duplicate count:     %d%n", stats.getWorstDuplicateCount());
                System.out.printf("Avg duplicates per group:  %.2f%n", stats.getAvgDuplicatesPerGroup());
                System.out.printf("Analysis duration:         %.2f seconds%n", stats.getAnalysisDurationSeconds());
                
                if (stats.getDuplicateGroups() > 0) {
                    System.out.println();
                    System.out.println("🔧 Next Steps:");
                    System.out.println("  Run 'atlas-cli cleanup sample' to see examples");
                    System.out.println("  Run 'atlas-cli cleanup remove --dry-run' to preview cleanup");
                    System.out.println("  Run 'atlas-cli cleanup remove' to clean duplicates");
                } else {
                    System.out.println();
                    System.out.println("✅ No duplicates found - collection is clean!");
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error analyzing duplicates: " + e.getMessage());
                return 1;
            }
        }
        
        private void loadConnectionFromConfig() {
            try {
                File configFile = GlobalConfig.getConfigFile();
                if (configFile != null && configFile.exists()) {
                    Properties props = new Properties();
                    props.load(new java.io.FileInputStream(configFile));
                    
                    String configConnection = props.getProperty("metrics.connection.string");
                    if (configConnection != null && connectionString.equals("mongodb://localhost:27017")) {
                        connectionString = configConnection;
                    }
                    
                    String configDatabase = props.getProperty("metrics.database.name");
                    if (configDatabase != null && databaseName.equals("atlas_metrics")) {
                        databaseName = configDatabase;
                    }
                }
            } catch (Exception e) {
                // Ignore config loading errors, use defaults
            }
        }
    }
    
    /**
     * Show sample duplicate documents
     */
    @Command(name = "sample", description = "Show sample duplicate measurements for inspection")
    public static class SampleCommand implements Callable<Integer> {
        
        @Option(names = {"-c", "--connection"}, 
                description = "MongoDB connection string (default: mongodb://localhost:27017)")
        private String connectionString = "mongodb://localhost:27017";
        
        @Option(names = {"-d", "--database"}, 
                description = "Database name (default: atlas_metrics)")
        private String databaseName = "atlas_metrics";
        
        @Option(names = {"--collection"}, 
                description = "Collection name (default: metrics)")
        private String collectionName = "metrics";
        
        @Option(names = {"-n", "--limit"}, 
                description = "Number of duplicate groups to show (default: 10)")
        private int limit = 10;
        
        @Override
        public Integer call() throws Exception {
            loadConnectionFromConfig();
            
            System.out.println("🔍 Fetching sample duplicate measurements...");
            System.out.println("Connection: " + connectionString);
            System.out.println("Database: " + databaseName);
            System.out.println("Collection: " + collectionName);
            System.out.println();
            
            try (DuplicateCleanupUtility cleanup = new DuplicateCleanupUtility(connectionString, databaseName, collectionName)) {
                List<DuplicateGroup> samples = cleanup.getSampleDuplicates(limit);
                
                if (samples.isEmpty()) {
                    System.out.println("✅ No duplicate measurements found!");
                    return 0;
                }
                
                System.out.printf("📋 Sample Duplicate Groups (showing %d of potentially more)%n", samples.size());
                System.out.println("═".repeat(80));
                
                for (int i = 0; i < samples.size(); i++) {
                    DuplicateGroup group = samples.get(i);
                    
                    System.out.printf("%n🔄 Duplicate Group %d:%n", i + 1);
                    System.out.printf("   Timestamp:    %s%n", group.getTimestamp());
                    System.out.printf("   Host:         %s%n", group.getHost());
                    System.out.printf("   Metric:       %s%n", group.getMetric());
                    System.out.printf("   Project:      %s%n", group.getProjectName());
                    System.out.printf("   Value:        %s%n", group.getValue());
                    System.out.printf("   Duplicates:   %d copies%n", group.getDuplicateCount());
                    System.out.printf("   Document IDs: %s%n", String.join(", ", group.getDocumentIds()));
                }
                
                System.out.println();
                System.out.println("🔧 Next Steps:");
                System.out.println("  Run 'atlas-cli cleanup remove --dry-run' to preview cleanup");
                System.out.println("  Run 'atlas-cli cleanup remove' to clean duplicates");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error fetching sample duplicates: " + e.getMessage());
                return 1;
            }
        }
        
        private void loadConnectionFromConfig() {
            try {
                File configFile = GlobalConfig.getConfigFile();
                if (configFile != null && configFile.exists()) {
                    Properties props = new Properties();
                    props.load(new java.io.FileInputStream(configFile));
                    
                    String configConnection = props.getProperty("metrics.connection.string");
                    if (configConnection != null && connectionString.equals("mongodb://localhost:27017")) {
                        connectionString = configConnection;
                    }
                    
                    String configDatabase = props.getProperty("metrics.database.name");
                    if (configDatabase != null && databaseName.equals("atlas_metrics")) {
                        databaseName = configDatabase;
                    }
                }
            } catch (Exception e) {
                // Ignore config loading errors, use defaults
            }
        }
    }
    
    /**
     * Remove duplicate documents
     */
    @Command(name = "remove", description = "Remove duplicate measurements from the collection")
    public static class RemoveCommand implements Callable<Integer> {
        
        @Option(names = {"-c", "--connection"}, 
                description = "MongoDB connection string (default: mongodb://localhost:27017)")
        private String connectionString = "mongodb://localhost:27017";
        
        @Option(names = {"-d", "--database"}, 
                description = "Database name (default: atlas_metrics)")
        private String databaseName = "atlas_metrics";
        
        @Option(names = {"--collection"}, 
                description = "Collection name (default: metrics)")
        private String collectionName = "metrics";
        
        @Option(names = {"--dry-run"}, 
                description = "Preview what would be removed without actually removing anything")
        private boolean dryRun = false;
        
        @Option(names = {"-y", "--yes"}, 
                description = "Skip confirmation prompt")
        private boolean skipConfirmation = false;
        
        @Override
        public Integer call() throws Exception {
            loadConnectionFromConfig();
            
            if (dryRun) {
                System.out.println("🔍 DRY RUN - Preview duplicate cleanup...");
            } else {
                System.out.println("🧹 Remove duplicate measurements...");
            }
            System.out.println("Connection: " + connectionString);
            System.out.println("Database: " + databaseName);
            System.out.println("Collection: " + collectionName);
            System.out.println();
            
            try (DuplicateCleanupUtility cleanup = new DuplicateCleanupUtility(connectionString, databaseName, collectionName)) {
                
                if (!dryRun && !skipConfirmation) {
                    // First show stats to help user understand what will be removed
                    System.out.println("📊 Analyzing collection before cleanup...");
                    DuplicateStats stats = cleanup.getDuplicateStats();
                    
                    if (stats.getDuplicateGroups() == 0) {
                        System.out.println("✅ No duplicates found - collection is already clean!");
                        return 0;
                    }
                    
                    System.out.println();
                    System.out.println("📈 Cleanup Preview");
                    System.out.println("═".repeat(50));
                    System.out.printf("Total documents:         %,d%n", stats.getTotalDocuments());
                    System.out.printf("Duplicate groups:        %,d%n", stats.getDuplicateGroups());
                    System.out.printf("Documents to be removed: %,d%n", stats.getDocumentsThatWouldBeRemoved());
                    System.out.printf("Duplicate percentage:    %.2f%%%n", stats.getDuplicatePercentage());
                    System.out.println();
                    
                    // Show example duplicate documents
                    System.out.println("📋 Example Duplicate Documents (showing 2 worst cases):");
                    System.out.println("─".repeat(60));
                    List<DetailedDuplicateGroup> examples = cleanup.getDetailedSampleDuplicates(2);
                    
                    for (int i = 0; i < examples.size(); i++) {
                        DetailedDuplicateGroup example = examples.get(i);
                        System.out.printf("%n🔄 Example %d - %d duplicates of same measurement:%n", i + 1, example.getDuplicateCount());
                        System.out.printf("   Timestamp: %s%n", example.getTimestamp());
                        System.out.printf("   Host:      %s%n", example.getHost());
                        System.out.printf("   Metric:    %s%n", example.getMetric());
                        System.out.printf("   Project:   %s%n", example.getProjectName());
                        System.out.printf("   Value:     %s%n", example.getValue());
                        System.out.println();
                        
                        Document keptDoc = example.getKeptDocument();
                        List<Document> removedDocs = example.getDocumentsToRemove();
                        
                        System.out.printf("   ✅ WILL KEEP (oldest ObjectId): %s%n", keptDoc.get("_id"));
                        System.out.printf("      Inserted: %s%n", keptDoc.get("_id"));
                        
                        for (int j = 0; j < Math.min(removedDocs.size(), 3); j++) {
                            Document doc = removedDocs.get(j);
                            System.out.printf("   ❌ WILL REMOVE: %s%n", doc.get("_id"));
                        }
                        
                        if (removedDocs.size() > 3) {
                            System.out.printf("   ❌ WILL REMOVE: ... and %d more duplicates%n", removedDocs.size() - 3);
                        }
                    }
                    
                    System.out.println();
                    System.out.println("⚠️  WARNING: This operation will permanently delete duplicate documents!");
                    System.out.println("   Each duplicate group will keep the document with the lowest ObjectId (earliest inserted).");
                    System.out.println("   All other duplicates will be removed.");
                    System.out.println();
                    System.out.print("Are you sure you want to proceed? [y/N]: ");
                    
                    Scanner scanner = new Scanner(System.in);
                    String confirmation = scanner.nextLine().trim();
                    
                    if (!"y".equalsIgnoreCase(confirmation) && !"yes".equalsIgnoreCase(confirmation)) {
                        System.out.println("❌ Operation cancelled by user");
                        return 0;
                    }
                    
                    System.out.println();
                }
                
                // Perform the cleanup
                CleanupResult result = cleanup.cleanupDuplicates(dryRun);
                
                System.out.println("📊 Cleanup Results");
                System.out.println("═".repeat(50));
                System.out.printf("Duplicate groups found:    %,d%n", result.getDuplicateGroups());
                System.out.printf("Total duplicate documents: %,d%n", result.getDuplicateDocuments());
                
                if (dryRun) {
                    System.out.printf("Would remove:              %,d documents%n", result.getDocumentsRemoved());
                    
                    // Show examples in dry run mode too
                    if (result.getDuplicateGroups() > 0) {
                        System.out.println();
                        System.out.println("📋 Example Duplicates (showing 3 worst cases):");
                        System.out.println("─".repeat(60));
                        List<DetailedDuplicateGroup> examples = cleanup.getDetailedSampleDuplicates(3);
                        
                        for (int i = 0; i < examples.size(); i++) {
                            DetailedDuplicateGroup example = examples.get(i);
                            System.out.printf("%n🔄 Example %d - %d duplicates of same measurement:%n", i + 1, example.getDuplicateCount());
                            System.out.printf("   Timestamp: %s%n", example.getTimestamp());
                            System.out.printf("   Host:      %s%n", example.getHost());
                            System.out.printf("   Metric:    %s%n", example.getMetric());
                            System.out.printf("   Project:   %s%n", example.getProjectName());
                            System.out.printf("   Value:     %s%n", example.getValue());
                            
                            Document keptDoc = example.getKeptDocument();
                            List<Document> removedDocs = example.getDocumentsToRemove();
                            
                            System.out.printf("   ✅ Would keep: %s%n", keptDoc.get("_id"));
                            System.out.printf("   ❌ Would remove: %d document(s)%n", removedDocs.size());
                        }
                    }
                    
                    System.out.println();
                    System.out.println("✅ Dry run completed successfully!");
                    System.out.println("🔧 To actually remove duplicates, run: atlas-cli cleanup remove");
                } else {
                    System.out.printf("Documents removed:         %,d%n", result.getDocumentsRemoved());
                    System.out.printf("Cleanup duration:          %.2f seconds%n", result.getDurationSeconds());
                    System.out.println();
                    System.out.println("✅ Duplicate cleanup completed successfully!");
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error during cleanup: " + e.getMessage());
                return 1;
            }
        }
        
        private void loadConnectionFromConfig() {
            try {
                File configFile = GlobalConfig.getConfigFile();
                if (configFile != null && configFile.exists()) {
                    Properties props = new Properties();
                    props.load(new java.io.FileInputStream(configFile));
                    
                    String configConnection = props.getProperty("metrics.connection.string");
                    if (configConnection != null && connectionString.equals("mongodb://localhost:27017")) {
                        connectionString = configConnection;
                    }
                    
                    String configDatabase = props.getProperty("metrics.database.name");
                    if (configDatabase != null && databaseName.equals("atlas_metrics")) {
                        databaseName = configDatabase;
                    }
                }
            } catch (Exception e) {
                // Ignore config loading errors, use defaults
            }
        }
    }
}