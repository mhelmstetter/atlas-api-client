package com.mongodb.atlas.api.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Integration tests for AtlasProjectsClient
 * Tests actual Atlas API operations - requires valid API credentials
 */
class AtlasProjectsClientIntegrationTest extends AtlasIntegrationTestBase {

    private String testProjectId;

    @BeforeEach
    void setupTest(TestInfo testInfo) {
        testProjectId = getTestProjectId();
        logger.info("Running test: {} with project: {}", testInfo.getDisplayName(), testProjectId);
    }

    @Test
    void testGetAllProjects() {
        logger.info("Testing getAllProjects");
        
        List<Map<String, Object>> projects = projectsClient.getAllProjects();
        
        assertNotNull(projects);
        assertFalse(projects.isEmpty(), "Should have at least one project");
        
        logger.info("Found {} projects", projects.size());
        
        // Verify project structure
        for (Map<String, Object> project : projects) {
            assertTrue(project.containsKey("id"));
            assertTrue(project.containsKey("name"));
            assertTrue(project.containsKey("orgId"));
            
            logger.debug("Project: {} ({})", project.get("name"), project.get("id"));
        }
    }

    @Test
    void testGetProjects() {
        logger.info("Testing getProjects with filter");
        
        // First get all projects to find a name to filter by
        List<Map<String, Object>> allProjects = projectsClient.getAllProjects();
        assertFalse(allProjects.isEmpty());
        
        String projectName = (String) allProjects.get(0).get("name");
        
        // Test filtering by specific project name
        Map<String, String> filteredProjects = projectsClient.getProjects(Set.of(projectName));
        
        assertNotNull(filteredProjects);
        assertTrue(filteredProjects.containsKey(projectName));
        assertEquals(1, filteredProjects.size());
        
        logger.info("Successfully filtered projects by name: {}", projectName);
        
        // Test getting all projects (empty filter)
        Map<String, String> allProjectsMap = projectsClient.getProjects(Set.of());
        assertNotNull(allProjectsMap);
        assertTrue(allProjectsMap.size() >= allProjects.size());
        
        logger.info("Successfully retrieved all projects via filter");
    }

    @Test
    void testGetProject() {
        logger.info("Testing getProject for ID: {}", testProjectId);
        
        Map<String, Object> project = projectsClient.getProject(testProjectId);
        
        assertNotNull(project);
        assertEquals(testProjectId, project.get("id"));
        assertNotNull(project.get("name"));
        assertNotNull(project.get("orgId"));
        
        logger.info("Successfully retrieved project: {} ({})", project.get("name"), project.get("id"));
    }

    @Test
    void testGetProjectByName() {
        // First get a project to know its name
        Map<String, Object> project = projectsClient.getProject(testProjectId);
        String projectName = (String) project.get("name");
        
        logger.info("Testing getProjectByName for: {}", projectName);
        
        Map<String, Object> foundProject = projectsClient.getProjectByName(projectName);
        
        assertNotNull(foundProject);
        assertEquals(projectName, foundProject.get("name"));
        assertEquals(testProjectId, foundProject.get("id"));
        
        logger.info("Successfully retrieved project by name");
        
        // Test non-existent project
        Map<String, Object> notFoundProject = projectsClient.getProjectByName("non-existent-project-" + getTestSuffix());
        assertNull(notFoundProject);
        
        logger.info("Correctly returned null for non-existent project");
    }

    @Test
    void testGetProjectTeams() {
        logger.info("Testing getProjectTeams for project: {}", testProjectId);
        
        List<Map<String, Object>> teams = projectsClient.getProjectTeams(testProjectId);
        
        assertNotNull(teams);
        logger.info("Found {} teams for project", teams.size());
        
        // Verify team structure if any teams exist
        for (Map<String, Object> team : teams) {
            assertTrue(team.containsKey("id"));
            assertTrue(team.containsKey("name"));
            
            logger.debug("Team: {} ({})", team.get("name"), team.get("id"));
        }
    }

    @Test
    void testGetProjectUsers() {
        logger.info("Testing getProjectUsers for project: {}", testProjectId);
        
        List<Map<String, Object>> users = projectsClient.getProjectUsers(testProjectId);
        
        assertNotNull(users);
        assertFalse(users.isEmpty(), "Project should have at least one user (the API key owner)");
        
        logger.info("Found {} users for project", users.size());
        
        // Verify user structure
        for (Map<String, Object> user : users) {
            assertTrue(user.containsKey("id"));
            // Users may have username or emailAddress
            assertTrue(user.containsKey("username") || user.containsKey("emailAddress"));
            assertTrue(user.containsKey("roles"));
            
            logger.debug("User: {} - Roles: {}", 
                        user.getOrDefault("username", user.get("emailAddress")), 
                        user.get("roles"));
        }
    }

    @Test
    void testGetProjectSettings() {
        logger.info("Testing getProjectSettings for project: {}", testProjectId);
        
        Map<String, Object> settings = projectsClient.getProjectSettings(testProjectId);
        
        assertNotNull(settings);
        logger.info("Successfully retrieved project settings");
        
        // Log some common settings (structure may vary)
        logger.debug("Project settings keys: {}", settings.keySet());
    }

    @Test
    void testCreateAndDeleteProject() {
        // Skip this test if we don't have an org ID
        if (config.getTestOrgId() == null) {
            logger.warn("No test organization ID configured - skipping project creation test");
            return;
        }
        
        String testProjectName = "test-project-" + getTestSuffix();
        
        logger.info("Testing project creation: {}", testProjectName);
        
        // Create project
        Map<String, Object> createResponse = projectsClient.createProject(testProjectName, config.getTestOrgId());
        
        assertNotNull(createResponse);
        assertEquals(testProjectName, createResponse.get("name"));
        assertEquals(config.getTestOrgId(), createResponse.get("orgId"));
        assertNotNull(createResponse.get("id"));
        
        String createdProjectId = (String) createResponse.get("id");
        logger.info("Project created successfully with ID: {}", createdProjectId);
        
        try {
            // Verify project exists
            Map<String, Object> retrievedProject = projectsClient.getProject(createdProjectId);
            assertNotNull(retrievedProject);
            assertEquals(testProjectName, retrievedProject.get("name"));
            
            // Test project appears in list
            List<Map<String, Object>> projects = projectsClient.getAllProjects();
            boolean projectFound = projects.stream()
                .anyMatch(p -> createdProjectId.equals(p.get("id")));
            assertTrue(projectFound, "Created project should appear in project list");
            
        } finally {
            // Clean up - delete the test project
            try {
                Map<String, Object> deleteResponse = projectsClient.deleteProject(createdProjectId);
                assertNotNull(deleteResponse);
                logger.info("Test project deleted successfully: {}", createdProjectId);
            } catch (Exception e) {
                logger.error("Failed to delete test project: {}", e.getMessage());
            }
        }
    }

    @Test
    void testInvalidProjectOperations() {
        String invalidProjectId = "invalid-project-id-" + getTestSuffix();
        
        // Test getting non-existent project
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            projectsClient.getProject(invalidProjectId);
        });
        
        // Test deleting non-existent project
        assertThrows(AtlasApiBase.AtlasApiException.class, () -> {
            projectsClient.deleteProject(invalidProjectId);
        });
        
        logger.info("Invalid project operations test completed successfully");
    }
}