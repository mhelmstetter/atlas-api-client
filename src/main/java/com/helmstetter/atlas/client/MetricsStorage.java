package com.helmstetter.atlas.client;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesOptions;


/**
 * Handles storing Atlas metrics in a MongoDB timeseries collection
 * Enhanced with better timestamp tracking for optimized metric collection
 */
public class MetricsStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsStorage.class);
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final String collectionName;
    private MongoCollection<Document> metricsCollection;
    
    // Cache to keep track of the last timestamp for each host+metric combination
    private final Map<String, Instant> lastTimestampCache = new HashMap<>();
    
    /**
     * Creates a new MetricsStorage with the specified MongoDB connection string
     * 
     * @param connectionString MongoDB connection string
     * @param databaseName Database name to use
     * @param collectionName Collection name to use
     */
    public MetricsStorage(String connectionString, String databaseName, String collectionName) {
        this.mongoClient = MongoClients.create(connectionString);
        this.database = mongoClient.getDatabase(databaseName);
        this.collectionName = collectionName;
        
        // Initialize the collection if it doesn't exist
        initializeCollection();
        
        // Build the cache of last timestamps
        buildLastTimestampCache();
        
        logger.info("Initialized metrics storage with database: {}, collection: {}", 
                databaseName, collectionName);
    }
    
    /**
     * Initialize the timeseries collection if it doesn't exist
     */
    private void initializeCollection() {
        boolean collectionExists = database.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
        
        if (!collectionExists) {
            logger.info("Creating timeseries collection: {}", collectionName);
            
            // Configure the timeseries collection
            TimeSeriesOptions timeSeriesOptions = new TimeSeriesOptions("timestamp")
                    .metaField("metadata");
            
            CreateCollectionOptions options = new CreateCollectionOptions()
                    .timeSeriesOptions(timeSeriesOptions);
            
            // Create the collection
            database.createCollection(collectionName, options);
            
            // Create indexes for faster queries
            metricsCollection = database.getCollection(collectionName);
            metricsCollection.createIndex(Indexes.ascending("metadata.host", "metadata.metric"));
            metricsCollection.createIndex(Indexes.ascending("metadata.projectName"));
            metricsCollection.createIndex(Indexes.ascending("metadata.partition"));
        } else {
            logger.info("Using existing collection: {}", collectionName);
            metricsCollection = database.getCollection(collectionName);
        }
    }
    
    /**
     * Build a cache of the last timestamp for each host+metric combination
     * This helps with efficiently checking for duplicates
     */
    private void buildLastTimestampCache() {
        logger.info("Building last timestamp cache...");
        
        // Find all distinct host+metric combinations
        List<String> distinctHosts = metricsCollection.distinct("metadata.host", String.class)
                .into(new ArrayList<>());
        
        int cacheEntries = 0;
        
        for (String host : distinctHosts) {
            
            List<String> metrics = metricsCollection.distinct("metadata.metric", 
                    Filters.eq("metadata.host", host), String.class)
                    .into(new ArrayList<>());
            
            for (String metric : metrics) {
                
                // For each host+metric, find the document with the latest timestamp
                Document latestDoc = metricsCollection.find(
                        Filters.and(
                            Filters.eq("metadata.host", host),
                            Filters.eq("metadata.metric", metric)
                        ))
                        .sort(Sorts.descending("timestamp"))
                        .first();
                
                if (latestDoc != null) {
                    Instant timestamp = latestDoc.getDate("timestamp").toInstant();
                    String cacheKey = host + ":" + metric;
                    lastTimestampCache.put(cacheKey, timestamp);
                    cacheEntries++;
                    
                    // Log progress periodically
                    if (cacheEntries % 100 == 0) {
                        logger.info("Cached {} timestamp entries...", cacheEntries);
                    }
                }
            }
        }
        
        logger.info("Completed last timestamp cache with {} entries", cacheEntries);
    }
    
    /**
     * Store metrics measurements in the timeseries collection
     * 
     * @param projectName Atlas project name
     * @param host Hostname of the MongoDB instance
     * @param port Port of the MongoDB instance
     * @param partition Optional partition name for disk metrics (can be null)
     * @param metric Metric name
     * @param measurements List of measurement data points
     * @return Number of new documents inserted
     */
    public int storeMetrics(String projectName, String host, int port, 
                          String partition, String metric, List<Map<String, Object>> dataPoints) {
        
        if (dataPoints == null || dataPoints.isEmpty()) {
            logger.debug("No data points to store for {}:{} metric {}", host, port, metric);
            return 0;
        }
        
        String hostPort = host + ":" + port;
        String cacheKey = hostPort + ":" + metric;
        if (partition != null) {
            cacheKey += ":" + partition;
        }
        
        Instant lastTimestamp = lastTimestampCache.getOrDefault(cacheKey, Instant.EPOCH);
        logger.debug("Last timestamp for {}: {}", cacheKey, lastTimestamp);
        
        List<Document> documents = new ArrayList<>();
        int newPoints = 0;
        int skippedPoints = 0;
        Instant latestTimestampInBatch = lastTimestamp;
        
        for (Map<String, Object> dataPoint : dataPoints) {
            // Extract timestamp and value
            String timestampStr = (String) dataPoint.get("timestamp");
            Object valueObj = dataPoint.get("value");
            Double value = null;
            
            // Parse timestamp
            Instant timestamp;
            try {
                timestamp = Instant.parse(timestampStr);
            } catch (DateTimeParseException e) {
                logger.warn("Failed to parse timestamp: {}", timestampStr);
                continue;
            }
            
            // Skip if this timestamp is not newer than the last one we have
            if (timestamp.isBefore(lastTimestamp) || timestamp.equals(lastTimestamp)) {
                skippedPoints++;
                continue;
            }
            
            // Update the latest timestamp in this batch
            if (timestamp.isAfter(latestTimestampInBatch)) {
                latestTimestampInBatch = timestamp;
            }
            
            // Convert value to double
            if (valueObj instanceof Integer) {
                value = ((Integer) valueObj).doubleValue();
            } else if (valueObj instanceof Double) {
                value = (Double) valueObj;
            } else if (valueObj instanceof Long) {
                value = ((Long) valueObj).doubleValue();
            }
            
            if (value == null) {
                logger.warn("Null or invalid value for {}:{} metric {} at {}", 
                        host, port, metric, timestampStr);
                continue;
            }
            
            // Create the document
            Document doc = new Document();
            doc.append("timestamp", java.util.Date.from(timestamp));
            doc.append("value", value);
            
            // Add metadata
            Document metadata = new Document();
            metadata.append("projectName", projectName);
            metadata.append("host", hostPort);
            metadata.append("metric", metric);
            if (partition != null) {
                metadata.append("partition", partition);
            }
            
            doc.append("metadata", metadata);
            documents.add(doc);
            newPoints++;
        }
        
        // Insert documents if we have any
        if (!documents.isEmpty()) {
            try {
                metricsCollection.insertMany(documents, new InsertManyOptions().ordered(false));
                
                // Update the cache with the latest timestamp
                lastTimestampCache.put(cacheKey, latestTimestampInBatch);
                
                logger.info("Stored {} new data points for {}:{} metric {} (skipped {} duplicates)", 
                        newPoints, host, port, metric, skippedPoints);
            } catch (Exception e) {
                logger.error("Failed to store metrics for {}:{} metric {}: {}", 
                        host, port, metric, e.getMessage());
            }
        } else {
            logger.info("No new data points to store for {}:{} metric {} (all {} points were duplicates)", 
                    host, port, metric, skippedPoints);
        }
        
        return newPoints;
    }
    
    /**
     * Get the latest timestamp for a specific metric across all hosts and projects
     * 
     * @param metric The metric name
     * @return The latest timestamp, or EPOCH if no data found
     */
    public Instant getLatestTimestampForMetric(String metric) {
        Document latest = metricsCollection.find(
                Filters.eq("metadata.metric", metric))
                .sort(Sorts.descending("timestamp"))
                .first();
        
        if (latest != null) {
            return latest.getDate("timestamp").toInstant();
        }
        
        return Instant.EPOCH;
    }
    
    /**
     * Get the latest timestamp for a specific project and metric
     * 
     * @param projectName The project name
     * @param metric The metric name
     * @return The latest timestamp, or EPOCH if no data found
     */
    public Instant getLatestTimestampForProjectMetric(String projectName, String metric) {
        Document latest = metricsCollection.find(
                Filters.and(
                    Filters.eq("metadata.projectName", projectName),
                    Filters.eq("metadata.metric", metric)
                ))
                .sort(Sorts.descending("timestamp"))
                .first();
        
        if (latest != null) {
            return latest.getDate("timestamp").toInstant();
        }
        
        return Instant.EPOCH;
    }
    
    /**
     * Get the latest timestamp for a specific host and metric
     * 
     * @param host The host identifier (hostname:port)
     * @param metric The metric name
     * @return The latest timestamp, or EPOCH if no data found
     */
    public Instant getLatestTimestampForHostMetric(String host, String metric) {
        Document latest = metricsCollection.find(
                Filters.and(
                    Filters.eq("metadata.host", host),
                    Filters.eq("metadata.metric", metric)
                ))
                .sort(Sorts.descending("timestamp"))
                .first();
        
        if (latest != null) {
            return latest.getDate("timestamp").toInstant();
        }
        
        return Instant.EPOCH;
    }
    
    /**
     * Get the latest timestamp for a specific host, partition and metric
     * 
     * @param host The host identifier (hostname:port)
     * @param partition The partition name 
     * @param metric The metric name
     * @return The latest timestamp, or EPOCH if no data found
     */
    public Instant getLatestTimestampForHostPartitionMetric(String host, String partition, String metric) {
        Document latest = metricsCollection.find(
                Filters.and(
                    Filters.eq("metadata.host", host),
                    Filters.eq("metadata.partition", partition),
                    Filters.eq("metadata.metric", metric)
                ))
                .sort(Sorts.descending("timestamp"))
                .first();
        
        if (latest != null) {
            return latest.getDate("timestamp").toInstant();
        }
        
        return Instant.EPOCH;
    }
    
    /**
     * Get metrics from the timeseries collection
     * 
     * @param projectName Optional project name filter (can be null)
     * @param host Optional hostname filter (can be null)
     * @param metric Optional metric name filter (can be null)
     * @param startTime Start time for the query
     * @param endTime End time for the query (can be null for 'now')
     * @return List of measurement documents
     */
    public List<Document> getMetrics(String projectName, String host, String metric,
                                    Instant startTime, Instant endTime) {
        
        // Build the filter
        List<org.bson.conversions.Bson> filters = new ArrayList<>();
        
        if (projectName != null) {
            filters.add(Filters.eq("metadata.projectName", projectName));
        }
        
        if (host != null) {
            filters.add(Filters.eq("metadata.host", host));
        }
        
        if (metric != null) {
            filters.add(Filters.eq("metadata.metric", metric));
        }
        
        // Time range filter
        filters.add(Filters.gte("timestamp", java.util.Date.from(startTime)));
        if (endTime != null) {
            filters.add(Filters.lte("timestamp", java.util.Date.from(endTime)));
        }
        
        // Execute the query
        return metricsCollection.find(Filters.and(filters))
                .sort(Sorts.ascending("timestamp"))
                .into(new ArrayList<>());
    }
    
    /**
     * Get the start time of the earliest available data for the given filters
     */
    public Instant getEarliestDataTime(String projectName, String host, String metric) {
        List<org.bson.conversions.Bson> filters = new ArrayList<>();
        
        if (projectName != null) {
            filters.add(Filters.eq("metadata.projectName", projectName));
        }
        
        if (host != null) {
            filters.add(Filters.eq("metadata.host", host));
        }
        
        if (metric != null) {
            filters.add(Filters.eq("metadata.metric", metric));
        }
        
        Document earliest = metricsCollection.find(Filters.and(filters))
                .sort(Sorts.ascending("timestamp"))
                .first();
        
        if (earliest != null) {
            return earliest.getDate("timestamp").toInstant();
        }
        
        // Default to epoch if no data exists
        return Instant.EPOCH;
    }
    
    /**
     * Get the end time of the latest available data for the given filters
     */
    public Instant getLatestDataTime(String projectName, String host, String metric) {
        List<org.bson.conversions.Bson> filters = new ArrayList<>();
        
        if (projectName != null) {
            filters.add(Filters.eq("metadata.projectName", projectName));
        }
        
        if (host != null) {
            filters.add(Filters.eq("metadata.host", host));
        }
        
        if (metric != null) {
            filters.add(Filters.eq("metadata.metric", metric));
        }
        
        Document latest = metricsCollection.find(Filters.and(filters))
                .sort(Sorts.descending("timestamp"))
                .first();
        
        if (latest != null) {
            return latest.getDate("timestamp").toInstant();
        }
        
        // Default to epoch if no data exists
        return Instant.EPOCH;
    }
    
    /**
     * Close the MongoDB client connection
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("Closed MongoDB connection");
        }
    }
}