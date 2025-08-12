package com.mongodb.atlas.api.metrics;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

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
 * Handles storing Atlas metrics in a MongoDB timeseries collection Enhanced
 * with better timestamp tracking for optimized metric collection
 */
public class MetricsStorage {

	private static final Logger logger = LoggerFactory.getLogger(MetricsStorage.class);

	private final MongoClient mongoClient;
	private final MongoDatabase database;
	private final String collectionName;
	private MongoCollection<Document> metricsCollection;
	private MongoCollection<Document> timestampTrackerCollection;
	private final boolean interactive;

	// In-memory tracker of the last timestamp for each host+metric combination
	private final Map<String, Instant> lastTimestampTracker = new HashMap<>();

	/**
	 * Creates a new MetricsStorage with the specified MongoDB connection string
	 * 
	 * @param connectionString MongoDB connection string
	 * @param databaseName     Database name to use
	 * @param collectionName   Collection name to use
	 */
	public MetricsStorage(String connectionString, String databaseName, String collectionName) {
		this(connectionString, databaseName, collectionName, false);
	}

	/**
	 * Creates a new MetricsStorage with the specified MongoDB connection string
	 * 
	 * @param connectionString MongoDB connection string
	 * @param databaseName     Database name to use
	 * @param collectionName   Collection name to use
	 * @param interactive      Enable interactive mode for long operations
	 */
	public MetricsStorage(String connectionString, String databaseName, String collectionName, boolean interactive) {
		this.interactive = interactive;
		
		logger.debug("Connecting to MongoDB at {}...", connectionString);
		long startTime = System.currentTimeMillis();
		this.mongoClient = MongoClients.create(connectionString);
		long connectionTime = System.currentTimeMillis() - startTime;
		logger.info("MongoDB connection established in {}ms", connectionTime);
		
		logger.debug("Accessing database '{}'...", databaseName);
		startTime = System.currentTimeMillis();
		this.database = mongoClient.getDatabase(databaseName);
		this.collectionName = collectionName;
		long dbAccessTime = System.currentTimeMillis() - startTime;
		logger.info("Database access completed in {}ms", dbAccessTime);

		// Initialize the collections if they don't exist
		logger.debug("Initializing metrics collection '{}'...", collectionName);
		startTime = System.currentTimeMillis();
		initializeCollection();
		long metricsCollectionTime = System.currentTimeMillis() - startTime;
		logger.info("Metrics collection initialization completed in {}ms", metricsCollectionTime);
		
		logger.debug("Initializing timestamp tracker collection...");
		startTime = System.currentTimeMillis();
		initializeTimestampTrackerCollection();
		long trackerCollectionTime = System.currentTimeMillis() - startTime;
		logger.info("Timestamp tracker collection initialization completed in {}ms", trackerCollectionTime);

		// Build the tracker of last timestamps
		logger.debug("Loading timestamp tracker...");
		startTime = System.currentTimeMillis();
		loadLastTimestampTracker();
		long trackerLoadTime = System.currentTimeMillis() - startTime;
		logger.info("Timestamp tracker loading completed in {}ms", trackerLoadTime);

		logger.info("Initialized metrics storage with database: {}, collection: {}", databaseName, collectionName);
	}

