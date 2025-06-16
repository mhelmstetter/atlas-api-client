package com.mongodb.atlas.api.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Integration tests for AtlasNetworkAccessClient
 * Tests actual Atlas API operations - requires valid API credentials
 */
class AtlasNetworkAccessClientIntegrationTest extends AtlasIntegrationTestBase {

    private String testProjectId;
    private String testIpAddress;
    private String testCidrBlock;

    @BeforeEach
    void setupTest(TestInfo testInfo) {
        testProjectId = getTestProjectId();
        testIpAddress = "203.0.113.1"; // RFC5737 test IP
        testCidrBlock = "203.0.113.0/24"; // RFC5737 test CIDR
        logger.info("Running test: {} with IP: {}", testInfo.getDisplayName(), testIpAddress);
    }

    @AfterEach
    void tearDown() {
        cleanupTestIpAccessList(testProjectId, testIpAddress);
        cleanupTestIpAccessList(testProjectId, testCidrBlock);
    }

    @Test
    void testGetIpAccessList() {
        logger.info("Testing getIpAccessList for project: {}", testProjectId);
        
        List<Map<String, Object>> accessList = networkAccessClient.getIpAccessList(testProjectId);
        
        assertNotNull(accessList);
        logger.info("Found {} IP access list entries for project", accessList.size());
        
        // Verify access list entry structure if any entries exist
        for (Map<String, Object> entry : accessList) {
            // Entry should have either ipAddress or cidrBlock
            assertTrue(entry.containsKey("ipAddress") || entry.containsKey("cidrBlock"));
            
            String entryValue = entry.containsKey("ipAddress") ? 
                (String) entry.get("ipAddress") : (String) entry.get("cidrBlock");
            
            logger.debug("IP Access List Entry: {} - Comment: {}", 
                        entryValue, entry.get("comment"));
        }
    }

    @Test
    void testAddIpAddress() {
        String comment = "Test IP address added by integration test";
        logger.info("Testing addIpAddress: {} with comment: {}", testIpAddress, comment);
        
        Map<String, Object> response = networkAccessClient.addIpAddress(testProjectId, testIpAddress, comment);
        
        assertNotNull(response);
        logger.info("IP address added successfully: {}", testIpAddress);
        
        // Verify IP appears in access list
        List<Map<String, Object>> accessList = networkAccessClient.getIpAccessList(testProjectId);
        boolean ipFound = accessList.stream()
            .anyMatch(entry -> testIpAddress.equals(entry.get("ipAddress")));
        assertTrue(ipFound, "Added IP address should appear in access list");
        
        // Verify comment
        Map<String, Object> addedEntry = accessList.stream()
            .filter(entry -> testIpAddress.equals(entry.get("ipAddress")))
            .findFirst()
            .orElse(null);
        assertNotNull(addedEntry);
        assertEquals(comment, addedEntry.get("comment"));
    }

    @Test
    void testAddCidrBlock() {
        String comment = "Test CIDR block added by integration test";
        logger.info("Testing addCidrBlock: {} with comment: {}", testCidrBlock, comment);
        
        Map<String, Object> response = networkAccessClient.addCidrBlock(testProjectId, testCidrBlock, comment);
        
        assertNotNull(response);
        logger.info("CIDR block added successfully: {}", testCidrBlock);
        
        // Verify CIDR appears in access list
        List<Map<String, Object>> accessList = networkAccessClient.getIpAccessList(testProjectId);
        boolean cidrFound = accessList.stream()
            .anyMatch(entry -> testCidrBlock.equals(entry.get("cidrBlock")));
        assertTrue(cidrFound, "Added CIDR block should appear in access list");
    }

    @Test
    void testGetIpAccessListEntry() {
        // Add IP first
        networkAccessClient.addIpAddress(testProjectId, testIpAddress, "Test entry for get test");
        
        logger.info("Testing getIpAccessListEntry for: {}", testIpAddress);
        
        Map<String, Object> entry = networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        
        assertNotNull(entry);
        assertEquals(testIpAddress, entry.get("ipAddress"));
        assertNotNull(entry.get("comment"));
        
        logger.info("IP access list entry retrieved successfully: {}", testIpAddress);
    }

    @Test
    void testUpdateIpAccessListEntry() {
        // Add IP first
        networkAccessClient.addIpAddress(testProjectId, testIpAddress, "Original comment");
        
        String newComment = "Updated comment from integration test";
        logger.info("Testing updateIpAccessListEntry for: {} with new comment: {}", testIpAddress, newComment);
        
        Map<String, Object> response = networkAccessClient.updateIpAccessListEntry(
            testProjectId, testIpAddress, newComment);
        
        assertNotNull(response);
        logger.info("IP access list entry updated successfully: {}", testIpAddress);
        
        // Verify comment was updated
        Map<String, Object> updatedEntry = networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        assertEquals(newComment, updatedEntry.get("comment"));
    }

