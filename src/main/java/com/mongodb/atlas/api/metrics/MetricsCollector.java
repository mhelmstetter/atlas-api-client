package com.mongodb.atlas.api.metrics;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.util.MetricsUtils;

/**
 * Collects metrics from MongoDB Atlas API and optionally stores them Optimized
 * to only fetch data not already collected
 */
public class MetricsCollector {

	private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

	private final AtlasApiClient apiClient;
	private final List<String> metrics;
	private final String period; // Kept for backward compatibility
	// private final int periodDays; // Period in days for explicit time range
	private final String granularity;
	private final MetricsStorage metricsStorage;
	private final boolean storeMetrics;
	private final boolean collectOnly;
	private Set<String> includedProjects; // Track the projects we're collecting

	// Caches for last timestamps
	private final Map<String, Instant> systemLastTimestamps = new HashMap<>();
	private final Map<String, Instant> diskLastTimestamps = new HashMap<>();

	// Tracking for collection statistics
	private int totalProcessesScanned = 0;
	private int totalDataPointsCollected = 0;
	private int totalDataPointsStored = 0;
	private final Map<String, Integer> projectDataPoints = new HashMap<>();

	/**
	 * Creates a collector that doesn't store metrics
	 */
	public MetricsCollector(AtlasApiClient apiClient, List<String> metrics, String period, String granularity) {
		this(apiClient, metrics, period, granularity, null, false, false);
	}

	/**
	 * Creates a collector with storage option
	 * 
	 * @param apiClient      The Atlas API client
	 * @param metrics        List of metrics to collect
	 * @param period         Time period to collect
	 * @param granularity    Data granularity
	 * @param metricsStorage Optional storage for collected metrics (can be null)
	 * @param storeMetrics   Whether to store metrics in the provided storage
	 * @param collectOnly    If true, only collect metrics without processing
	 */
	public MetricsCollector(AtlasApiClient apiClient, List<String> metrics, String period, String granularity,
			MetricsStorage metricsStorage, boolean storeMetrics, boolean collectOnly) {
		this.apiClient = apiClient;
		this.metrics = metrics;
		this.period = period;
		this.granularity = granularity;
		this.metricsStorage = metricsStorage;
		this.storeMetrics = storeMetrics && metricsStorage != null;
		this.collectOnly = collectOnly;

		logger.info("📊 Atlas Metrics Analyzer initialized");
		logger.info("🎯 Collecting {} metrics over {} with {} granularity", 
				metrics.size(), period, granularity);
		if (this.storeMetrics) {
			logger.info("💾 Storage enabled");
		}
		if (this.collectOnly) {
			logger.info("📥 Collection-only mode enabled");
		}

		// Initialize timestamp caches if storage is enabled
		if (this.storeMetrics) {
			initializeTimestampCaches();
		}
	}

	/**
	 * Initialize caches for the last timestamps from storage
	 */
	private void initializeTimestampCaches() {
		if (metricsStorage == null) {
			return;
		}

		logger.info("Initializing timestamp caches from storage...");

		// For system metrics (non-disk)
		for (String metric : metrics) {
			if (!metric.startsWith("DISK_")) {
				try {
					// First get the global latest timestamp for this metric type
					Instant latestTime = metricsStorage.getLatestTimestampForMetric(metric);
					if (latestTime != null && !latestTime.equals(Instant.EPOCH)) {
						systemLastTimestamps.put(metric, latestTime);
						logger.info("Last timestamp for system metric {}: {}", metric, latestTime);
					}

					// Next, get per-project timestamps for this metric if needed
					// This can be useful for more targeted optimizations
					if (includedProjects != null && !includedProjects.isEmpty()) {
						for (String projectName : includedProjects) {
							Instant projectLatestTime = metricsStorage.getLatestTimestampForProjectMetric(projectName,
									metric);
							if (projectLatestTime != null && !projectLatestTime.equals(Instant.EPOCH)) {
								String key = projectName + ":" + metric;
								systemLastTimestamps.put(key, projectLatestTime);
								logger.debug("Last timestamp for project {} metric {}: {}", projectName, metric,
										projectLatestTime);
							}
						}
					}
				} catch (Exception e) {
					logger.warn("Error initializing timestamp cache for metric {}: {}", metric, e.getMessage());
				}
			}
		}

		// For disk metrics
		for (String metric : metrics) {
			if (metric.startsWith("DISK_")) {
				try {
					Instant latestTime = metricsStorage.getLatestTimestampForMetric(metric);
					if (latestTime != null && !latestTime.equals(Instant.EPOCH)) {
						diskLastTimestamps.put(metric, latestTime);
						logger.info("Last timestamp for disk metric {}: {}", metric, latestTime);
					}
				} catch (Exception e) {
					logger.warn("Error initializing timestamp cache for disk metric {}: {}", metric, e.getMessage());
				}
			}
		}

		logger.info("Timestamp caches initialized with {} system metrics and {} disk metrics",
				systemLastTimestamps.size(), diskLastTimestamps.size());
	}

