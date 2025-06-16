package com.mongodb.atlas.api.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for AtlasFlexClustersClient
 * Tests actual Atlas API operations for Flex clusters - requires valid API credentials
 * 
 * Flex Clusters are cost-effective clusters for development and low-throughput applications
 * with pay-as-you-go pricing ($8-$30/month based on usage)
 */
class AtlasFlexClustersClientIntegrationTest extends AtlasIntegrationTestBase {

    private String testProjectId;
    private String testFlexClusterName;
    private AtlasFlexClustersClient flexClustersClient;

    @BeforeEach
    void setupTest(TestInfo testInfo) {
        testProjectId = getTestProjectId();
        testFlexClusterName = "test-flex-" + getTestSuffix();
        flexClustersClient = new AtlasFlexClustersClient(apiBase);
        logger.info("Running test: {} with Flex cluster: {}", testInfo.getDisplayName(), testFlexClusterName);
    }

    @AfterEach
    void tearDown() {
        cleanupTestFlexCluster(testProjectId, testFlexClusterName);
    }

    @Test
    void testGetFlexClusters() {
        logger.info("Testing getFlexClusters for project: {}", testProjectId);
        
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        
        assertNotNull(flexClusters);
        logger.info("Found {} Flex clusters in project", flexClusters.size());
        
        // Verify Flex cluster structure if any clusters exist
        for (Map<String, Object> cluster : flexClusters) {
            assertTrue(cluster.containsKey("id"));
            assertTrue(cluster.containsKey("name"));
            assertTrue(cluster.containsKey("stateName"));
            assertEquals("FLEX", cluster.get("clusterType"));
            
            logger.debug("Flex Cluster: {} - State: {}", cluster.get("name"), cluster.get("stateName"));
        }
    }

    @Test
    void testGetFlexClusterDetails() {
        // Skip if no Flex clusters exist
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping cluster details test");
            return;
        }
        
        String clusterName = (String) flexClusters.get(0).get("name");
        logger.info("Testing getFlexCluster for: {}", clusterName);
        
        Map<String, Object> cluster = flexClustersClient.getFlexCluster(testProjectId, clusterName);
        
        assertNotNull(cluster);
        assertEquals(clusterName, cluster.get("name"));
        assertEquals("FLEX", cluster.get("clusterType"));
        assertNotNull(cluster.get("stateName"));
        assertNotNull(cluster.get("mongoDBVersion"));
        