	/**
	 * Initialize the timeseries collection if it doesn't exist
	 */
	private void initializeCollection() {
		logger.info("Checking if collection '{}' exists...", collectionName);
		long startTime = System.currentTimeMillis();
		boolean collectionExists = database.listCollectionNames().into(new ArrayList<>()).contains(collectionName);
		long checkTime = System.currentTimeMillis() - startTime;
		logger.info("Collection existence check completed in {}ms (exists: {})", checkTime, collectionExists);

		if (!collectionExists) {
			logger.info("Creating timeseries collection: {}", collectionName);
			startTime = System.currentTimeMillis();

			// Configure the timeseries collection
			TimeSeriesOptions timeSeriesOptions = new TimeSeriesOptions("timestamp").metaField("metadata");
			CreateCollectionOptions options = new CreateCollectionOptions().timeSeriesOptions(timeSeriesOptions);

			// Create the collection
			database.createCollection(collectionName, options);
			long createTime = System.currentTimeMillis() - startTime;
			logger.info("Collection created in {}ms", createTime);

			// Create indexes for faster queries
			logger.info("Creating indexes for collection '{}'...", collectionName);
			startTime = System.currentTimeMillis();
			metricsCollection = database.getCollection(collectionName);
			metricsCollection.createIndex(Indexes.ascending("metadata.host", "metadata.metric"));
			metricsCollection.createIndex(Indexes.ascending("metadata.projectName"));
			metricsCollection.createIndex(Indexes.ascending("metadata.partition"));
			long indexTime = System.currentTimeMillis() - startTime;
			logger.info("Indexes created in {}ms", indexTime);
		} else {
			logger.info("Using existing collection: {}", collectionName);
			metricsCollection = database.getCollection(collectionName);
		}
	}

	/**
	 * Initialize the timestamp tracker collection
	 */
	private void initializeTimestampTrackerCollection() {
		String trackerCollectionName = collectionName + "_tracker";
		boolean collectionExists = database.listCollectionNames().into(new ArrayList<>()).contains(trackerCollectionName);

		if (!collectionExists) {
			logger.info("Creating timestamp tracker collection: {}", trackerCollectionName);
			database.createCollection(trackerCollectionName);
		}

		timestampTrackerCollection = database.getCollection(trackerCollectionName);
		
		// Create indexes for faster queries
		timestampTrackerCollection.createIndex(Indexes.ascending("host", "metric"));
		timestampTrackerCollection.createIndex(Indexes.ascending("lastTimestamp"));
	}

	/**
	 * Load timestamp tracker from the database
	 */
	private void loadLastTimestampTracker() {
		logger.info("Loading timestamp tracker from database...");
		
		// First, load all existing tracker entries
		logger.info("Querying timestamp tracker collection...");
		long startTime = System.currentTimeMillis();
		List<Document> trackerDocs = timestampTrackerCollection.find().into(new ArrayList<>());
		long queryTime = System.currentTimeMillis() - startTime;
		logger.info("Tracker query completed in {}ms, found {} documents", queryTime, trackerDocs.size());
		
		int loadedEntries = 0;
		startTime = System.currentTimeMillis();
		
		for (Document doc : trackerDocs) {
			String key = doc.getString("_id");
			Date timestampDate = doc.getDate("lastTimestamp");
			if (key != null && timestampDate != null) {
				lastTimestampTracker.put(key, timestampDate.toInstant());
				loadedEntries++;
			}
		}
		
		long loadTime = System.currentTimeMillis() - startTime;
		logger.info("Loaded {} timestamp tracker entries from database in {}ms", loadedEntries, loadTime);
		
		// Check if we need to scan the metrics collection for missing entries
		if (loadedEntries == 0) {
			logger.info("No tracker entries found. Scanning metrics collection to build initial tracker...");
			buildInitialTimestampTracker();
		} else {
			// Optionally check for new hosts/metrics not in tracker
			logger.info("Checking for new host/metric combinations...");
			startTime = System.currentTimeMillis();
			checkForNewHostMetrics();
			long checkTime = System.currentTimeMillis() - startTime;
			logger.info("New host/metric check completed in {}ms", checkTime);
		}
	}

