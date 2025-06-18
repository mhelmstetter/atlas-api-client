package com.mongodb.atlas.api.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration management for Atlas API tests and client operations
 * Supports both properties files and environment variables with precedence:
 * 1. System properties (command line -D)
 * 2. Environment variables
 * 3. Properties file (atlas-client.properties)
 * 4. Default values
 */
public class AtlasTestConfig {

    private static final Logger logger = LoggerFactory.getLogger(AtlasTestConfig.class);
    
    // Configuration keys (following existing camelCase convention)
    public static final String API_PUBLIC_KEY = "apiPublicKey";
    public static final String API_PRIVATE_KEY = "apiPrivateKey";
    public static final String TEST_PROJECT_ID = "testProjectId";
    public static final String TEST_ORG_ID = "testOrgId";
    public static final String TEST_REGION = "testRegion";
    public static final String TEST_CLOUD_PROVIDER = "testCloudProvider";
    public static final String TEST_MONGO_VERSION = "testMongoVersion";
    public static final String DEBUG_LEVEL = "debugLevel";
    public static final String RATE_LIMIT_ENABLED = "rateLimitEnabled";
    
    // Cluster management configuration
    public static final String CLUSTER_REUSE_ENABLED = "clusterReuseEnabled";
    public static final String SHARED_CLUSTERS_ENABLED = "sharedClustersEnabled";
    public static final String CLUSTER_TIMEOUT_MINUTES = "clusterTimeoutMinutes";
    public static final String CLEANUP_EPHEMERAL_CLUSTERS = "cleanupEphemeralClusters";
    
    // Environment variable keys (for backward compatibility)
    public static final String ENV_API_PUBLIC_KEY = "ATLAS_API_PUBLIC_KEY";
    public static final String ENV_API_PRIVATE_KEY = "ATLAS_API_PRIVATE_KEY";
    public static final String ENV_TEST_PROJECT_ID = "ATLAS_TEST_PROJECT_ID";
    public static final String ENV_TEST_ORG_ID = "ATLAS_TEST_ORG_ID";
    
    
    // Default properties file name
    public static final String DEFAULT_PROPERTIES_FILE = "atlas-client.properties";
    
    private static AtlasTestConfig instance;
    private final Properties properties;
    
    private AtlasTestConfig() {
        this.properties = loadConfiguration();
    }
    
    /**
     * Get singleton instance of configuration
     */
    public static synchronized AtlasTestConfig getInstance() {
        if (instance == null) {
            instance = new AtlasTestConfig();
        }
        return instance;
    }
    
    /**
     * Load configuration from multiple sources with precedence
     */
    private Properties loadConfiguration() {
        Properties config = new Properties();
        
        // 1. Load from properties file
        loadFromPropertiesFile(config);
        
        // 2. Override with environment variables
        loadFromEnvironmentVariables(config);
        
        // 3. Override with system properties
        loadFromSystemProperties(config);
        
        // Log configuration status (without revealing secrets)
        logConfigurationStatus(config);
        
        return config;
    }
    