	/**
	 * Collect metrics for specified projects
	 * 
	 * @param includeProjectNames Project names to include
	 * @return Map of project names to their metric results (empty if
	 *         collectOnly=true)
	 */
	public Map<String, ProjectMetricsResult> collectMetrics(Set<String> includeProjectNames) {
		// Store included projects for later reference
		this.includedProjects = includeProjectNames;

		// Get projects matching the specified names
		Map<String, String> projectMap = apiClient.clusters().getProjects(includeProjectNames);

		logger.info("🚀 Starting metrics collection for {} projects", projectMap.size());

		// Reset collection statistics
		resetCollectionStats();

		// Create results map (will remain empty if collectOnly=true)
		Map<String, ProjectMetricsResult> results = new HashMap<>();

		// If not in collect-only mode, initialize result objects
		if (!collectOnly) {
			for (String projectName : projectMap.keySet()) {
				String projectId = projectMap.get(projectName);
				ProjectMetricsResult projectResult = new ProjectMetricsResult(projectName, projectId);

				// Initialize metrics
				metrics.forEach(projectResult::initializeMetric);

				results.put(projectName, projectResult);
			}
		}

		// Process each project
		for (String projectName : projectMap.keySet()) {
			String projectId = projectMap.get(projectName);

			try {
				logger.info("📁 Processing project: {}", projectName);

				// Collect metrics for this project
				ProjectCollectionResult collectionResult = collectProjectMetrics(projectName, projectId);

				// Update collection statistics
				totalProcessesScanned += collectionResult.getProcessCount();
				totalDataPointsCollected += collectionResult.getDataPointsCollected();
				totalDataPointsStored += collectionResult.getDataPointsStored();
				projectDataPoints.put(projectName, collectionResult.getDataPointsCollected());

				// If not in collect-only mode, calculate final averages for the project
				if (!collectOnly && results.containsKey(projectName)) {
					results.get(projectName).calculateAverages();
				}

				// Log project summary
				if (collectionResult.getDataPointsCollected() > 0) {
					logger.info("✅ {} complete: {} data points collected", projectName, collectionResult.getDataPointsCollected());
				} else {
					logger.warn("⚠️  {} complete: No data points collected", projectName);
				}

			} catch (Exception e) {
				logger.error("Error collecting metrics for project {}: {}", projectName, e.getMessage(), e);
			}
		}

		// Log final collection statistics
		logCollectionStats();

		return results;
	}