	/**
	 * Check for new host/metric combinations not in the tracker (optimized version)
	 */
	private void checkForNewHostMetrics() {
		logger.info("Checking for new host/metric combinations...");
		
		// Use aggregation to efficiently find host/metric combinations not in tracker
		// This is much faster than the previous approach that scanned all hosts
		logger.info("Running aggregation to find missing tracker entries...");
		long startTime = System.currentTimeMillis();
		
		try {
			// Get distinct host:metric combinations from metrics collection using aggregation
			List<String> existingKeys = new ArrayList<>(lastTimestampTracker.keySet());
			
			// Use a more efficient approach: aggregate unique host+metric combinations
			List<Document> pipeline = List.of(
				new Document("$group", new Document("_id", 
					new Document("host", "$metadata.host")
						.append("metric", "$metadata.metric"))
					.append("lastTimestamp", new Document("$max", "$timestamp"))),
				new Document("$project", new Document("key", 
					new Document("$concat", List.of("$_id.host", ":", "$_id.metric")))
					.append("host", "$_id.host")
					.append("metric", "$_id.metric") 
					.append("lastTimestamp", "$lastTimestamp"))
			);
			
			List<Document> results = metricsCollection.aggregate(pipeline).into(new ArrayList<>());
			long aggregationTime = System.currentTimeMillis() - startTime;
			logger.info("Aggregation completed in {}ms, found {} unique combinations", aggregationTime, results.size());
			
			int newCombinations = 0;
			for (Document result : results) {
				String key = result.getString("key");
				if (!lastTimestampTracker.containsKey(key)) {
					String host = result.getString("host");
					String metric = result.getString("metric");
					Date timestampDate = result.getDate("lastTimestamp");
					
					if (timestampDate != null) {
						Instant timestamp = timestampDate.toInstant();
						lastTimestampTracker.put(key, timestamp);
						
						// Save to database
						Document trackerDoc = new Document("_id", key)
								.append("host", host)
								.append("metric", metric)
								.append("lastTimestamp", timestampDate);
						timestampTrackerCollection.replaceOne(
								Filters.eq("_id", key),
								trackerDoc,
								new com.mongodb.client.model.ReplaceOptions().upsert(true)
						);
						
						newCombinations++;
					}
				}
			}
			
			if (newCombinations > 0) {
				logger.info("Found and added {} new host/metric combinations to tracker", newCombinations);
			} else {
				logger.info("No new host/metric combinations found");
			}
			
		} catch (Exception e) {
			logger.error("Error checking for new host/metric combinations: {}", e.getMessage());
			// Fall back to simpler approach if aggregation fails
			logger.info("Falling back to simple approach...");
			checkForNewHostMetricsSimple();
		}
	}
	
	/**
	 * Simple fallback method for checking new host/metric combinations
	 */
	private void checkForNewHostMetricsSimple() {
		// Only check if we have a reasonable number of existing entries
		if (lastTimestampTracker.size() > 1000) {
			logger.info("Large dataset detected ({}+ entries), skipping new combination check for performance", 
					lastTimestampTracker.size());
			return;
		}
		
		List<String> distinctHosts = metricsCollection.distinct("metadata.host", String.class).into(new ArrayList<>());
		
		int newCombinations = 0;
		for (String host : distinctHosts) {
			List<String> metrics = metricsCollection
					.distinct("metadata.metric", Filters.eq("metadata.host", host), String.class)
					.into(new ArrayList<>());
			
			for (String metric : metrics) {
				String key = host + ":" + metric;
				if (!lastTimestampTracker.containsKey(key)) {
					Document latestDoc = metricsCollection
							.find(Filters.and(Filters.eq("metadata.host", host), Filters.eq("metadata.metric", metric)))
							.sort(Sorts.descending("timestamp")).first();
					
					if (latestDoc != null) {
						Instant timestamp = latestDoc.getDate("timestamp").toInstant();
						lastTimestampTracker.put(key, timestamp);
						
						Document trackerDoc = new Document("_id", key)
								.append("host", host)
								.append("metric", metric)
								.append("lastTimestamp", Date.from(timestamp));
						timestampTrackerCollection.replaceOne(
								Filters.eq("_id", key),
								trackerDoc,
								new com.mongodb.client.model.ReplaceOptions().upsert(true)
						);
						
						newCombinations++;
					}
				}
			}
		}
		
		logger.info("Simple check found {} new combinations", newCombinations);
	}

