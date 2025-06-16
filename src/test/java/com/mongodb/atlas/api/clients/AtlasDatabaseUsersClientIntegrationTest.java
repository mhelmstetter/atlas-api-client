package com.mongodb.atlas.api.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Integration tests for AtlasDatabaseUsersClient
 * Tests actual Atlas API operations - requires valid API credentials
 */
class AtlasDatabaseUsersClientIntegrationTest extends AtlasIntegrationTestBase {

    private String testProjectId;
    private String testUsername;
    private String testPassword;

    @BeforeEach
    void setupTest(TestInfo testInfo) {
        testProjectId = getTestProjectId();
        testUsername = "testuser" + getTestSuffix();
        testPassword = "TestPassword123!";
        logger.info("Running test: {} with user: {}", testInfo.getDisplayName(), testUsername);
    }

    @AfterEach
    void tearDown() {
        cleanupTestDatabaseUser(testProjectId, testUsername);
    }

    @Test
    void testGetDatabaseUsers() {
        logger.info("Testing getDatabaseUsers for project: {}", testProjectId);
        
        List<Map<String, Object>> users = databaseUsersClient.getDatabaseUsers(testProjectId);
        
        assertNotNull(users);
        logger.info("Found {} database users in project", users.size());
        
        // Verify user structure if any users exist
        for (Map<String, Object> user : users) {
            assertTrue(user.containsKey("username"));
            assertTrue(user.containsKey("databaseName"));
            assertTrue(user.containsKey("roles"));
            
            logger.debug("Database User: {} - Database: {} - Roles: {}", 
                        user.get("username"), user.get("databaseName"), user.get("roles"));
        }
    }

    @Test
    void testCreateReadOnlyUser() {
        logger.info("Testing createReadOnlyUser: {}", testUsername);
        
        Map<String, Object> response = databaseUsersClient.createReadOnlyUser(
            testProjectId, testUsername, testPassword);
        
        assertNotNull(response);
        assertEquals(testUsername, response.get("username"));
        assertEquals("admin", response.get("databaseName"));
        
        // Verify roles
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) response.get("roles");
        assertNotNull(roles);
        assertFalse(roles.isEmpty());
        
        boolean hasReadRole = roles.stream()
            .anyMatch(role -> "read".equals(role.get("roleName")));
        assertTrue(hasReadRole, "User should have read role");
        
        logger.info("Read-only user created successfully: {}", testUsername);
        