	/**
	 * Collect metrics for a single project
	 * 
	 * @param projectName The project name
	 * @param projectId   The project ID
	 * @return Collection statistics for this project
	 */
	private ProjectCollectionResult collectProjectMetrics(String projectName, String projectId) {
		ProjectCollectionResult result = new ProjectCollectionResult(projectName, projectId);

		try {
			// Get all processes for this project
			List<Map<String, Object>> processes = apiClient.clusters().getProcesses(projectId);
			logger.debug("Collecting metrics for project: {} with {} processes", projectName, processes.size());

			// Filter out config servers and mongos instances
			List<Map<String, Object>> filteredProcesses = processes.stream().filter(process -> {
				String typeName = (String) process.get("typeName");
				return !typeName.startsWith("SHARD_CONFIG") && !typeName.equals("SHARD_MONGOS");
			}).collect(Collectors.toList());

			logger.debug("Filtered to {} mongod processes for project {}", filteredProcesses.size(), projectName);

			result.setProcessCount(filteredProcesses.size());

			// Process each MongoDB instance sequentially
			int processedCount = 0;
			logger.info("📊 Processing {} MongoDB instances for project {}", filteredProcesses.size(), projectName);

			for (Map<String, Object> process : filteredProcesses) {
				String hostname = (String) process.get("hostname");
				int port = (int) process.get("port");
				
				// Log progress periodically (every 25% or every 5 instances, whichever is smaller)
				int progressInterval = Math.max(1, Math.min(5, filteredProcesses.size() / 4));
				if (processedCount % progressInterval == 0 || processedCount == filteredProcesses.size() - 1) {
					logger.info("   🔄 Processing instance {}/{}: {}:{}", 
						processedCount + 1, filteredProcesses.size(), hostname, port);
				} else {
					logger.debug("   🔄 Processing instance {}/{}: {}:{}", 
						processedCount + 1, filteredProcesses.size(), hostname, port);
				}

				try {
					// Collect system metrics
					logger.debug("      Collecting system metrics for {}:{}...", hostname, port);
					int systemPoints = collectSystemMetrics(projectName, projectId, hostname, port, result);
					result.addDataPointsCollected(systemPoints);
					logger.debug("      ✓ Collected {} system metric data points", systemPoints);

					// Collect disk metrics if requested
					if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
						logger.debug("      Collecting disk metrics for {}:{}...", hostname, port);
						int diskPoints = collectDiskMetrics(projectName, projectId, hostname, port, result);
						result.addDataPointsCollected(diskPoints);
						logger.debug("      ✓ Collected {} disk metric data points", diskPoints);
					}

					// Update progress periodically
					processedCount++;
					if (processedCount % progressInterval == 0 || processedCount == filteredProcesses.size()) {
						logger.info("   ✅ Completed {}/{} instances. Total data points: {}", 
							processedCount, filteredProcesses.size(), result.getDataPointsCollected());
					}
				} catch (Exception e) {
					// Log error but continue with other processes
					logger.error("Error collecting metrics for process {}:{} in project {}: {}", hostname, port,
							projectName, e.getMessage());
				}
			}

		} catch (Exception e) {
			logger.error("Error collecting metrics for project {}: {}", projectName, e.getMessage());
		}