	/**
	 * Build initial timestamp tracker by scanning the metrics collection
	 * This is similar to the old buildLastTimestampCache but saves to database
	 */
	private void buildInitialTimestampTracker() {
		long startTime = System.currentTimeMillis();
		List<String> distinctHosts = metricsCollection.distinct("metadata.host", String.class).into(new ArrayList<>());
		long queryTime = System.currentTimeMillis() - startTime;
		logger.info("Found {} distinct hosts (query took {}ms)", distinctHosts.size(), queryTime);

		if (distinctHosts.isEmpty()) {
			logger.info("No hosts found in collection - cache will be empty");
			return;
		}

		// Calculate total work for progress tracking
		int totalMetrics = 0;
		for (String host : distinctHosts) {
			List<String> metrics = metricsCollection
					.distinct("metadata.metric", Filters.eq("metadata.host", host), String.class)
					.into(new ArrayList<>());
			totalMetrics += metrics.size();
		}
		
		logger.info("Total work: {} metrics across {} hosts", totalMetrics, distinctHosts.size());

		// Check if interactive mode should be used for large datasets
		if (interactive && totalMetrics > 100) {
			logger.info("🔄 Large dataset detected ({} metrics). This operation may take several minutes.", totalMetrics);
			handleInteractiveTrackerBuilding(distinctHosts, totalMetrics);
		} else {
			buildTrackerNonInteractive(distinctHosts, totalMetrics);
		}
	}

	private void handleInteractiveTrackerBuilding(List<String> distinctHosts, int totalMetrics) {
		Scanner scanner = new Scanner(System.in);
		
		System.out.println("\n⚠️  Timestamp Tracker Initialization Required");
		System.out.println("==========================================");
		System.out.printf("Found %d metrics across %d hosts that need timestamp tracking.\n", totalMetrics, distinctHosts.size());
		System.out.println("This one-time process builds a database of latest timestamps to prevent duplicate data.");
		System.out.printf("Estimated time: %d-%d minutes\n", totalMetrics / 60, totalMetrics / 30);
		System.out.println("\nOptions:");
		System.out.println("  1. Build tracker with progress updates (recommended)");
		System.out.println("  2. Build tracker silently in background");
		System.out.println("  3. Skip tracker building (slower duplicate detection)");
		System.out.println("  4. Cancel operation");
		System.out.print("\nSelect option [1-4]: ");
		System.out.flush();
		
		String choice;
		try {
			choice = scanner.nextLine().trim();
		} catch (Exception e) {
			logger.warn("Failed to read user input, defaulting to option 2 (background build)");
			choice = "2";
		}
		
		switch (choice) {
			case "1":
				buildTrackerWithProgress(distinctHosts, totalMetrics, scanner);
				break;
			case "2":
				logger.info("Building timestamp tracker in background...");
				buildTrackerNonInteractive(distinctHosts, totalMetrics);
				break;
			case "3":
				logger.warn("⚠️  Skipping timestamp tracker building. Performance may be slower for duplicate detection.");
				System.out.println("✅ Tracker building skipped. Continuing with operation...");
				break;
			case "4":
			default:
				logger.info("Operation cancelled by user");
				System.out.println("❌ Operation cancelled.");
				throw new RuntimeException("Operation cancelled by user");
		}
	}

