package com.mongodb.atlas.api.metrics;

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

			for (Map<String, Object> process : filteredProcesses) {
				String hostname = (String) process.get("hostname");
				int port = (int) process.get("port");

				try {
					// Collect system metrics
					int systemPoints = collectSystemMetrics(projectName, projectId, hostname, port, result);
					result.addDataPointsCollected(systemPoints);

					// Collect disk metrics if requested
					if (metrics.stream().anyMatch(m -> m.startsWith("DISK_"))) {
						int diskPoints = collectDiskMetrics(projectName, projectId, hostname, port, result);
						result.addDataPointsCollected(diskPoints);
					}

					// Update progress
					processedCount++;
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

			// If we're storing metrics, find the latest timestamp we have for this host
			// to avoid fetching duplicate data
			if (storeMetrics && metricsStorage != null) {
				// First check if we have a global timestamp for this metric (non host-specific)
				Instant globalLastTimestamp = null;

				for (String metric : systemMetrics) {
					Instant metricLastTimestamp = systemLastTimestamps.getOrDefault(metric, Instant.EPOCH);
					if (!metricLastTimestamp.equals(Instant.EPOCH)) {
						if (globalLastTimestamp == null || metricLastTimestamp.isBefore(globalLastTimestamp)) {
							globalLastTimestamp = metricLastTimestamp;
						}
					}
				}

				// Next, get host-specific timestamps
				Instant hostLastTimestamp = null;

				for (String metric : systemMetrics) {
					// Get last timestamp for this specific host/metric combination
					Instant metricLastTimestamp = metricsStorage.getLatestDataTime(projectName, processId, metric);

					if (metricLastTimestamp != null && !metricLastTimestamp.equals(Instant.EPOCH)) {
						if (hostLastTimestamp == null || metricLastTimestamp.isBefore(hostLastTimestamp)) {
							hostLastTimestamp = metricLastTimestamp;
						}
					}
				}
			}

			// Fetch measurements from Atlas API
			logger.debug("Fetching system metrics for {}:{} with period of {}", hostname, port, period);

			List<Map<String, Object>> measurements = apiClient.monitoring().getProcessMeasurementsWithTimeRange(projectId, hostname, port, systemMetrics,
					granularity, period);

			if (measurements == null || measurements.isEmpty()) {
				logger.debug("{} {} -> No measurements data found", projectName, hostname + ":" + port);
				return 0;
			}
			
			logger.debug("{} {} -> Received {} measurements", projectName, hostname + ":" + port, measurements.size());

			// Process each measurement
			for (Map<String, Object> measurement : measurements) {
				String metric = (String) measurement.get("name");
				List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement.get("dataPoints");

				if (dataPoints == null || dataPoints.isEmpty()) {
					logger.debug("{} {} -> No data points found for metric {}", projectName, hostname + ":" + port,
							metric);
					continue;
				}
				
				logger.debug("{} {} -> Metric {}: {} data points", projectName, hostname + ":" + port, metric, dataPoints.size());

				// Count the data points
				dataPointsCollected += dataPoints.size();

				// Store the metrics if storage is enabled
				if (storeMetrics && metricsStorage != null) {
					int stored = metricsStorage.storeMetrics(projectName, hostname, port, null, metric, dataPoints);
					result.addDataPointsStored(stored);
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
			List<Map<String, Object>> disks = apiClient.monitoring().getProcessDisks(projectId, hostname, port);

			if (disks.isEmpty()) {
				logger.warn("No disk partitions found for process {}:{}", hostname, port);
				return 0;
			}

			// Process each partition
			for (Map<String, Object> disk : disks) {
				String partitionName = (String) disk.get("partitionName");

				try {

					// If we're storing metrics, find the latest timestamp we have for this
					// partition
					if (storeMetrics && metricsStorage != null) {
						// Find the oldest last timestamp across all metrics for this partition
						Instant oldestLastTimestamp = null;

						for (String metric : diskMetrics) {
							// Get last timestamp for this specific host/partition/metric combination
							Instant metricLastTimestamp = metricsStorage.getLatestDataTime(projectName, processId,
									metric);

							if (metricLastTimestamp != null && !metricLastTimestamp.equals(Instant.EPOCH)) {
								if (oldestLastTimestamp == null || metricLastTimestamp.isBefore(oldestLastTimestamp)) {
									oldestLastTimestamp = metricLastTimestamp;
								}
							}
						}

						// Only adjust period if we found a valid last timestamp
						if (oldestLastTimestamp != null) {
							// Add a small overlap to ensure we don't miss any data (10 minutes)
							Instant adjustedStartTime = oldestLastTimestamp.minus(10, ChronoUnit.MINUTES);

							// Calculate days between adjusted start time and now
							long daysToFetch = ChronoUnit.DAYS.between(adjustedStartTime, Instant.now());

							logger.info("Process {}:{} partition {} - Last disk data from {}", hostname, port,
									partitionName, oldestLastTimestamp);

						}
					}

					// Get measurements for this disk partition using time range
					logger.info("Fetching disk metrics for {}:{} partition {}", hostname, port, partitionName);

					List<Map<String, Object>> measurements = apiClient.monitoring().getDiskMeasurementsWithTimeRange(projectId,
							hostname, port, partitionName, diskMetrics, granularity, period);

					if (measurements == null || measurements.isEmpty()) {
						logger.warn("{} {}:{} partition {} -> No disk measurements found", projectName, hostname, port,
								partitionName);
						continue;
					}

					// Process each measurement
					for (Map<String, Object> measurement : measurements) {
						String metric = (String) measurement.get("name");
						List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) measurement
								.get("dataPoints");

						if (dataPoints == null || dataPoints.isEmpty()) {
							logger.warn("{} {}:{} partition {} -> No data points found for metric {}", projectName,
									hostname, port, partitionName, metric);
							continue;
						}

						// Count the data points
						dataPointsCollected += dataPoints.size();

						// Store the metrics if storage is enabled
						if (storeMetrics && metricsStorage != null) {
							int stored = metricsStorage.storeMetrics(projectName, hostname, port, partitionName, metric,
									dataPoints);
							result.addDataPointsStored(stored);
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