        logger.info("Flex cluster details retrieved successfully for: {}", clusterName);
    }

    @Test
    @Timeout(180) // 3 minute timeout for Flex cluster creation (faster than regular clusters)
    void testCreateFlexCluster() {
        logger.info("Testing Flex cluster creation: {}", testFlexClusterName);
        
        Map<String, Object> response = flexClustersClient.createFlexCluster(
            testProjectId, 
            testFlexClusterName, 
            "7.0", 
            "US_EAST_1", 
            "AWS"
        );
        
        assertNotNull(response);
        assertEquals(testFlexClusterName, response.get("name"));
        assertEquals("FLEX", response.get("clusterType"));
        assertTrue(response.get("stateName").toString().equals("CREATING") || 
                  response.get("stateName").toString().equals("IDLE"));
        
        logger.info("Flex cluster creation initiated successfully: {}", testFlexClusterName);
        
        // Verify cluster appears in list
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        boolean clusterFound = flexClusters.stream()
            .anyMatch(cluster -> testFlexClusterName.equals(cluster.get("name")));
        assertTrue(clusterFound, "Created Flex cluster should appear in project cluster list");
    }

    @Test
    void testWaitForFlexClusterState() {
        // Skip if no Flex clusters exist
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping wait for state test");
            return;
        }
        
        String clusterName = (String) flexClusters.get(0).get("name");
        String currentState = (String) flexClusters.get(0).get("stateName");
        
        logger.info("Testing waitForFlexClusterState for cluster: {} (current state: {})", clusterName, currentState);
        
        // Wait for current state (should return immediately)
        boolean result = flexClustersClient.waitForFlexClusterState(testProjectId, clusterName, currentState, 30);
        assertTrue(result, "Should successfully wait for current Flex cluster state");
        
        logger.info("Wait for Flex cluster state test completed successfully");
    }

    @Test
    void testFlexClusterTerminationProtection() {
        // Skip if no Flex clusters exist
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping termination protection test");
            return;
        }
        
        String clusterName = (String) flexClusters.get(0).get("name");
        logger.info("Testing termination protection for Flex cluster: {}", clusterName);
        
        // Enable termination protection
        Map<String, Object> enableResponse = flexClustersClient.enableTerminationProtection(testProjectId, clusterName);
        assertNotNull(enableResponse);
        
        // Verify protection is enabled
        Map<String, Object> cluster = flexClustersClient.getFlexCluster(testProjectId, clusterName);
        Boolean protectionEnabled = (Boolean) cluster.get("terminationProtectionEnabled");
        assertTrue(protectionEnabled != null && protectionEnabled, "Termination protection should be enabled");
        
        // Disable termination protection
        Map<String, Object> disableResponse = flexClustersClient.disableTerminationProtection(testProjectId, clusterName);
        assertNotNull(disableResponse);
        
        // Verify protection is disabled
        cluster = flexClustersClient.getFlexCluster(testProjectId, clusterName);
        protectionEnabled = (Boolean) cluster.get("terminationProtectionEnabled");
        assertFalse(protectionEnabled != null && protectionEnabled, "Termination protection should be disabled");
        
        logger.info("Termination protection test completed successfully");
    }

    @Test
    void testCreateAndDeleteFlexClusterLifecycle() {
        logger.info("Testing complete Flex cluster lifecycle: create -> delete");
        
        // Create Flex cluster
        Map<String, Object> createResponse = flexClustersClient.createFlexCluster(
            testProjectId, 
            testFlexClusterName, 
            "7.0", 
            "US_EAST_1", 
            "AWS"
        );
        
        assertNotNull(createResponse);
        assertEquals(testFlexClusterName, createResponse.get("name"));
        assertEquals("FLEX", createResponse.get("clusterType"));
        
        // Verify cluster exists
        Map<String, Object> cluster = flexClustersClient.getFlexCluster(testProjectId, testFlexClusterName);
        assertNotNull(cluster);
        assertEquals(testFlexClusterName, cluster.get("name"));
        assertEquals("FLEX", cluster.get("clusterType"));
        
        // Delete cluster
        Map<String, Object> deleteResponse = flexClustersClient.deleteFlexCluster(testProjectId, testFlexClusterName);
        assertNotNull(deleteResponse);
        
        logger.info("Flex cluster lifecycle test completed successfully");
        
        // Note: Don't need to cleanup in afterEach since we deleted it here
        testFlexClusterName = null;
    }

    @Test
    void testUpdateFlexCluster() {
        // Skip if no Flex clusters exist
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping update test");
            return;
        }
        
        String clusterName = (String) flexClusters.get(0).get("name");
        logger.info("Testing updateFlexCluster for: {}", clusterName);
        
        // Update cluster with termination protection
        Map<String, Object> updateSpec = new HashMap<>();
        updateSpec.put("terminationProtectionEnabled", true);
        
        Map<String, Object> response = flexClustersClient.updateFlexCluster(testProjectId, clusterName, updateSpec);
        
        assertNotNull(response);
        assertEquals(clusterName, response.get("name"));
        
        // Verify update took effect
        Map<String, Object> updatedCluster = flexClustersClient.getFlexCluster(testProjectId, clusterName);
        Boolean protectionEnabled = (Boolean) updatedCluster.get("terminationProtectionEnabled");
        assertTrue(protectionEnabled != null && protectionEnabled, "Termination protection should be enabled");
        
        // Reset to original state
        updateSpec.put("terminationProtectionEnabled", false);
        flexClustersClient.updateFlexCluster(testProjectId, clusterName, updateSpec);
        
        logger.info("Flex cluster update test completed successfully");
    }

    @Test
    void testUpgradeFlexCluster() {
        // This test should only run if there's a Flex cluster available and the user understands
        // that upgrading will change billing from Flex pricing to dedicated cluster pricing
        
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping upgrade test");
            return;
        }
        
        // For safety, we'll just test the upgrade API structure without actually upgrading
        // since upgrading changes the billing model significantly
        String clusterName = (String) flexClusters.get(0).get("name");
        logger.info("Testing upgrade process structure for Flex cluster: {} (NOT actually upgrading)", clusterName);
        
        // Just verify we can get the cluster info that would be needed for upgrade
        Map<String, Object> cluster = flexClustersClient.getFlexCluster(testProjectId, clusterName);
        assertNotNull(cluster);
        assertEquals("FLEX", cluster.get("clusterType"));
        
        logger.info("Upgrade test structure validated (actual upgrade not performed for cost safety)");
        
        // NOTE: To actually test upgrade, uncomment the following lines:
        // Map<String, Object> upgradeResponse = flexClustersClient.upgradeFlexCluster(
        //     testProjectId, clusterName, "M10");
        // assertNotNull(upgradeResponse);
        // logger.info("Flex cluster upgrade initiated successfully");
    }

    @Test
    void testGetFlexClusterConnectionInfo() {
        // Skip if no Flex clusters exist
        List<Map<String, Object>> flexClusters = flexClustersClient.getFlexClusters(testProjectId);
        if (flexClusters.isEmpty()) {
            logger.warn("No Flex clusters found in project - skipping connection info test");
            return;
        }
        
        String clusterName = (String) flexClusters.get(0).get("name");
        logger.info("Testing getFlexClusterConnectionInfo for: {}", clusterName);
        
        Map<String, Object> connectionInfo = flexClustersClient.getFlexClusterConnectionInfo(testProjectId, clusterName);
        
        assertNotNull(connectionInfo);
        assertEquals(clusterName, connectionInfo.get("clusterName"));
        assertNotNull(connectionInfo.get("stateName"));
        assertNotNull(connectionInfo.get("mongoDBVersion"));
        
        // Connection strings may not be available if cluster is still creating
        if (connectionInfo.containsKey("connectionStrings")) {
            logger.info("Connection strings available for Flex cluster");
        } else {
            logger.info("Connection strings not yet available (cluster may still be creating)");
        }
        
        logger.info("Flex cluster connection info test completed successfully");
    }

    @Test
    void testInvalidFlexClusterOperations() {
        String invalidClusterName = "non-existent-flex-cluster-" + getTestSuffix();
        
        // Test getting non-existent Flex cluster
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            flexClustersClient.getFlexCluster(testProjectId, invalidClusterName);
        });
        
        // Test deleting non-existent Flex cluster
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            flexClustersClient.deleteFlexCluster(testProjectId, invalidClusterName);
        });
        
        // Test updating non-existent Flex cluster
        Map<String, Object> updateSpec = new HashMap<>();
        updateSpec.put("terminationProtectionEnabled", true);
        
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            flexClustersClient.updateFlexCluster(testProjectId, invalidClusterName, updateSpec);
        });
        
        logger.info("Invalid Flex cluster operations test completed successfully");
    }

    @Test
    void testFlexClusterSpecificFeatures() {
        logger.info("Testing Flex cluster specific characteristics");
        
        // Create a test Flex cluster to examine its properties
        Map<String, Object> createResponse = flexClustersClient.createFlexCluster(
            testProjectId, 
            testFlexClusterName, 
            "7.0", 
            "US_EAST_1", 
            "AWS"
        );
        
        assertNotNull(createResponse);
        assertEquals("FLEX", createResponse.get("clusterType"));
        
        // Verify Flex-specific characteristics
        Map<String, Object> cluster = flexClustersClient.getFlexCluster(testProjectId, testFlexClusterName);
        
        // Flex clusters should have specific characteristics
        assertEquals("FLEX", cluster.get("clusterType"));
        
        // Verify no dedicated cluster features
        assertFalse(cluster.containsKey("autoScaling"), "Flex clusters should not have auto-scaling");
        
        logger.info("Flex cluster specific features test completed successfully");
    }

    /**
     * Helper method to clean up test Flex cluster
     */
    private void cleanupTestFlexCluster(String projectId, String clusterName) {
        if (clusterName == null) return;
        
        try {
            flexClustersClient.deleteFlexCluster(projectId, clusterName);
            logger.info("Cleaned up test Flex cluster: {}", clusterName);
        } catch (Exception e) {
            logger.debug("Flex cluster cleanup failed (may not exist): {}", e.getMessage());
        }
    }
}