	private void buildTrackerWithProgress(List<String> distinctHosts, int totalMetrics, Scanner scanner) {
		System.out.println("\n🔄 Building timestamp tracker with progress updates...");
		System.out.println("Press 'q' + Enter at any time to cancel");
		
		int cacheEntries = 0;
		int processedHosts = 0;
		int processedMetrics = 0;
		long overallStartTime = System.currentTimeMillis();

		for (String host : distinctHosts) {
			processedHosts++;
			long hostStartTime = System.currentTimeMillis();
			
			// Progress indicator
			int progressPercent = (processedMetrics * 100) / totalMetrics;
			System.out.printf("\r[%s%s] %d%% - Host %d/%d: %s", 
					"=".repeat(progressPercent / 5), 
					" ".repeat(20 - progressPercent / 5),
					progressPercent, processedHosts, distinctHosts.size(), host);

			List<String> metrics = metricsCollection
					.distinct("metadata.metric", Filters.eq("metadata.host", host), String.class)
					.into(new ArrayList<>());

			for (String metric : metrics) {
				processedMetrics++;
				
				// Check for user cancellation (non-blocking)
				try {
					if (System.in.available() > 0) {
						String input = scanner.nextLine().trim();
						if ("q".equalsIgnoreCase(input)) {
							System.out.println("\n❌ Timestamp tracker building cancelled by user.");
							logger.warn("Timestamp tracker building cancelled - partial tracker with {} entries", cacheEntries);
							return;
						}
					}
				} catch (IOException e) {
					// Ignore - just continue without checking for input
				}

				// For each host+metric, find the document with the latest timestamp
				Document latestDoc = metricsCollection
						.find(Filters.and(Filters.eq("metadata.host", host), Filters.eq("metadata.metric", metric)))
						.sort(Sorts.descending("timestamp")).first();

				if (latestDoc != null) {
					Instant timestamp = latestDoc.getDate("timestamp").toInstant();
					String key = host + ":" + metric;
					lastTimestampTracker.put(key, timestamp);
					
					// Save to database
					Document trackerDoc = new Document("_id", key)
							.append("host", host)
							.append("metric", metric)
							.append("lastTimestamp", Date.from(timestamp));
					timestampTrackerCollection.replaceOne(
							Filters.eq("_id", key),
							trackerDoc,
							new com.mongodb.client.model.ReplaceOptions().upsert(true)
					);
					
					cacheEntries++;
				}
			}
			
			long hostTime = System.currentTimeMillis() - hostStartTime;
			long elapsedTotal = System.currentTimeMillis() - overallStartTime;
			long estimatedTotal = (elapsedTotal * totalMetrics) / Math.max(processedMetrics, 1);
			long remaining = estimatedTotal - elapsedTotal;
			
			System.out.printf("\nHost %s complete (%dms) - ETA: %d seconds\n", 
					host, hostTime, remaining / 1000);
		}

		System.out.printf("\n✅ Timestamp tracker building complete! Created %d entries in %d seconds.\n", 
				cacheEntries, (System.currentTimeMillis() - overallStartTime) / 1000);
		logger.info("Completed timestamp tracker with {} entries from {} hosts and {} total metrics", 
				cacheEntries, distinctHosts.size(), processedMetrics);
	}

	private void buildTrackerNonInteractive(List<String> distinctHosts, int totalMetrics) {
		int cacheEntries = 0;
		int processedHosts = 0;
		int processedMetrics = 0;

		for (String host : distinctHosts) {
			processedHosts++;
			long hostStartTime = System.currentTimeMillis();
			logger.info("Processing host {} ({}/{})", host, processedHosts, distinctHosts.size());

			List<String> metrics = metricsCollection
					.distinct("metadata.metric", Filters.eq("metadata.host", host), String.class)
					.into(new ArrayList<>());

			for (String metric : metrics) {
				processedMetrics++;

				Document latestDoc = metricsCollection
						.find(Filters.and(Filters.eq("metadata.host", host), Filters.eq("metadata.metric", metric)))
						.sort(Sorts.descending("timestamp")).first();

				if (latestDoc != null) {
					Instant timestamp = latestDoc.getDate("timestamp").toInstant();
					String key = host + ":" + metric;
					lastTimestampTracker.put(key, timestamp);
					
					// Save to database
					Document trackerDoc = new Document("_id", key)
							.append("host", host)
							.append("metric", metric)
							.append("lastTimestamp", Date.from(timestamp));
					timestampTrackerCollection.replaceOne(
							Filters.eq("_id", key),
							trackerDoc,
							new com.mongodb.client.model.ReplaceOptions().upsert(true)
					);
					
					cacheEntries++;
				}

				// Log progress periodically
				if (processedMetrics % 100 == 0) {
					logger.info("Progress: processed {} total metrics, {} cache entries created", processedMetrics, cacheEntries);
				}
			}
			
			long hostTime = System.currentTimeMillis() - hostStartTime;
			logger.info("Completed processing host {} - took {}ms, total cache entries: {}", host, hostTime, cacheEntries);
		}

		logger.info("Completed timestamp tracker with {} entries from {} hosts and {} total metrics", 
				cacheEntries, distinctHosts.size(), processedMetrics);
	}

