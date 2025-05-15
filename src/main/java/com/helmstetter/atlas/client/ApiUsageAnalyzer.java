package com.helmstetter.atlas.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for monitoring and analyzing Atlas API usage
 * Can be used for debugging and understanding rate limit issues
 */
public class ApiUsageAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiUsageAnalyzer.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private final AtlasApiClient apiClient;
    private final int windowSizeSeconds;
    private final List<ApiUsageSample> samples = new ArrayList<>();
    private Instant monitoringStartTime;
    
    /**
     * Data class to hold a single API usage sample
     */
    public static class ApiUsageSample {
        private final Instant timestamp;
        private final int totalRequests;
        private final long requestsInLastMinute;
        private final Map<String, Integer> projectRequestCounts;
        
        public ApiUsageSample(Instant timestamp, int totalRequests, long requestsInLastMinute, 
                             Map<String, Integer> projectRequestCounts) {
            this.timestamp = timestamp;
            this.totalRequests = totalRequests;
            this.requestsInLastMinute = requestsInLastMinute;
            this.projectRequestCounts = new HashMap<>(projectRequestCounts);
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public int getTotalRequests() {
            return totalRequests;
        }
        
        public long getRequestsInLastMinute() {
            return requestsInLastMinute;
        }
        
        public Map<String, Integer> getProjectRequestCounts() {
            return projectRequestCounts;
        }
        
        @Override
        public String toString() {
            return String.format("%s: Total=%d, LastMin=%d, Projects=%d",
                    timestamp.toString(), totalRequests, requestsInLastMinute, projectRequestCounts.size());
        }
    }
    
    /**
     * Create a new API usage analyzer with default sampling (every 10 seconds)
     */
    public ApiUsageAnalyzer(AtlasApiClient apiClient) {
        this(apiClient, 10);
    }
    
    /**
     * Create a new API usage analyzer with custom sampling interval
     * @param apiClient The API client to monitor
     * @param windowSizeSeconds Sampling interval in seconds
     */
    public ApiUsageAnalyzer(AtlasApiClient apiClient, int windowSizeSeconds) {
        this.apiClient = apiClient;
        this.windowSizeSeconds = windowSizeSeconds;
        this.monitoringStartTime = null;
    }
    
    /**
     * Start monitoring API usage
     * Will run in a separate thread to avoid blocking
     */
    public void startMonitoring() {
        if (monitoringStartTime != null) {
            logger.warn("API usage monitoring already running. Stop it first before starting again.");
            return;
        }
        
        monitoringStartTime = Instant.now();
        samples.clear();
        
        // Start monitoring in a separate thread
        Thread monitoringThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && monitoringStartTime != null) {
                    // Take a sample
                    takeSample();
                    
                    // Sleep for the window size
                    Thread.sleep(windowSizeSeconds * 1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("API usage monitoring interrupted");
            }
        });
        
        monitoringThread.setDaemon(true);
        monitoringThread.setName("API-Usage-Monitor");
        monitoringThread.start();
        
        logger.info("API usage monitoring started with {} second sampling interval", windowSizeSeconds);
    }
    
    /**
     * Stop monitoring API usage
     */
    public void stopMonitoring() {
        monitoringStartTime = null;
        logger.info("API usage monitoring stopped. Collected {} samples.", samples.size());
    }
    
    /**
     * Take a single sample of API usage
     */
    public void takeSample() {
        Map<String, Object> stats = apiClient.getApiStats();
        int totalRequests = (int) stats.get("totalRequests");
        long requestsInLastMinute = (long) stats.get("requestsInLastMinute");
        Map<String, Integer> projectStats = (Map<String, Integer>) stats.get("projectStats");
        
        ApiUsageSample sample = new ApiUsageSample(
                Instant.now(), 
                totalRequests, 
                requestsInLastMinute, 
                projectStats);
        
        synchronized (samples) {
            samples.add(sample);
        }
        
        // Calculate request rate based on last few samples if we have enough
        if (samples.size() >= 2) {
            calculateRequestRate();
        }
    }
    
    /**
     * Calculate and log the current request rate
     */
    private void calculateRequestRate() {
        synchronized (samples) {
            if (samples.size() < 2) {
                return;
            }
            
            ApiUsageSample latest = samples.get(samples.size() - 1);
            ApiUsageSample previous = samples.get(samples.size() - 2);
            
            long timeDiffMs = latest.getTimestamp().toEpochMilli() - previous.getTimestamp().toEpochMilli();
            int requestDiff = latest.getTotalRequests() - previous.getTotalRequests();
            
            if (timeDiffMs > 0) {
                double requestsPerSecond = (double) requestDiff / (timeDiffMs / 1000.0);
                double requestsPerMinute = requestsPerSecond * 60.0;
                
                // Format time for readability
                String timeStr = formatInstant(latest.getTimestamp());
                
                // Only log if there were requests during this period
                if (requestDiff > 0) {
                    logger.info("API Rate at {}: {:.1f} req/sec ({:.1f} req/min)", 
                            timeStr, requestsPerSecond, requestsPerMinute);
                    
                    // Warn if approaching rate limit
                    if (requestsPerMinute > 85.0) {
                        logger.warn("Approaching rate limit! Current rate: {:.1f} req/min (limit: 100/min)",
                                requestsPerMinute);
                    }
                    
                    // Log per-project rates if we have multiple projects
                    if (latest.getProjectRequestCounts().size() > 1) {
                        for (Map.Entry<String, Integer> entry : latest.getProjectRequestCounts().entrySet()) {
                            String projectId = entry.getKey();
                            int currentCount = entry.getValue();
                            int previousCount = previous.getProjectRequestCounts().getOrDefault(projectId, 0);
                            int projectDiff = currentCount - previousCount;
                            
                            if (projectDiff > 0) {
                                double projectReqPerSecond = (double) projectDiff / (timeDiffMs / 1000.0);
                                logger.info("  Project {}: {:.1f} req/sec", 
                                        projectId, projectReqPerSecond);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Generate a detailed API usage report with time-series data
     */
    public String generateReport() {
        if (samples.isEmpty()) {
            return "No API usage data available";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("API Usage Report\n");
        report.append("================\n\n");
        
        // Calculate monitoring duration
        ApiUsageSample first = samples.get(0);
        ApiUsageSample last = samples.get(samples.size() - 1);
        long durationSeconds = first.getTimestamp().until(last.getTimestamp(), ChronoUnit.SECONDS);
        
        report.append(String.format("Monitoring period: %s to %s (%.1f minutes)\n", 
                formatInstant(first.getTimestamp()),
                formatInstant(last.getTimestamp()),
                durationSeconds / 60.0));
        report.append(String.format("Total samples: %d\n", samples.size()));
        report.append(String.format("Total requests: %d\n", last.getTotalRequests()));
        report.append(String.format("Average requests per minute: %.1f\n\n", 
                last.getTotalRequests() / (durationSeconds / 60.0)));
        
        // Projects accessed
        report.append("Projects accessed:\n");
        for (Map.Entry<String, Integer> entry : last.getProjectRequestCounts().entrySet()) {
            report.append(String.format("  %s: %d requests\n", entry.getKey(), entry.getValue()));
        }
        report.append("\n");
        
        // Time series data (every 5 samples to keep report manageable)
        report.append("Time series data (sampled):\n");
        report.append(String.format("%-10s %-10s %-15s\n", "Time", "Requests", "Req/Min"));
        
        int sampleInterval = Math.max(1, samples.size() / 20); // Show at most 20 data points
        for (int i = 0; i < samples.size(); i += sampleInterval) {
            ApiUsageSample sample = samples.get(i);
            report.append(String.format("%-10s %-10d %-15d\n", 
                    formatInstant(sample.getTimestamp()), 
                    sample.getTotalRequests(),
                    sample.getRequestsInLastMinute()));
        }
        
        report.append("\n");
        report.append("Report generated: ").append(formatInstant(Instant.now())).append("\n");
        
        return report.toString();
    }
    
    /**
     * Format an Instant for display
     */
    private String formatInstant(Instant instant) {
        return TIME_FORMAT.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
    
    /**
     * Get all collected samples
     */
    public List<ApiUsageSample> getSamples() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }
}