    /**
     * Load configuration from properties file
     */
    private void loadFromPropertiesFile(Properties config) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(DEFAULT_PROPERTIES_FILE)) {
            if (input != null) {
                config.load(input);
                logger.info("Loaded configuration from {}", DEFAULT_PROPERTIES_FILE);
            } else {
                logger.debug("Properties file {} not found in classpath", DEFAULT_PROPERTIES_FILE);
            }
        } catch (IOException e) {
            logger.warn("Failed to load properties file {}: {}", DEFAULT_PROPERTIES_FILE, e.getMessage());
        }
    }
    
    /**
     * Load configuration from environment variables
     */
    private void loadFromEnvironmentVariables(Properties config) {
        // Map environment variables to property keys
        mapEnvToProperty(config, ENV_API_PUBLIC_KEY, API_PUBLIC_KEY);
        mapEnvToProperty(config, ENV_API_PRIVATE_KEY, API_PRIVATE_KEY);
        mapEnvToProperty(config, ENV_TEST_PROJECT_ID, TEST_PROJECT_ID);
        mapEnvToProperty(config, ENV_TEST_ORG_ID, TEST_ORG_ID);
        
        logger.debug("Environment variables loaded");
    }
    
    /**
     * Load configuration from system properties
     */
    private void loadFromSystemProperties(Properties config) {
        // Copy all atlas.* system properties
        System.getProperties().entrySet().stream()
            .filter(entry -> entry.getKey().toString().startsWith("atlas."))
            .forEach(entry -> config.setProperty(entry.getKey().toString(), entry.getValue().toString()));
        
        logger.debug("System properties loaded");
    }
    
    /**
     * Map environment variable to property if it exists
     */
    private void mapEnvToProperty(Properties config, String envKey, String propKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            config.setProperty(propKey, envValue);
        }
    }
    
    /**
     * Log configuration status without revealing secrets
     */
    private void logConfigurationStatus(Properties config) {
        logger.info("Atlas Configuration Status:");
        logger.info("  API Public Key: {}", isConfigured(config, API_PUBLIC_KEY) ? "✓ configured" : "✗ missing");
        logger.info("  API Private Key: {}", isConfigured(config, API_PRIVATE_KEY) ? "✓ configured" : "✗ missing");
        logger.info("  Test Project ID: {}", getConfiguredValue(config, TEST_PROJECT_ID, "not set"));
        logger.info("  Test Org ID: {}", getConfiguredValue(config, TEST_ORG_ID, "not set"));
        logger.info("  Test Region: {}", getConfiguredValue(config, TEST_REGION, "US_EAST_1 (default)"));
        logger.info("  Cloud Provider: {}", getConfiguredValue(config, TEST_CLOUD_PROVIDER, "AWS (default)"));
        logger.info("  MongoDB Version: {}", getConfiguredValue(config, TEST_MONGO_VERSION, "7.0 (default)"));
    }
    
    private boolean isConfigured(Properties config, String key) {
        String value = config.getProperty(key);
        return value != null && !value.trim().isEmpty();
    }
    
    private String getConfiguredValue(Properties config, String key, String defaultDisplay) {
        String value = config.getProperty(key);
        return (value != null && !value.trim().isEmpty()) ? value : defaultDisplay;
    }
    
    // Getters for configuration values
    
    public String getApiPublicKey() {
        return properties.getProperty(API_PUBLIC_KEY);
    }
    
    public String getApiPrivateKey() {
        return properties.getProperty(API_PRIVATE_KEY);
    }
    
    public String getTestProjectId() {
        return properties.getProperty(TEST_PROJECT_ID);
    }
    
    public String getTestOrgId() {
        return properties.getProperty(TEST_ORG_ID);
    }
    
    public String getTestRegion() {
        return properties.getProperty(TEST_REGION, "US_EAST_1");
    }
    
    public String getTestCloudProvider() {
        return properties.getProperty(TEST_CLOUD_PROVIDER, "AWS");
    }
    
    public String getTestMongoVersion() {
        return properties.getProperty(TEST_MONGO_VERSION, "7.0");
    }
    
    public int getDebugLevel() {
        return Integer.parseInt(properties.getProperty(DEBUG_LEVEL, "0"));
    }
    
    public boolean isRateLimitEnabled() {
        return Boolean.parseBoolean(properties.getProperty(RATE_LIMIT_ENABLED, "true"));
    }
    
    // Cluster management getters
    
    public boolean isClusterReuseEnabled() {
        return Boolean.parseBoolean(properties.getProperty(CLUSTER_REUSE_ENABLED, "true"));
    }
    
    public boolean areSharedClustersEnabled() {
        return Boolean.parseBoolean(properties.getProperty(SHARED_CLUSTERS_ENABLED, "true"));
    }
    
    public int getClusterTimeoutMinutes() {
        return Integer.parseInt(properties.getProperty(CLUSTER_TIMEOUT_MINUTES, "15"));
    }
    
    public boolean shouldCleanupEphemeralClusters() {
        return Boolean.parseBoolean(properties.getProperty(CLEANUP_EPHEMERAL_CLUSTERS, "true"));
    }
    
    // Validation methods
    
    public boolean hasRequiredCredentials() {
        return getApiPublicKey() != null && getApiPrivateKey() != null &&
               !getApiPublicKey().trim().isEmpty() && !getApiPrivateKey().trim().isEmpty();
    }
    
    public void validateConfiguration() throws IllegalStateException {
        if (!hasRequiredCredentials()) {
            throw new IllegalStateException(
                "Atlas API credentials not configured. Please set " + API_PUBLIC_KEY + 
                " and " + API_PRIVATE_KEY + " in properties file or environment variables.");
        }
    }
    
    // Utility methods for tests
    
    public boolean isConfiguredForTesting() {
        return hasRequiredCredentials() && 
               (getTestProjectId() != null || getTestOrgId() != null);
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Create a summary of configuration for debugging
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Atlas Configuration Summary:\n");
        summary.append("  Credentials: ").append(hasRequiredCredentials() ? "✓" : "✗").append("\n");
        summary.append("  Test Project: ").append(getTestProjectId() != null ? "✓" : "✗").append("\n");
        summary.append("  Test Org: ").append(getTestOrgId() != null ? "✓" : "✗").append("\n");
        summary.append("  Region: ").append(getTestRegion()).append("\n");
        summary.append("  Provider: ").append(getTestCloudProvider()).append("\n");
        summary.append("  MongoDB: ").append(getTestMongoVersion()).append("\n");
        summary.append("  Ready for testing: ").append(isConfiguredForTesting() ? "✓" : "✗");
        return summary.toString();
    }
}