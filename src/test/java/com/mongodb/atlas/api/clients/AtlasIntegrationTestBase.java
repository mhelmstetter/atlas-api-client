package com.mongodb.atlas.api.clients;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach; 
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.config.AtlasTestConfig;
import com.mongodb.atlas.api.test.TestClusterManager;

/**
 * Base class for Atlas integration tests
 * Handles common setup and configuration for all Atlas API tests
 */
public abstract class AtlasIntegrationTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(AtlasIntegrationTestBase.class);

    protected static AtlasTestConfig config;
    protected static TestClusterManager testClusterManager;
    
    // Shared clusters across all tests
    private static final Map<String, String> sharedClusters = new ConcurrentHashMap<>();
    
    protected AtlasApiBase apiBase;
    protected AtlasClustersClient clustersClient;
    protected AtlasProjectsClient projectsClient;
    protected AtlasDatabaseUsersClient databaseUsersClient;
    protected AtlasNetworkAccessClient networkAccessClient;
    protected AtlasBackupsClient backupsClient;
    protected AtlasFlexClustersClient flexClustersClient;

    @BeforeAll
    static void setupClass() {
        // Load configuration from properties file, environment variables, and system properties
        config = AtlasTestConfig.getInstance();
        
        logger.info("=== Atlas Integration Test Setup ===");
        logger.info(config.getConfigurationSummary());
        logger.info("=====================================");
        
        // Initialize cluster manager if credentials are available and cluster reuse is enabled
        if (config.hasRequiredCredentials() && config.isClusterReuseEnabled()) {
            setupTestClusterManager();
            if (config.areSharedClustersEnabled()) {
                setupSharedClusters();
            }
        }
    }
    
    @AfterAll
    static void tearDownClass() {
        if (testClusterManager != null) {
            logger.info("=== Atlas Integration Test Teardown ===");
            logger.info(testClusterManager.getClusterSummary());
            
            // Clean up isolated clusters if configured to do so (shared clusters are preserved)
            if (config.shouldCleanupIsolatedClusters()) {
                try {
                    testClusterManager.cleanupIsolatedClusters();
                    logger.info("Isolated cluster cleanup completed");
                } catch (Exception e) {
                    logger.warn("Failed to cleanup isolated clusters: {}", e.getMessage());
                }
            } else {
                logger.info("Isolated cluster cleanup skipped (cleanupIsolatedClusters=false)");
            }
            
            logger.info("=======================================");
        }
    }
    
    /**
     * Initialize the test cluster manager
     */
    private static void setupTestClusterManager() {
        try {
            AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
            AtlasClustersClient clustersClient = new AtlasClustersClient(apiBase);
            AtlasFlexClustersClient flexClustersClient = new AtlasFlexClustersClient(apiBase);
            
            testClusterManager = new TestClusterManager(config, clustersClient, flexClustersClient);
            logger.info("Test cluster manager initialized");
        } catch (Exception e) {
            logger.warn("Failed to initialize test cluster manager: {}", e.getMessage());
        }
    }
    
    /**
     * Setup shared clusters for testing
     */
    private static void setupSharedClusters() {
        if (testClusterManager == null) {
            return;
        }
        
        logger.info("Setting up shared test clusters...");
        
        try {
            // Setup M0 equivalent (Flex) cluster for basic testing
            String flexCluster = testClusterManager.getOrCreateSharedCluster("M0", "7.0");
            sharedClusters.put("flex", flexCluster);
            logger.info("Shared Flex cluster: {}", flexCluster);
            
            // Setup M10 cluster for advanced testing (if budget allows)
            // Only create if configured to do so
            if (shouldCreateDedicatedSharedCluster()) {
                String dedicatedCluster = testClusterManager.getOrCreateSharedCluster("M10", "7.0");
                sharedClusters.put("dedicated", dedicatedCluster);
                logger.info("Shared dedicated cluster: {}", dedicatedCluster);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to setup shared clusters: {}", e.getMessage());
        }
    }
    
    /**
     * Check if we should create dedicated (paid) shared clusters
     */
    private static boolean shouldCreateDedicatedSharedCluster() {
        // For now, only create free clusters by default
        // This can be configured later via properties
        return false;
    }

    @BeforeEach
    void setup() {
        // Skip tests if Atlas credentials are not available
        Assumptions.assumeTrue(config.hasRequiredCredentials(), 
                              "Atlas API credentials not provided. Create atlas-test.properties file or set environment variables.");
        
        // Initialize API clients
        apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
        clustersClient = new AtlasClustersClient(apiBase);
        projectsClient = new AtlasProjectsClient(apiBase);
        databaseUsersClient = new AtlasDatabaseUsersClient(apiBase);
        networkAccessClient = new AtlasNetworkAccessClient(apiBase);
        backupsClient = new AtlasBackupsClient(apiBase);
        flexClustersClient = new AtlasFlexClustersClient(apiBase);
    }

    /**
     * Helper method to ensure we have a test project
     */
    protected String getTestProjectId() {
        String projectId = config.getTestProjectId();
        if (projectId != null) {
            return projectId;
        }
        
        // If no project ID provided, use the first available project
        var projects = projectsClient.getAllProjects();
        Assumptions.assumeFalse(projects.isEmpty(), "No Atlas projects available for testing");
        
        projectId = (String) projects.get(0).get("id");
        logger.info("Using project ID '{}' for testing", projectId);
        return projectId;
    }

    /**
     * Generate a unique test name suffix
     */
    protected String getTestSuffix() {
        return "test-" + System.currentTimeMillis();
    }
    
    /**
     * Get the name of the current test method
     */
    protected String getTestMethodName(TestInfo testInfo) {
        return testInfo.getTestMethod()
            .map(method -> method.getName())
            .orElse("unknown");
    }
    
    /**
     * Get the name of the current test class (simple name)
     */
    protected String getTestClassName(TestInfo testInfo) {
        return testInfo.getTestClass()
            .map(clazz -> clazz.getSimpleName())
            .orElse("UnknownTest");
    }
    
    // ========================================================================
    // Cluster Management Methods
    // ========================================================================
    
    /**
     * Get a shared cluster for testing (reused across tests)
     * These clusters are not automatically deleted
     */
    protected String getSharedFlexCluster() {
        String clusterName = sharedClusters.get("flex");
        if (clusterName == null) {
            throw new IllegalStateException("Shared Flex cluster not available");
        }
        return clusterName;
    }
    
    /**
     * Get a shared dedicated cluster for testing (if available)
     */
    protected String getSharedDedicatedCluster() {
        String clusterName = sharedClusters.get("dedicated");
        if (clusterName == null) {
            throw new IllegalStateException("Shared dedicated cluster not available");
        }
        return clusterName;
    }
    
    /**
     * Create an isolated cluster for a specific test
     * This cluster will be automatically cleaned up after test completion
     */
    protected String createIsolatedTestCluster(TestInfo testInfo, String instanceSize, String mongoVersion) {
        if (testClusterManager == null) {
            throw new IllegalStateException("Test cluster manager not available");
        }
        
        String testClass = getTestClassName(testInfo);
        String testMethod = getTestMethodName(testInfo);
        
        return testClusterManager.createIsolatedCluster(testClass, testMethod, instanceSize, mongoVersion);
    }
    
    /**
     * Create an isolated Flex cluster for a specific test
     */
    protected String createIsolatedFlexCluster(TestInfo testInfo) {
        return createIsolatedTestCluster(testInfo, "M0", "7.0");
    }
    
    /**
     * Create an isolated dedicated cluster for a specific test
     */
    protected String createIsolatedDedicatedCluster(TestInfo testInfo) {
        return createIsolatedTestCluster(testInfo, "M10", "7.0");
    }
    
    /**
     * Wait for a cluster to become ready for use
     */
    protected boolean waitForClusterReady(String clusterName, Duration timeout) {
        if (testClusterManager == null) {
            return false;
        }
        return testClusterManager.waitForClusterReady(clusterName, timeout);
    }
    
    /**
     * Wait for a cluster to become ready with configured timeout
     */
    protected boolean waitForClusterReady(String clusterName) {
        int timeoutMinutes = config.getClusterTimeoutMinutes();
        return waitForClusterReady(clusterName, Duration.ofMinutes(timeoutMinutes));
    }
    
    /**
     * Check if a cluster is ready for use
     */
    protected boolean isClusterReady(String clusterName) {
        if (testClusterManager == null) {
            return false;
        }
        return testClusterManager.isClusterReady(clusterName);
    }

    /**
     * Clean up helper - attempt to delete test cluster if it exists
     */
    protected void cleanupTestCluster(String projectId, String clusterName) {
        try {
            clustersClient.deleteCluster(projectId, clusterName);
            logger.info("Cleaned up test cluster: {}", clusterName);
        } catch (Exception e) {
            logger.debug("Cluster cleanup failed (may not exist): {}", e.getMessage());
        }
    }

    /**
     * Clean up helper - attempt to delete test database user if it exists
     */
    protected void cleanupTestDatabaseUser(String projectId, String username) {
        try {
            databaseUsersClient.deleteDatabaseUser(projectId, username);
            logger.info("Cleaned up test database user: {}", username);
        } catch (Exception e) {
            logger.debug("Database user cleanup failed (may not exist): {}", e.getMessage());
        }
    }

    /**
     * Clean up helper - attempt to delete test IP access list entry if it exists
     */
    protected void cleanupTestIpAccessList(String projectId, String ipAddress) {
        try {
            networkAccessClient.deleteIpAccessListEntry(projectId, ipAddress);
            logger.info("Cleaned up test IP access list entry: {}", ipAddress);
        } catch (Exception e) {
            logger.debug("IP access list cleanup failed (may not exist): {}", e.getMessage());
        }
    }
}