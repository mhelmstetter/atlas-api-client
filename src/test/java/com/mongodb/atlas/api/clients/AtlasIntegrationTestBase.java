package com.mongodb.atlas.api.clients;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.config.AtlasTestConfig;

/**
 * Base class for Atlas integration tests
 * Handles common setup and configuration for all Atlas API tests
 */
public abstract class AtlasIntegrationTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(AtlasIntegrationTestBase.class);

    protected static AtlasTestConfig config;
    
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