	/**
	 * Store metrics measurements in the timeseries collection
	 * 
	 * @param projectName  Atlas project name
	 * @param host         Hostname of the MongoDB instance
	 * @param port         Port of the MongoDB instance
	 * @param partition    Optional partition name for disk metrics (can be null)
	 * @param metric       Metric name
	 * @param measurements List of measurement data points
	 * @return Number of new documents inserted
	 */
	public int storeMetrics(String projectName, String host, int port, String partition, String metric,
			List<Map<String, Object>> dataPoints) {

		if (dataPoints == null || dataPoints.isEmpty()) {
			logger.debug("No data points to store for {}:{} metric {}", host, port, metric);
			return 0;
		}

		String hostPort = host + ":" + port;
		String cacheKey = hostPort + ":" + metric;
		if (partition != null) {
			cacheKey += ":" + partition;
		}

		Instant lastTimestamp = lastTimestampTracker.getOrDefault(cacheKey, Instant.EPOCH);
		logger.debug("Last timestamp for {}: {}", cacheKey, lastTimestamp);

		List<Document> documents = new ArrayList<>();
		int newPoints = 0;
		int skippedPoints = 0;
		Instant latestTimestampInBatch = lastTimestamp;

// Create a set to track unique timestamps within this batch
		Set<Instant> seenTimestamps = new HashSet<>();

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

// Additional checks to prevent duplicates
// 1. Ensure timestamp is strictly after the last known timestamp
// 2. Ensure we haven't seen this exact timestamp in this batch
			if (timestamp.isBefore(lastTimestamp) || timestamp.equals(lastTimestamp)
					|| seenTimestamps.contains(timestamp)) {
				skippedPoints++;
				continue;
			}

// Add to seen timestamps to prevent intra-batch duplicates
			seenTimestamps.add(timestamp);

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
				logger.debug("Null or invalid value for {}:{} metric {} at {}", host, port, metric, timestampStr);
				continue;
			}

// First, check if this exact data point already exists in the database
			long existingCount = metricsCollection
					.countDocuments(Filters.and(Filters.eq("timestamp", java.util.Date.from(timestamp)),
							Filters.eq("value", value), Filters.eq("metadata.projectName", projectName),
							Filters.eq("metadata.host", hostPort), Filters.eq("metadata.metric", metric),
							(partition != null ? Filters.eq("metadata.partition", partition)
									: Filters.or(Filters.exists("metadata.partition", false),
											Filters.eq("metadata.partition", null)))));

