package com.mongodb.atlas.api.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.mongodb.atlas.api.config.AtlasTestConfig;

/**
 * Integration tests for AtlasProgrammaticAPIKeysClient
 * 
 * Note: These tests will create real API keys in Atlas.
 * Make sure to clean up any test keys that are created.
 */
public class AtlasProgrammaticAPIKeysClientTest {

    private AtlasProgrammaticAPIKeysClient apiKeysClient;
    private String testOrgId;
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
            1  // Debug level
        );
        
        apiKeysClient = new AtlasProgrammaticAPIKeysClient(apiBase);
        testOrgId = config.getTestOrgId();
        testProjectId = config.getTestProjectId();
    }

    @Test
    public void testListOrganizationAPIKeys() {
        if (apiKeysClient == null || testOrgId == null) {
            System.out.println("Skipping test - Atlas credentials or org ID not configured");
            return;
        }

        try {
            System.out.println("Listing API keys for organization: " + testOrgId);
            
            List<Map<String, Object>> apiKeys = apiKeysClient.getOrganizationAPIKeys(testOrgId);
            
            assertNotNull(apiKeys);
            System.out.println("Found " + apiKeys.size() + " API keys");
            
            // Print details of existing API keys (excluding sensitive data)
            for (Map<String, Object> apiKey : apiKeys) {
                System.out.println("API Key ID: " + apiKey.get("id"));
                System.out.println("Description: " + apiKey.get("desc"));
                System.out.println("Roles: " + apiKey.get("roles"));
                System.out.println("---");
            }
            
        } catch (Exception e) {
            System.err.println("Failed to list API keys: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to list API keys: " + e.getMessage());
        }
    }

    @Test
    public void testCreateAndDeleteAPIKey() {
        if (apiKeysClient == null || testOrgId == null) {
            System.out.println("Skipping test - Atlas credentials or org ID not configured");
            return;
        }

        String testApiKeyDescription = "Test API Key - " + System.currentTimeMillis();
        List<String> roles = Arrays.asList(
            AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY
        );

        try {
            System.out.println("Creating test API key: " + testApiKeyDescription);
            
            // Create API key
            Map<String, Object> createResponse = apiKeysClient.createOrganizationAPIKey(
                testOrgId, testApiKeyDescription, roles);
            
            assertNotNull(createResponse);
            assertTrue(createResponse.containsKey("id"));
            assertTrue(createResponse.containsKey("publicKey"));
            assertTrue(createResponse.containsKey("privateKey"));
            
            String apiKeyId = (String) createResponse.get("id");
            String publicKey = (String) createResponse.get("publicKey");
            String privateKey = (String) createResponse.get("privateKey");
            
            System.out.println("Created API key with ID: " + apiKeyId);
            System.out.println("Public key: " + publicKey);
            System.out.println("Private key: " + privateKey.substring(0, 8) + "...");
            
            // Verify API key was created
            Map<String, Object> retrievedApiKey = apiKeysClient.getOrganizationAPIKey(testOrgId, apiKeyId);
            assertNotNull(retrievedApiKey);
            assertEquals(apiKeyId, retrievedApiKey.get("id"));
            assertEquals(testApiKeyDescription, retrievedApiKey.get("desc"));
            
            // Clean up - delete the test API key
            System.out.println("Deleting test API key: " + apiKeyId);
            Map<String, Object> deleteResponse = apiKeysClient.deleteOrganizationAPIKey(testOrgId, apiKeyId);
            assertNotNull(deleteResponse);
            
            System.out.println("Test API key deleted successfully");
            
        } catch (Exception e) {
            System.err.println("Failed to create/delete API key: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to create/delete API key: " + e.getMessage());
        }
    }

    @Test
    public void testAPIKeyAccessListManagement() {
        if (apiKeysClient == null || testOrgId == null) {
            System.out.println("Skipping test - Atlas credentials or org ID not configured");
            return;
        }

        String testApiKeyDescription = "Test API Key for Access List - " + System.currentTimeMillis();
        List<String> roles = Arrays.asList(
            AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY
        );

        try {
            // Create API key for testing access lists
            Map<String, Object> createResponse = apiKeysClient.createOrganizationAPIKey(
                testOrgId, testApiKeyDescription, roles);
            
            String apiKeyId = (String) createResponse.get("id");
            System.out.println("Created test API key for access list testing: " + apiKeyId);
            
            // Test access list operations
            List<String> accessListEntries = Arrays.asList("192.168.1.0/24", "10.0.0.1");
            
            System.out.println("Adding access list entries...");
            Map<String, Object> accessListResponse = apiKeysClient.createAPIKeyAccessList(
                testOrgId, apiKeyId, accessListEntries);
            assertNotNull(accessListResponse);
            
            // Get access list
            System.out.println("Retrieving access list...");
            List<Map<String, Object>> accessList = apiKeysClient.getAPIKeyAccessList(testOrgId, apiKeyId);
            assertNotNull(accessList);
            System.out.println("Access list has " + accessList.size() + " entries");
            
            // Clean up access list entries
            for (String entry : accessListEntries) {
                try {
                    System.out.println("Removing access list entry: " + entry);
                    apiKeysClient.deleteAPIKeyAccessListEntry(testOrgId, apiKeyId, entry);
                } catch (Exception e) {
                    System.out.println("Note: Could not remove entry " + entry + ": " + e.getMessage());
                }
            }
            
            // Clean up - delete the test API key
            apiKeysClient.deleteOrganizationAPIKey(testOrgId, apiKeyId);
            System.out.println("Test API key deleted successfully");
            
        } catch (Exception e) {
            System.err.println("Failed to test access list management: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to test access list management: " + e.getMessage());
        }
    }

    @Test
    public void testAPIKeyRolesAndConstants() {
        // Test that role constants are available
        assertNotNull(AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_OWNER);
        assertNotNull(AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY);
        assertNotNull(AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_OWNER);
        assertNotNull(AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_READ_ONLY);
        
        // Test project assignment helper
        Map<String, Object> assignment = AtlasProgrammaticAPIKeysClient.createProjectAssignment(
            "test-project-id", 
            Arrays.asList(AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_READ_ONLY)
        );
        
        assertNotNull(assignment);
        assertEquals("test-project-id", assignment.get("groupId"));
        assertTrue(assignment.containsKey("roles"));
        
        System.out.println("API key role constants and helpers working correctly");
    }
}