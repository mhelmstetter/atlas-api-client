package com.mongodb.atlas.api.clients;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main entry point for Atlas API operations.
 * Provides access to specialized clients for different API categories.
 */
public class AtlasApiClient {
    
    private final AtlasApiBase apiBase;
    private final AtlasMonitoringClient monitoring;
    private final AtlasClustersClient clusters;
    private final AtlasLogsClient logs;
    
    public AtlasApiClient(String apiPublicKey, String apiPrivateKey) {
        this(apiPublicKey, apiPrivateKey, 2);
    }
    
    public AtlasApiClient(String apiPublicKey, String apiPrivateKey, int debugLevel) {
        this.apiBase = new AtlasApiBase(apiPublicKey, apiPrivateKey, debugLevel);
        this.monitoring = new AtlasMonitoringClient(apiBase);
        this.clusters = new AtlasClustersClient(apiBase);
        this.logs = new AtlasLogsClient(apiBase);
    }
    
    
    public AtlasClustersClient clusters() {
        return clusters;
    }
    
    /**
     * Get the monitoring/metrics client
     */
    public AtlasMonitoringClient monitoring() {
        return monitoring;
    }
    
    /**
     * Get the logs client
     */
    public AtlasLogsClient logs() {
        return logs;
    }
    
    /**
     * Access base functionality (rate limiting stats, debug level, etc.)
     */
    public AtlasApiBase base() {
        return apiBase;
    }
    
    /**
     * Convenience method to set debug level across all clients
     */
    public void setDebugLevel(int level) {
        apiBase.setDebugLevel(level);
    }
    
    /**
     * Convenience method to get API usage statistics
     */
    public Map<String, Object> getApiStats() {
        return apiBase.getApiStats();
    }
}