		return result;
	}

	/**
	 * Collect system-level metrics (CPU, memory) using time-range based approach
	 * This optimizes the time range to only fetch new data
	 */
	private int collectSystemMetrics(String projectName, String projectId, String hostname, int port,
			ProjectCollectionResult result) {

		// Only include non-disk metrics
		List<String> systemMetrics = metrics.stream().filter(m -> !m.startsWith("DISK_")).collect(Collectors.toList());

		if (systemMetrics.isEmpty()) {
			return 0; // Skip if no system metrics requested
		}

		int dataPointsCollected = 0;
		String processId = hostname + ":" + port;

		try {
			// Determine optimal time range for this process based on stored data
			Instant startTime = null;
			Instant endTime = Instant.now();

			// If we're storing metrics, find the latest timestamp we have for this host
			// to avoid fetching duplicate data
			if (storeMetrics && metricsStorage != null) {
				// Get the most recent timestamp we have for ANY metric on this host
				Instant hostLastTimestamp = null;

				for (String metric : systemMetrics) {
					// Get last timestamp for this specific host/metric combination
					Instant metricLastTimestamp = metricsStorage.getLatestTimestampForHostMetric(processId, metric);

					if (metricLastTimestamp != null && !metricLastTimestamp.equals(Instant.EPOCH)) {
						if (hostLastTimestamp == null || metricLastTimestamp.isAfter(hostLastTimestamp)) {
							hostLastTimestamp = metricLastTimestamp;
						}
					}
				}

				if (hostLastTimestamp != null && !hostLastTimestamp.equals(Instant.EPOCH)) {
					// Start from 5 minutes before the latest timestamp to ensure no gaps
					// This small overlap helps handle clock skew and ensures data continuity
					startTime = hostLastTimestamp.minus(5, ChronoUnit.MINUTES);
					logger.debug("         🕒 Using optimized time range: {} to {} (5min overlap from last known: {})", 
							startTime, endTime, hostLastTimestamp);
				} else {
					// No previous data, use the full period
					startTime = endTime.minus(Duration.parse(period));
					logger.info("         🕒 No previous data found, using full period: {} to {}", startTime, endTime);
				}
			} else {
				// Not storing metrics, use the full period
				startTime = endTime.minus(Duration.parse(period));
				logger.info("         🕒 Using full period: {} to {}", startTime, endTime);
			}

			// Skip if the time range is invalid (start time is in the future)
			if (startTime.isAfter(endTime)) {
				logger.info("         ✅ All data is up to date for this host (start time {} is after end time {})", startTime, endTime);
				return 0;
			}

			// Fetch measurements from Atlas API using optimized time range
			logger.debug("         📡 Fetching {} system metrics from Atlas API...", systemMetrics.size());

			List<Map<String, Object>> measurements = apiClient.monitoring().getProcessMeasurementsWithExplicitTimeRange(
					projectId, hostname, port, systemMetrics, granularity, startTime, endTime);

			if (measurements == null || measurements.isEmpty()) {
				logger.info("         ⚠️  No measurements data found for this instance");
				return 0;
			}
			
			logger.debug("         📊 Received {} metric measurements from API", measurements.size());

			// Process each measurement
			for (Map<String, Object> measurement : measurements) {
				String metric = (String) measurement.get("name");
				List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");

				if (dataPoints == null || dataPoints.isEmpty()) {
					logger.debug("         ⚠️  Metric {} has no data points", metric);
					continue;
				}
				
				logger.debug("         📈 Processing metric {}: {} data points", metric, dataPoints.size());

				// Count the data points
				dataPointsCollected += dataPoints.size();

				// Store the metrics if storage is enabled
				if (storeMetrics && metricsStorage != null) {
					logger.debug("         💾 Storing {} data points for {}...", dataPoints.size(), metric);
					
					// Validate data quality before storing
					validateDataPoints(dataPoints, metric, hostname, port);
					
					int stored = metricsStorage.storeMetrics(projectName, hostname, port, null, metric, dataPoints);
					result.addDataPointsStored(stored);
					
					// Enhanced logging with efficiency metrics
					if (dataPoints.size() > 0) {
						double efficiency = (double) stored / dataPoints.size() * 100;
						logger.debug("         ✓ Stored {} new data points, skipped {} duplicates ({}% efficiency)", 
								stored, dataPoints.size() - stored, String.format("%.1f", efficiency));
					}
				}

				// If not in collect-only mode, process the measurements for the result
				if (!collectOnly) {
					processMetricData(metric, dataPoints, hostname + ":" + port, projectName, projectId);
				}
			}
		} catch (Exception e) {
			logger.error("Error collecting system measurements for {}:{}: {}", hostname, port, e.getMessage());
		}

		return dataPointsCollected;
	}

	/**
	 * Collect disk-level metrics using time-range based approach This optimizes the
	 * time range to only fetch new data
	 */
	private int collectDiskMetrics(String projectName, String projectId, String hostname, int port,
			ProjectCollectionResult result) {

		// Filter for disk metrics only
		List<String> diskMetrics = metrics.stream().filter(m -> m.startsWith("DISK_")).collect(Collectors.toList());

		if (diskMetrics.isEmpty()) {
			return 0; // Skip if no disk metrics requested
		}

		int dataPointsCollected = 0;
		String processId = hostname + ":" + port;

		try {
			// Get all disk partitions
			logger.info("         🔍 Discovering disk partitions...");
			List<Map<String, Object>> disks = apiClient.monitoring().getProcessDisks(projectId, hostname, port);

			if (disks.isEmpty()) {
				logger.info("         ⚠️  No disk partitions found for this instance");
				return 0;
			}
			
			logger.info("         💽 Found {} disk partitions", disks.size());

			// Process each partition
			for (Map<String, Object> disk : disks) {
				String partitionName = (String) disk.get("partitionName");

				try {
					// Determine optimal time range for this partition based on stored data
					Instant startTime = null;
					Instant endTime = Instant.now();

					// If we're storing metrics, find the latest timestamp we have for this partition
					if (storeMetrics && metricsStorage != null) {
						// Get the most recent timestamp we have for ANY metric on this partition
						Instant partitionLastTimestamp = null;

						for (String metric : diskMetrics) {
							// Get last timestamp for this specific host/partition/metric combination
							Instant metricLastTimestamp = metricsStorage.getLatestTimestampForHostPartitionMetric(
									processId, partitionName, metric);

							if (metricLastTimestamp != null && !metricLastTimestamp.equals(Instant.EPOCH)) {
								if (partitionLastTimestamp == null || metricLastTimestamp.isAfter(partitionLastTimestamp)) {
									partitionLastTimestamp = metricLastTimestamp;
								}
							}
						}

						if (partitionLastTimestamp != null && !partitionLastTimestamp.equals(Instant.EPOCH)) {
							// Start from 5 minutes before the latest timestamp to ensure no gaps  
							startTime = partitionLastTimestamp.minus(5, ChronoUnit.MINUTES);
							logger.info("         🕒 Using optimized time range for partition '{}': {} to {} (5min overlap from last known: {})", 
									partitionName, startTime, endTime, partitionLastTimestamp);
						} else {
							// No previous data, use the full period
							startTime = endTime.minus(Duration.parse(period));
							logger.info("         🕒 No previous data found for partition '{}', using full period: {} to {}", 
									partitionName, startTime, endTime);
						}
					} else {
						// Not storing metrics, use the full period
						startTime = endTime.minus(Duration.parse(period));
						logger.info("         🕒 Using full period for partition '{}': {} to {}", partitionName, startTime, endTime);
					}

					// Skip if the time range is invalid (start time is in the future)
					if (startTime.isAfter(endTime)) {
						logger.info("         ✅ All data is up to date for partition '{}' (start time {} is after end time {})", 
								partitionName, startTime, endTime);
						continue;
					}

					// Get measurements for this disk partition using optimized time range
					logger.info("         📡 Fetching disk metrics for partition '{}'...", partitionName);

					List<Map<String, Object>> measurements = apiClient.monitoring().getDiskMeasurementsWithExplicitTimeRange(
							projectId, hostname, port, partitionName, diskMetrics, granularity, startTime, endTime);

					if (measurements == null || measurements.isEmpty()) {
						logger.info("         ⚠️  No disk measurements found for partition '{}'", partitionName);
						continue;
					}

					logger.info("         📊 Received {} disk measurements for partition '{}'", measurements.size(), partitionName);

					// Process each measurement
					for (Map<String, Object> measurement : measurements) {
						String metric = (String) measurement.get("name");
						List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement
								.get("dataPoints");

						if (dataPoints == null || dataPoints.isEmpty()) {
							logger.debug("         ⚠️  Metric {} has no data points for partition '{}'", metric, partitionName);
							continue;
						}

						logger.info("         📈 Processing disk metric {}: {} data points", metric, dataPoints.size());

						// Count the data points
						dataPointsCollected += dataPoints.size();

						// Store the metrics if storage is enabled
						if (storeMetrics && metricsStorage != null) {
							logger.info("         💾 Storing {} data points for {} (partition {})...", dataPoints.size(), metric, partitionName);
							int stored = metricsStorage.storeMetrics(projectName, hostname, port, partitionName, metric,
									dataPoints);
							result.addDataPointsStored(stored);
							logger.info("         ✓ Stored {} new data points (skipped {} duplicates)", stored, dataPoints.size() - stored);
						}

						// If not in collect-only mode, process the measurements for the result
						if (!collectOnly) {
							String location = hostname + ":" + port + ", partition: " + partitionName;
							processMetricData(metric, dataPoints, location, projectName, projectId);
						}
					}
				} catch (Exception e) {
					logger.error("Error collecting disk measurements for {}:{} partition {}: {}", hostname, port,
							partitionName, e.getMessage());
				}
			}
		} catch (Exception e) {
			logger.error("Failed to get disk partitions for process {}:{}: {}", hostname, port, e.getMessage());
		}

		return dataPointsCollected;
	}

	/**
	 * Process metric data and add to project results if not in collect-only mode
	 */
	private void processMetricData(String metric, List<Map<String, Object>> dataPoints, String location,
			String projectName, String projectId) {
		if (collectOnly) {
			return; // Skip processing in collect-only mode
		}

		// Extract values from data points
		List<Double> values = MetricsUtils.extractDataPointValues(dataPoints);

		if (!values.isEmpty()) {
			// Calculate statistics from this batch of values
			ProcessingResult result = MetricsUtils.processValues(values);

			// TODO: Add to project result if needed
			// Since we don't have the original ProjectMetricsResult class,
			// this method is incomplete. In a real implementation, you'd
			// update the appropriate project result object.
		}
	}

	/**
	 * Validate data points for quality and detect potential gaps
	 */
	private void validateDataPoints(List<Map<String, Object>> dataPoints, String metric, String hostname, int port) {
		if (dataPoints == null || dataPoints.isEmpty()) {
			return;
		}
		
		try {
			// Sort data points by timestamp to detect gaps
			List<Instant> timestamps = dataPoints.stream()
				.map(dp -> Instant.parse((String) dp.get("timestamp")))
				.sorted()
				.collect(Collectors.toList());
			
			Instant first = timestamps.get(0);
			Instant last = timestamps.get(timestamps.size() - 1);
			
			// Log the time span being processed
			logger.debug("         📊 Data span for {}: {} to {} ({} points)", 
					metric, first, last, dataPoints.size());
			
			// Check for large gaps (more than 10 minutes between consecutive points for 1-minute granularity)
			if (granularity.equals("PT1M") && timestamps.size() > 1) {
				for (int i = 1; i < timestamps.size(); i++) {
					long gapMinutes = ChronoUnit.MINUTES.between(timestamps.get(i-1), timestamps.get(i));
					if (gapMinutes > 10) {
						logger.warn("         ⚠️  Large gap detected in {} data for {}:{}: {} minutes between {} and {}", 
								metric, hostname, port, gapMinutes, timestamps.get(i-1), timestamps.get(i));
					}
				}
			}
			
			// Check for data outside expected time range
			Instant now = Instant.now();
			if (last.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
				logger.warn("         ⚠️  Future timestamp detected in {} data for {}:{}: {}", 
						metric, hostname, port, last);
			}
			
		} catch (Exception e) {
			logger.debug("Error validating data points for {} {}:{}: {}", metric, hostname, port, e.getMessage());
		}
	}

	/**
	 * Reset collection statistics
	 */
	private void resetCollectionStats() {
		totalProcessesScanned = 0;
		totalDataPointsCollected = 0;
		totalDataPointsStored = 0;
		projectDataPoints.clear();
	}

	/**
	 * Log collection statistics
	 */
	private void logCollectionStats() {
		if (totalDataPointsCollected > 0) {
			logger.info("🎉 Collection complete: {} data points from {} processes", 
					totalDataPointsCollected, totalProcessesScanned);
			if (storeMetrics) {
				logger.info("💾 {} data points stored to MongoDB", totalDataPointsStored);
			}
		} else {
			logger.warn("⚠️  Collection complete: No data points collected from {} processes", totalProcessesScanned);
			logger.warn("💡 Try using --debug to investigate the issue");
		}
	}

	/**
	 * Class to track collection statistics for a project
	 */
	private static class ProjectCollectionResult {
		private final String projectName;
		private final String projectId;
		private int processCount;
		private int dataPointsCollected;
		private int dataPointsStored;

		public ProjectCollectionResult(String projectName, String projectId) {
			this.projectName = projectName;
			this.projectId = projectId;
			this.processCount = 0;
			this.dataPointsCollected = 0;
			this.dataPointsStored = 0;
		}

		public String getProjectName() {
			return projectName;
		}

		public String getProjectId() {
			return projectId;
		}

		public int getProcessCount() {
			return processCount;
		}

		public void setProcessCount(int processCount) {
			this.processCount = processCount;
		}

		public int getDataPointsCollected() {
			return dataPointsCollected;
		}

		public void addDataPointsCollected(int points) {
			this.dataPointsCollected += points;
		}

		public int getDataPointsStored() {
			return dataPointsStored;
		}

		public void addDataPointsStored(int points) {
			this.dataPointsStored += points;
		}
	}
}