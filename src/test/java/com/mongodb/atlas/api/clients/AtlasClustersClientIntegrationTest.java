package com.mongodb.atlas.api.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for AtlasClustersClient
 * Tests actual Atlas API operations - requires valid API credentials
 */
class AtlasClustersClientIntegrationTest extends AtlasIntegrationTestBase {

    private String testProjectId;
    private String testClusterName;

    @BeforeEach
    void setupTest(TestInfo testInfo) {
        testProjectId = getTestProjectId();
        testClusterName = "test-cluster-" + getTestSuffix();
        logger.info("Running test: {} with cluster: {}", testInfo.getDisplayName(), testClusterName);
    }

    @AfterEach
    void tearDown() {
        cleanupTestCluster(testProjectId, testClusterName);
    }

    @Test
    void testGetClusters() {
        logger.info("Testing getClusters for project: {}", testProjectId);
        
        List<Map<String, Object>> clusters = clustersClient.getClusters(testProjectId);
        
        assertNotNull(clusters);
        logger.info("Found {} clusters in project", clusters.size());
        
        // Verify cluster structure
        for (Map<String, Object> cluster : clusters) {
            assertTrue(cluster.containsKey("id"));
            assertTrue(cluster.containsKey("name"));
            assertTrue(cluster.containsKey("stateName"));
            
            logger.debug("Cluster: {} - State: {}", cluster.get("name"), cluster.get("stateName"));
        }
    }

    @Test
    void testGetClusterDetails() {
        // Skip if no clusters exist
        List<Map<String, Object>> clusters = clustersClient.getClusters(testProjectId);
        if (clusters.isEmpty()) {
            logger.warn("No clusters found in project - skipping cluster details test");
            return;
        }
        
        String clusterName = (String) clusters.get(0).get("name");
        logger.info("Testing getCluster for: {}", clusterName);
        
        Map<String, Object> cluster = clustersClient.getCluster(testProjectId, clusterName);
        
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.get("name"));
        assertNotNull(cluster.get("stateName"));
        assertNotNull(cluster.get("mongoDBVersion"));
        
        logger.info("Cluster details retrieved successfully for: {}", clusterName);
    }

    @Test
    @Timeout(300) // 5 minute timeout for cluster creation
    void testCreateCluster() {
        logger.info("Testing cluster creation: {}", testClusterName);
        
        Map<String, Object> response = clustersClient.createCluster(
            testProjectId, 
            testClusterName, 
            "M0", // Free tier
            "7.0", 
            "US_EAST_1", 
            "AWS"
        );
        
        assertNotNull(response);
        assertEquals(testClusterName, response.get("name"));
        assertTrue(response.get("stateName").toString().equals("CREATING") || 
                  response.get("stateName").toString().equals("IDLE"));
        
        logger.info("Cluster creation initiated successfully: {}", testClusterName);
        
        // Verify cluster appears in list
        List<Map<String, Object>> clusters = clustersClient.getClusters(testProjectId);
        boolean clusterFound = clusters.stream()
            .anyMatch(cluster -> testClusterName.equals(cluster.get("name")));
        assertTrue(clusterFound, "Created cluster should appear in project cluster list");
    }

    @Test
    void testWaitForClusterState() {
        // Skip if no clusters exist
        List<Map<String, Object>> clusters = clustersClient.getClusters(testProjectId);
        if (clusters.isEmpty()) {
            logger.warn("No clusters found in project - skipping wait for state test");
            return;
        }
        
        String clusterName = (String) clusters.get(0).get("name");
        String currentState = (String) clusters.get(0).get("stateName");
        
        logger.info("Testing waitForClusterState for cluster: {} (current state: {})", clusterName, currentState);
        
        // Wait for current state (should return immediately)
        boolean result = clustersClient.waitForClusterState(testProjectId, clusterName, currentState, 30);
        assertTrue(result, "Should successfully wait for current cluster state");
        
        logger.info("Wait for cluster state test completed successfully");
    }

    @Test
    void testCreateAndDeleteClusterLifecycle() {
        logger.info("Testing complete cluster lifecycle: create -> delete");
        
        // Create cluster
        Map<String, Object> createResponse = clustersClient.createCluster(
            testProjectId, 
            testClusterName, 
            "M0", 
            "7.0", 
            "US_EAST_1", 
            "AWS"
        );
        
        assertNotNull(createResponse);
        assertEquals(testClusterName, createResponse.get("name"));
        
        // Verify cluster exists
        Map<String, Object> cluster = clustersClient.getCluster(testProjectId, testClusterName);
        assertNotNull(cluster);
        assertEquals(testClusterName, cluster.get("name"));
        
        // Delete cluster
        Map<String, Object> deleteResponse = clustersClient.deleteCluster(testProjectId, testClusterName);
        assertNotNull(deleteResponse);
        
        logger.info("Cluster lifecycle test completed successfully");
        
        // Note: Don't need to cleanup in afterEach since we deleted it here
        testClusterName = null;
    }

    @Test
    void testGetProcesses() {
        logger.info("Testing getProcesses for project: {}", testProjectId);
        
        List<Map<String, Object>> processes = clustersClient.getProcesses(testProjectId);
        
        assertNotNull(processes);
        logger.info("Found {} processes in project", processes.size());
        
        // Verify process structure if any processes exist
        for (Map<String, Object> process : processes) {
            assertTrue(process.containsKey("id"));
            assertTrue(process.containsKey("hostname"));
            assertTrue(process.containsKey("port"));
            
            logger.debug("Process: {} - Port: {}", process.get("hostname"), process.get("port"));
        }
    }

    @Test
    void testGetProcessesForCluster() {
        // Skip if no clusters exist
        List<Map<String, Object>> clusters = clustersClient.getClusters(testProjectId);
        if (clusters.isEmpty()) {
            logger.warn("No clusters found in project - skipping processes for cluster test");
            return;
        }
        
        String clusterName = (String) clusters.get(0).get("name");
        logger.info("Testing getProcessesForCluster for: {}", clusterName);
        
        List<Map<String, Object>> processes = clustersClient.getProcessesForCluster(testProjectId, clusterName);
        
        assertNotNull(processes);
        logger.info("Found {} processes for cluster {}", processes.size(), clusterName);
    }

    @Test
    void testInvalidClusterOperations() {
        String invalidClusterName = "non-existent-cluster-" + getTestSuffix();
        
        // Test getting non-existent cluster
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            clustersClient.getCluster(testProjectId, invalidClusterName);
        });
        
        // Test deleting non-existent cluster
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            clustersClient.deleteCluster(testProjectId, invalidClusterName);
        });
        
        logger.info("Invalid cluster operations test completed successfully");
    }
}