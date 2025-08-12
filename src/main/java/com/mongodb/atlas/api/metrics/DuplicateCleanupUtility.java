package com.mongodb.atlas.api.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;

/**
 * Utility to find and remove duplicate metrics measurements from the atlas_metrics.metrics collection.
 * 
 * Duplicates are identified as documents that have identical:
 * - timestamp
 * - metadata.host
 * - metadata.metric
 * - metadata.projectName
 * - value
 * 
 * When duplicates are found, all but one document (with the lowest ObjectId) are removed.
 */
public class DuplicateCleanupUtility implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(DuplicateCleanupUtility.class);
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> metricsCollection;
    
    /**
     * Creates a new DuplicateCleanupUtility
     * 
     * @param connectionString MongoDB connection string
     * @param databaseName Database name (typically "atlas_metrics")
     * @param collectionName Collection name (typically "metrics")
     */
    public DuplicateCleanupUtility(String connectionString, String databaseName, String collectionName) {
        logger.info("Connecting to MongoDB at {}...", connectionString);
        this.mongoClient = MongoClients.create(connectionString);
        this.database = mongoClient.getDatabase(databaseName);
        this.metricsCollection = database.getCollection(collectionName);
        logger.info("Connected to {}.{}", databaseName, collectionName);
    }
    
    /**
     * Find and remove all duplicate measurements in the collection
     * 
     * @param dryRun If true, only count duplicates without removing them
     * @return CleanupResult with statistics about the operation
     */
    public CleanupResult cleanupDuplicates(boolean dryRun) {
        logger.info("Starting duplicate cleanup (dryRun={})", dryRun);
        long startTime = System.currentTimeMillis();
        
        CleanupResult result = new CleanupResult();
        
        // Use aggregation to find duplicate groups
        List<Document> pipeline = List.of(
            // Group by the fields that should be unique
            new Document("$group", new Document("_id", new Document()
                    .append("timestamp", "$timestamp")
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")
                    .append("value", "$value"))
                .append("docs", new Document("$push", new Document()
                    .append("id", "$_id")
                    .append("timestamp", "$timestamp")
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")
                    .append("value", "$value")))
                .append("count", new Document("$sum", 1))),
            
            // Only keep groups with more than one document (duplicates)
            new Document("$match", new Document("count", new Document("$gt", 1))),
            
            // Sort by count descending to process worst duplicates first
            new Document("$sort", new Document("count", -1))
        );
        
        logger.info("Running aggregation to find duplicate groups...");
        List<Document> duplicateGroups = metricsCollection.aggregate(pipeline).into(new ArrayList<>());
        
        if (duplicateGroups.isEmpty()) {
            logger.info("No duplicates found in the collection");
            result.setDuplicateGroups(0);
            result.setDuplicateDocuments(0);
            result.setDocumentsRemoved(0);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        logger.info("Found {} duplicate groups", duplicateGroups.size());
        result.setDuplicateGroups(duplicateGroups.size());
        
        AtomicInteger totalDuplicateDocuments = new AtomicInteger(0);
        AtomicInteger documentsRemoved = new AtomicInteger(0);
        AtomicInteger groupsProcessed = new AtomicInteger(0);
        
        for (Document group : duplicateGroups) {
            @SuppressWarnings("unchecked")
            List<Document> docs = (List<Document>) group.get("docs");
            int count = group.getInteger("count");
            
            totalDuplicateDocuments.addAndGet(count);
            
            if (docs.size() > 1) {
                // Sort by _id to keep the document with the smallest ObjectId (earliest inserted)
                docs.sort((d1, d2) -> {
                    Object id1 = d1.get("id");
                    Object id2 = d2.get("id");
                    if (id1 instanceof org.bson.types.ObjectId && id2 instanceof org.bson.types.ObjectId) {
                        return ((org.bson.types.ObjectId) id1).compareTo((org.bson.types.ObjectId) id2);
                    }
                    return 0;
                });
                
                // Keep the first document (smallest ObjectId), remove the rest
                List<Object> idsToRemove = new ArrayList<>();
                for (int i = 1; i < docs.size(); i++) {
                    idsToRemove.add(docs.get(i).get("id"));
                }
                
                if (!dryRun && !idsToRemove.isEmpty()) {
                    // Remove the duplicate documents
                    DeleteResult deleteResult = metricsCollection.deleteMany(
                        Filters.in("_id", idsToRemove)
                    );
                    
                    long deletedCount = deleteResult.getDeletedCount();
                    documentsRemoved.addAndGet((int) deletedCount);
                    
                    logger.debug("Removed {} duplicates for group with {} total documents", 
                        deletedCount, docs.size());
                } else if (dryRun) {
                    documentsRemoved.addAndGet(idsToRemove.size());
                    logger.debug("Would remove {} duplicates for group with {} total documents", 
                        idsToRemove.size(), docs.size());
                }
            }
            
            groupsProcessed.incrementAndGet();
            
            // Log progress every 100 groups
            if (groupsProcessed.get() % 100 == 0) {
                logger.info("Processed {}/{} duplicate groups, removed {} documents", 
                    groupsProcessed.get(), duplicateGroups.size(), documentsRemoved.get());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        result.setDuplicateDocuments(totalDuplicateDocuments.get());
        result.setDocumentsRemoved(documentsRemoved.get());
        result.setDurationMs(duration);
        
        if (dryRun) {
            logger.info("DRY RUN COMPLETE: Found {} duplicate groups with {} total duplicate documents. " +
                "Would remove {} documents in {:.2f} seconds", 
                result.getDuplicateGroups(), result.getDuplicateDocuments(), 
                result.getDocumentsRemoved(), duration / 1000.0);
        } else {
            logger.info("CLEANUP COMPLETE: Processed {} duplicate groups, removed {} of {} duplicate documents in {:.2f} seconds", 
                result.getDuplicateGroups(), result.getDocumentsRemoved(), 
                result.getDuplicateDocuments(), duration / 1000.0);
        }
        
        return result;
    }
    
    /**
     * Get statistics about duplicates without removing them
     * 
     * @return DuplicateStats with information about duplicate metrics
     */
    public DuplicateStats getDuplicateStats() {
        logger.info("Analyzing duplicate statistics...");
        long startTime = System.currentTimeMillis();
        
        // Count total documents
        long totalDocuments = metricsCollection.countDocuments();
        
        // Find duplicate groups using aggregation
        List<Document> pipeline = List.of(
            new Document("$group", new Document("_id", new Document()
                    .append("timestamp", "$timestamp")
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")
                    .append("value", "$value"))
                .append("count", new Document("$sum", 1))
                .append("hostMetric", new Document("$first", new Document()
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")))),
            
            new Document("$match", new Document("count", new Document("$gt", 1))),
            
            new Document("$group", new Document("_id", null)
                .append("duplicateGroups", new Document("$sum", 1))
                .append("totalDuplicateDocuments", new Document("$sum", "$count"))
                .append("worstDuplicateCount", new Document("$max", "$count"))
                .append("avgDuplicatesPerGroup", new Document("$avg", "$count")))
        );
        
        List<Document> stats = metricsCollection.aggregate(pipeline).into(new ArrayList<>());
        
        DuplicateStats duplicateStats = new DuplicateStats();
        duplicateStats.setTotalDocuments(totalDocuments);
        duplicateStats.setAnalysisDurationMs(System.currentTimeMillis() - startTime);
        
        if (stats.isEmpty()) {
            duplicateStats.setDuplicateGroups(0);
            duplicateStats.setTotalDuplicateDocuments(0);
            duplicateStats.setDocumentsThatWouldBeRemoved(0);
            duplicateStats.setWorstDuplicateCount(0);
            duplicateStats.setAvgDuplicatesPerGroup(0.0);
        } else {
            Document stat = stats.get(0);
            int duplicateGroups = stat.getInteger("duplicateGroups", 0);
            int totalDuplicateDocuments = stat.getInteger("totalDuplicateDocuments", 0);
            
            duplicateStats.setDuplicateGroups(duplicateGroups);
            duplicateStats.setTotalDuplicateDocuments(totalDuplicateDocuments);
            duplicateStats.setDocumentsThatWouldBeRemoved(totalDuplicateDocuments - duplicateGroups); // Keep one per group
            duplicateStats.setWorstDuplicateCount(stat.getInteger("worstDuplicateCount", 0));
            duplicateStats.setAvgDuplicatesPerGroup(stat.getDouble("avgDuplicatesPerGroup"));
        }
        
        logger.info("Duplicate analysis complete: {} groups, {} duplicate documents, {} would be removed", 
            duplicateStats.getDuplicateGroups(), duplicateStats.getTotalDuplicateDocuments(), 
            duplicateStats.getDocumentsThatWouldBeRemoved());
        
        return duplicateStats;
    }
    
    /**
     * Find a sample of duplicate documents for inspection
     * 
     * @param limit Maximum number of duplicate groups to return
     * @return List of sample duplicate groups
     */
    public List<DuplicateGroup> getSampleDuplicates(int limit) {
        logger.info("Fetching sample of {} duplicate groups...", limit);
        
        List<Document> pipeline = List.of(
            new Document("$group", new Document("_id", new Document()
                    .append("timestamp", "$timestamp")
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")
                    .append("value", "$value"))
                .append("docs", new Document("$push", new Document()
                    .append("id", "$_id")
                    .append("timestamp", "$timestamp")))
                .append("count", new Document("$sum", 1))),
            
            new Document("$match", new Document("count", new Document("$gt", 1))),
            new Document("$sort", new Document("count", -1)),
            new Document("$limit", limit)
        );
        
        List<Document> results = metricsCollection.aggregate(pipeline).into(new ArrayList<>());
        List<DuplicateGroup> duplicateGroups = new ArrayList<>();
        
        for (Document result : results) {
            Document id = result.get("_id", Document.class);
            @SuppressWarnings("unchecked")
            List<Document> docs = (List<Document>) result.get("docs");
            
            DuplicateGroup group = new DuplicateGroup();
            group.setTimestamp(id.getDate("timestamp").toInstant());
            group.setHost(id.getString("host"));
            group.setMetric(id.getString("metric"));
            group.setProjectName(id.getString("projectName"));
            group.setValue(id.getDouble("value"));
            group.setDuplicateCount(result.getInteger("count"));
            group.setDocumentIds(docs.stream()
                .map(doc -> doc.get("id").toString())
                .toArray(String[]::new));
            
            duplicateGroups.add(group);
        }
        
        logger.info("Found {} sample duplicate groups", duplicateGroups.size());
        return duplicateGroups;
    }
    
    /**
     * Get detailed sample duplicate documents with full document content
     * 
     * @param limit Maximum number of duplicate groups to return
     * @return List of detailed duplicate groups with full documents
     */
    public List<DetailedDuplicateGroup> getDetailedSampleDuplicates(int limit) {
        logger.info("Fetching detailed sample of {} duplicate groups...", limit);
        
        // First get the duplicate groups using the same aggregation as before
        List<Document> pipeline = List.of(
            new Document("$group", new Document("_id", new Document()
                    .append("timestamp", "$timestamp")
                    .append("host", "$metadata.host")
                    .append("metric", "$metadata.metric")
                    .append("projectName", "$metadata.projectName")
                    .append("value", "$value"))
                .append("docIds", new Document("$push", "$_id"))
                .append("count", new Document("$sum", 1))),
            
            new Document("$match", new Document("count", new Document("$gt", 1))),
            new Document("$sort", new Document("count", -1)),
            new Document("$limit", limit)
        );
        
        List<Document> results = metricsCollection.aggregate(pipeline).into(new ArrayList<>());
        List<DetailedDuplicateGroup> detailedGroups = new ArrayList<>();
        
        for (Document result : results) {
            Document id = result.get("_id", Document.class);
            @SuppressWarnings("unchecked")
            List<Object> docIds = (List<Object>) result.get("docIds");
            
            // Fetch the full documents for this duplicate group
            List<Document> fullDocs = metricsCollection.find(Filters.in("_id", docIds)).into(new ArrayList<>());
            
            // Sort by ObjectId to match cleanup behavior (keep first, remove rest)
            fullDocs.sort((d1, d2) -> {
                Object id1 = d1.get("_id");
                Object id2 = d2.get("_id");
                if (id1 instanceof org.bson.types.ObjectId && id2 instanceof org.bson.types.ObjectId) {
                    return ((org.bson.types.ObjectId) id1).compareTo((org.bson.types.ObjectId) id2);
                }
                return 0;
            });
            
            DetailedDuplicateGroup group = new DetailedDuplicateGroup();
            group.setTimestamp(id.getDate("timestamp").toInstant());
            group.setHost(id.getString("host"));
            group.setMetric(id.getString("metric"));
            group.setProjectName(id.getString("projectName"));
            group.setValue(id.getDouble("value"));
            group.setDuplicateCount(result.getInteger("count"));
            group.setDocuments(fullDocs);
            
            detailedGroups.add(group);
        }
        
        logger.info("Found {} detailed sample duplicate groups", detailedGroups.size());
        return detailedGroups;
    }
    
    /**
     * Close the MongoDB connection
     */
    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("Closed MongoDB connection");
        }
    }
    
    /**
     * Result of a cleanup operation
     */
    public static class CleanupResult {
        private int duplicateGroups;
        private int duplicateDocuments;
        private int documentsRemoved;
        private long durationMs;
        
        // Getters and setters
        public int getDuplicateGroups() { return duplicateGroups; }
        public void setDuplicateGroups(int duplicateGroups) { this.duplicateGroups = duplicateGroups; }
        
        public int getDuplicateDocuments() { return duplicateDocuments; }
        public void setDuplicateDocuments(int duplicateDocuments) { this.duplicateDocuments = duplicateDocuments; }
        
        public int getDocumentsRemoved() { return documentsRemoved; }
        public void setDocumentsRemoved(int documentsRemoved) { this.documentsRemoved = documentsRemoved; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        
        public double getDurationSeconds() { return durationMs / 1000.0; }
    }
    
    /**
     * Statistics about duplicates in the collection
     */
    public static class DuplicateStats {
        private long totalDocuments;
        private int duplicateGroups;
        private int totalDuplicateDocuments;
        private int documentsThatWouldBeRemoved;
        private int worstDuplicateCount;
        private double avgDuplicatesPerGroup;
        private long analysisDurationMs;
        
        // Getters and setters
        public long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }
        
        public int getDuplicateGroups() { return duplicateGroups; }
        public void setDuplicateGroups(int duplicateGroups) { this.duplicateGroups = duplicateGroups; }
        
        public int getTotalDuplicateDocuments() { return totalDuplicateDocuments; }
        public void setTotalDuplicateDocuments(int totalDuplicateDocuments) { this.totalDuplicateDocuments = totalDuplicateDocuments; }
        
        public int getDocumentsThatWouldBeRemoved() { return documentsThatWouldBeRemoved; }
        public void setDocumentsThatWouldBeRemoved(int documentsThatWouldBeRemoved) { this.documentsThatWouldBeRemoved = documentsThatWouldBeRemoved; }
        
        public int getWorstDuplicateCount() { return worstDuplicateCount; }
        public void setWorstDuplicateCount(int worstDuplicateCount) { this.worstDuplicateCount = worstDuplicateCount; }
        
        public double getAvgDuplicatesPerGroup() { return avgDuplicatesPerGroup; }
        public void setAvgDuplicatesPerGroup(double avgDuplicatesPerGroup) { this.avgDuplicatesPerGroup = avgDuplicatesPerGroup; }
        
        public long getAnalysisDurationMs() { return analysisDurationMs; }
        public void setAnalysisDurationMs(long analysisDurationMs) { this.analysisDurationMs = analysisDurationMs; }
        
        public double getAnalysisDurationSeconds() { return analysisDurationMs / 1000.0; }
        
        public double getDuplicatePercentage() {
            return totalDocuments > 0 ? (totalDuplicateDocuments * 100.0) / totalDocuments : 0.0;
        }
    }
    
    /**
     * Represents a group of duplicate documents
     */
    public static class DuplicateGroup {
        private Instant timestamp;
        private String host;
        private String metric;
        private String projectName;
        private Double value;
        private int duplicateCount;
        private String[] documentIds;
        
        // Getters and setters
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
        
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        
        public String[] getDocumentIds() { return documentIds; }
        public void setDocumentIds(String[] documentIds) { this.documentIds = documentIds; }
    }
    
    /**
     * Represents a group of duplicate documents with full document details
     */
    public static class DetailedDuplicateGroup {
        private Instant timestamp;
        private String host;
        private String metric;
        private String projectName;
        private Double value;
        private int duplicateCount;
        private List<Document> documents;
        
        // Getters and setters
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
        
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        
        public List<Document> getDocuments() { return documents; }
        public void setDocuments(List<Document> documents) { this.documents = documents; }
        
        /**
         * Get the document that will be kept (first one, smallest ObjectId)
         */
        public Document getKeptDocument() {
            return documents != null && !documents.isEmpty() ? documents.get(0) : null;
        }
        
        /**
         * Get the documents that will be removed (all except the first one)
         */
        public List<Document> getDocumentsToRemove() {
            if (documents == null || documents.size() <= 1) {
                return new ArrayList<>();
            }
            return documents.subList(1, documents.size());
        }
    }
}