        // Verify user appears in list
        List<Map<String, Object>> users = databaseUsersClient.getDatabaseUsers(testProjectId);
        boolean userFound = users.stream()
            .anyMatch(user -> testUsername.equals(user.get("username")));
        assertTrue(userFound, "Created user should appear in database users list");
    }

    @Test
    void testCreateReadWriteUser() {
        logger.info("Testing createReadWriteUser: {}", testUsername);
        
        Map<String, Object> response = databaseUsersClient.createReadWriteUser(
            testProjectId, testUsername, testPassword);
        
        assertNotNull(response);
        assertEquals(testUsername, response.get("username"));
        assertEquals("admin", response.get("databaseName"));
        
        // Verify roles
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) response.get("roles");
        assertNotNull(roles);
        assertFalse(roles.isEmpty());
        
        boolean hasReadWriteRole = roles.stream()
            .anyMatch(role -> "readWrite".equals(role.get("roleName")));
        assertTrue(hasReadWriteRole, "User should have readWrite role");
        
        logger.info("Read-write user created successfully: {}", testUsername);
    }

    @Test
    void testGetDatabaseUser() {
        // Create user first
        databaseUsersClient.createReadOnlyUser(testProjectId, testUsername, testPassword);
        
        logger.info("Testing getDatabaseUser for: {}", testUsername);
        
        Map<String, Object> user = databaseUsersClient.getDatabaseUser(testProjectId, testUsername);
        
        assertNotNull(user);
        assertEquals(testUsername, user.get("username"));
        assertEquals("admin", user.get("databaseName"));
        assertNotNull(user.get("roles"));
        
        logger.info("Database user retrieved successfully: {}", testUsername);
    }

    @Test
    void testUpdateDatabaseUser() {
        // Create user first
        databaseUsersClient.createReadOnlyUser(testProjectId, testUsername, testPassword);
        
        logger.info("Testing updateDatabaseUser for: {}", testUsername);
        
        // Create readWrite role for update
        Map<String, Object> readWriteRole = Map.of(
            "roleName", "readWrite",
            "databaseName", "admin"
        );
        
        Map<String, Object> response = databaseUsersClient.updateDatabaseUser(
            testProjectId, testUsername, null, List.of(readWriteRole), null);
        
        assertNotNull(response);
        assertEquals(testUsername, response.get("username"));
        
        // Verify updated roles
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) response.get("roles");
        assertNotNull(roles);
        
        boolean hasReadWriteRole = roles.stream()
            .anyMatch(role -> "readWrite".equals(role.get("roleName")));
        assertTrue(hasReadWriteRole, "User should have updated readWrite role");
        
        logger.info("Database user updated successfully: {}", testUsername);
    }

    @Test
    void testDeleteDatabaseUser() {
        // Create user first
        databaseUsersClient.createReadOnlyUser(testProjectId, testUsername, testPassword);
        
        // Verify user exists
        Map<String, Object> user = databaseUsersClient.getDatabaseUser(testProjectId, testUsername);
        assertNotNull(user);
        
        logger.info("Testing deleteDatabaseUser for: {}", testUsername);
        
        Map<String, Object> response = databaseUsersClient.deleteDatabaseUser(testProjectId, testUsername);
        assertNotNull(response);
        
        // Verify user no longer exists
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            databaseUsersClient.getDatabaseUser(testProjectId, testUsername);
        });
        
        logger.info("Database user deleted successfully: {}", testUsername);
        
        // Don't need cleanup since we deleted it
        testUsername = null;
    }

    @Test
    void testDatabaseUserLifecycle() {
        logger.info("Testing complete database user lifecycle: create -> get -> update -> delete");
        
        // Create
        Map<String, Object> createResponse = databaseUsersClient.createReadOnlyUser(
            testProjectId, testUsername, testPassword);
        assertNotNull(createResponse);
        assertEquals(testUsername, createResponse.get("username"));
        
        // Get
        Map<String, Object> user = databaseUsersClient.getDatabaseUser(testProjectId, testUsername);
        assertNotNull(user);
        assertEquals(testUsername, user.get("username"));
        
        // Update to readWrite
        Map<String, Object> readWriteRole = Map.of(
            "roleName", "readWrite",
            "databaseName", "admin"
        );
        Map<String, Object> updateResponse = databaseUsersClient.updateDatabaseUser(
            testProjectId, testUsername, null, List.of(readWriteRole), null);
        assertNotNull(updateResponse);
        
        // Delete
        Map<String, Object> deleteResponse = databaseUsersClient.deleteDatabaseUser(testProjectId, testUsername);
        assertNotNull(deleteResponse);
        
        logger.info("Database user lifecycle test completed successfully");
        
        // Don't need cleanup since we deleted it
        testUsername = null;
    }

    @Test
    void testInvalidDatabaseUserOperations() {
        String invalidUsername = "non-existent-user-" + getTestSuffix();
        
        // Test getting non-existent user
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            databaseUsersClient.getDatabaseUser(testProjectId, invalidUsername);
        });
        
        // Test deleting non-existent user
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            databaseUsersClient.deleteDatabaseUser(testProjectId, invalidUsername);
        });
        
        logger.info("Invalid database user operations test completed successfully");
    }

    @Test
    void testCreateUserWithCustomRoles() {
        logger.info("Testing createDatabaseUser with custom roles");
        
        // Create user with multiple roles
        Map<String, Object> readRole = Map.of(
            "roleName", "read",
            "databaseName", "admin"
        );
        Map<String, Object> readAnyDbRole = Map.of(
            "roleName", "readAnyDatabase",
            "databaseName", "admin"
        );
        
        Map<String, Object> response = databaseUsersClient.createDatabaseUser(
            testProjectId, testUsername, testPassword, 
            List.of(readRole, readAnyDbRole), null);
        
        assertNotNull(response);
        assertEquals(testUsername, response.get("username"));
        
        // Verify roles
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) response.get("roles");
        assertNotNull(roles);
        assertEquals(2, roles.size());
        
        logger.info("Database user with custom roles created successfully: {}", testUsername);
    }
}