    @Test
    void testDeleteIpAccessListEntry() {
        // Add IP first
        networkAccessClient.addIpAddress(testProjectId, testIpAddress, "Entry to be deleted");
        
        // Verify entry exists
        Map<String, Object> entry = networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        assertNotNull(entry);
        
        logger.info("Testing deleteIpAccessListEntry for: {}", testIpAddress);
        
        Map<String, Object> response = networkAccessClient.deleteIpAccessListEntry(testProjectId, testIpAddress);
        assertNotNull(response);
        
        // Verify entry no longer exists
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        });
        
        logger.info("IP access list entry deleted successfully: {}", testIpAddress);
        
        // Don't need cleanup since we deleted it
        testIpAddress = null;
    }

    @Test
    void testNetworkAccessLifecycle() {
        logger.info("Testing complete network access lifecycle: add -> get -> update -> delete");
        
        String comment = "Lifecycle test entry";
        
        // Add
        Map<String, Object> addResponse = networkAccessClient.addIpAddress(testProjectId, testIpAddress, comment);
        assertNotNull(addResponse);
        
        // Get
        Map<String, Object> entry = networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        assertNotNull(entry);
        assertEquals(testIpAddress, entry.get("ipAddress"));
        assertEquals(comment, entry.get("comment"));
        
        // Update
        String newComment = "Updated lifecycle test entry";
        Map<String, Object> updateResponse = networkAccessClient.updateIpAccessListEntry(
            testProjectId, testIpAddress, newComment);
        assertNotNull(updateResponse);
        
        // Verify update
        Map<String, Object> updatedEntry = networkAccessClient.getIpAccessListEntry(testProjectId, testIpAddress);
        assertEquals(newComment, updatedEntry.get("comment"));
        
        // Delete
        Map<String, Object> deleteResponse = networkAccessClient.deleteIpAccessListEntry(testProjectId, testIpAddress);
        assertNotNull(deleteResponse);
        
        logger.info("Network access lifecycle test completed successfully");
        
        // Don't need cleanup since we deleted it
        testIpAddress = null;
    }

    @Test
    void testAllowAccessFromAnywhere() {
        logger.warn("Testing allowAccessFromAnywhere - THIS IS A SECURITY RISK!");
        
        String comment = "Integration test - REMOVE IMMEDIATELY";
        
        Map<String, Object> response = networkAccessClient.allowAccessFromAnywhere(testProjectId, comment);
        
        assertNotNull(response);
        logger.info("Access from anywhere added successfully");
        
        // Verify 0.0.0.0/0 appears in access list
        List<Map<String, Object>> accessList = networkAccessClient.getIpAccessList(testProjectId);
        boolean anywhereFound = accessList.stream()
            .anyMatch(entry -> "0.0.0.0/0".equals(entry.get("cidrBlock")));
        assertTrue(anywhereFound, "0.0.0.0/0 should appear in access list");
        
        // IMPORTANT: Clean up immediately for security
        try {
            networkAccessClient.deleteIpAccessListEntry(testProjectId, "0.0.0.0/0");
            logger.info("Successfully removed 0.0.0.0/0 from access list");
        } catch (Exception e) {
            logger.error("SECURITY WARNING: Failed to remove 0.0.0.0/0 from access list: {}", e.getMessage());
        }
    }

    @Test
    void testInvalidNetworkAccessOperations() {
        String invalidIpAddress = "999.999.999.999"; // Invalid IP
        
        // Test adding invalid IP
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            networkAccessClient.addIpAddress(testProjectId, invalidIpAddress, "Invalid IP test");
        });
        
        // Test getting non-existent entry
        String nonExistentIp = "203.0.113.254";
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            networkAccessClient.getIpAccessListEntry(testProjectId, nonExistentIp);
        });
        
        // Test deleting non-existent entry
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            networkAccessClient.deleteIpAccessListEntry(testProjectId, nonExistentIp);
        });
        
        logger.info("Invalid network access operations test completed successfully");
    }

    @Test
    void testAddIpAddressWithoutComment() {
        String testIpNoComment = "203.0.113.2";
        
        logger.info("Testing addIpAddress without comment: {}", testIpNoComment);
        
        try {
            Map<String, Object> response = networkAccessClient.addIpAddress(testProjectId, testIpNoComment, null);
            
            assertNotNull(response);
            logger.info("IP address added successfully without comment: {}", testIpNoComment);
            
            // Verify IP appears in access list
            List<Map<String, Object>> accessList = networkAccessClient.getIpAccessList(testProjectId);
            boolean ipFound = accessList.stream()
                .anyMatch(entry -> testIpNoComment.equals(entry.get("ipAddress")));
            assertTrue(ipFound, "Added IP address should appear in access list");
            
        } finally {
            // Cleanup
            cleanupTestIpAccessList(testProjectId, testIpNoComment);
        }
    }
}