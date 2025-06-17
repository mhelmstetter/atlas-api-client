package com.mongodb.atlas.api.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

import com.mongodb.atlas.api.config.AtlasTestConfig;

/**
 * Simple test for Flex cluster creation
 */
public class SimpleFlexTest {

    private AtlasFlexClustersClient flexClient;
    private String testProjectId;

    @BeforeEach
    public void setUp() {
        AtlasTestConfig config = AtlasTestConfig.getInstance();
        
        if (!config.hasRequiredCredentials()) {
            System.out.println("Skipping test - Atlas credentials not configured");
            return;
        }

        AtlasApiBase apiBase = new AtlasApiBase(
            config.getApiPublicKey(),
            config.getApiPrivateKey(),
            2  // Debug level
        );
        
        flexClient = new AtlasFlexClustersClient(apiBase);
        testProjectId = config.getTestProjectId();
    }

    @Test
    public void testFlexClusterCreation() {
        if (flexClient == null || testProjectId == null) {
            System.out.println("Skipping test - Atlas credentials not configured");
            return;
        }

        String clusterName = "test-flex-" + System.currentTimeMillis();
        String mongoVersion = "7.0";
        String region = "US_WEST_2";
        String cloudProvider = "AWS";

        try {
            System.out.println("Creating Flex cluster: " + clusterName);
            
            Map<String, Object> result = flexClient.createFlexCluster(
                testProjectId, clusterName, mongoVersion, region, cloudProvider);
            
            assertNotNull(result);
            assertTrue(result.containsKey("name"));
            assertEquals(clusterName, result.get("name"));
            
            System.out.println("Flex cluster creation successful!");
            System.out.println("Cluster details: " + result);
            
        } catch (Exception e) {
            System.err.println("Flex cluster creation failed: " + e.getMessage());
            e.printStackTrace();
            fail("Flex cluster creation failed: " + e.getMessage());
        }
    }
}