			if (existingCount > 0) {
			    // Log the details of the existing document
			    Document existingDoc = metricsCollection.find(
			        Filters.and(
			            Filters.eq("timestamp", java.util.Date.from(timestamp)),
			            Filters.eq("value", value),
			            Filters.eq("metadata.projectName", projectName),
			            Filters.eq("metadata.host", hostPort),
			            Filters.eq("metadata.metric", metric),
			            (partition != null ? 
			                Filters.eq("metadata.partition", partition) : 
			                Filters.or(Filters.exists("metadata.partition", false), Filters.eq("metadata.partition", null)))
			        )
			    ).first();
			    
			    logger.debug("Duplicate document found: {}", existingDoc);
			    skippedPoints++;
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

// Update the in-memory tracker and database with the latest timestamp
				lastTimestampTracker.put(cacheKey, latestTimestampInBatch);
				
				// Update the database tracker
				Document trackerDoc = new Document("_id", cacheKey)
						.append("host", hostPort)
						.append("metric", metric);
				if (partition != null) {
					trackerDoc.append("partition", partition);
				}
				trackerDoc.append("lastTimestamp", Date.from(latestTimestampInBatch));
				
				timestampTrackerCollection.replaceOne(
						Filters.eq("_id", cacheKey),
						trackerDoc,
						new com.mongodb.client.model.ReplaceOptions().upsert(true)
				);

				logger.debug("Stored {} new data points for {}:{} metric {} (skipped {} duplicates)", newPoints, host,
						port, metric, skippedPoints);
			} catch (Exception e) {
				logger.error("Failed to store metrics for {}:{} metric {}: {}", host, port, metric, e.getMessage());
			}
		} else {
			logger.debug("No new data points to store for {}:{} metric {} (all {} points were duplicates)", host, port,
					metric, skippedPoints);
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
		Document latest = metricsCollection.find(Filters.eq("metadata.metric", metric))
				.sort(Sorts.descending("timestamp")).first();

		if (latest != null) {
			return latest.getDate("timestamp").toInstant();
		}

		return Instant.EPOCH;
	}

	/**
	 * Get the latest timestamp for a specific project and metric
	 * 
	 * @param projectName The project name
	 * @param metric      The metric name
	 * @return The latest timestamp, or EPOCH if no data found
	 */
	public Instant getLatestTimestampForProjectMetric(String projectName, String metric) {
		Document latest = metricsCollection.find(
				Filters.and(Filters.eq("metadata.projectName", projectName), Filters.eq("metadata.metric", metric)))
				.sort(Sorts.descending("timestamp")).first();

		if (latest != null) {
			return latest.getDate("timestamp").toInstant();
		}

		return Instant.EPOCH;
	}

	/**
	 * Get the latest timestamp for a specific host and metric
	 * 
	 * @param host   The host identifier (hostname:port)
	 * @param metric The metric name
	 * @return The latest timestamp, or EPOCH if no data found
	 */
	public Instant getLatestTimestampForHostMetric(String host, String metric) {
		Document latest = metricsCollection
				.find(Filters.and(Filters.eq("metadata.host", host), Filters.eq("metadata.metric", metric)))
				.sort(Sorts.descending("timestamp")).first();

		if (latest != null) {
			return latest.getDate("timestamp").toInstant();
		}

		return Instant.EPOCH;
	}

	/**
	 * Get the latest timestamp for a specific host, partition and metric
	 * 
	 * @param host      The host identifier (hostname:port)
	 * @param partition The partition name
	 * @param metric    The metric name
	 * @return The latest timestamp, or EPOCH if no data found
	 */
	public Instant getLatestTimestampForHostPartitionMetric(String host, String partition, String metric) {
		Document latest = metricsCollection.find(Filters.and(Filters.eq("metadata.host", host),
				Filters.eq("metadata.partition", partition), Filters.eq("metadata.metric", metric)))
				.sort(Sorts.descending("timestamp")).first();

		if (latest != null) {
			return latest.getDate("timestamp").toInstant();
		}

		return Instant.EPOCH;
	}

	/**
	 * Get metrics from the timeseries collection
	 * 
	 * @param projectName Optional project name filter (can be null)
	 * @param host        Optional hostname filter (can be null)
	 * @param metric      Optional metric name filter (can be null)
	 * @param startTime   Start time for the query
	 * @param endTime     End time for the query (can be null for 'now')
	 * @return List of measurement documents
	 */
	public List<Document> getMetrics(String projectName, String host, String metric, Instant startTime,
			Instant endTime) {

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
		return metricsCollection.find(Filters.and(filters)).sort(Sorts.ascending("timestamp")).into(new ArrayList<>());
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

		Document earliest = metricsCollection.find(Filters.and(filters)).sort(Sorts.ascending("timestamp")).first();

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

		Document latest = metricsCollection.find(Filters.and(filters)).sort(Sorts.descending("timestamp")).first();

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