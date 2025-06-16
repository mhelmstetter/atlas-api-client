package com.mongodb.atlas.api.clients;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test suite for all Atlas API integration tests
 * Run this to execute all Atlas API client tests together
 */
@Suite
@SuiteDisplayName("MongoDB Atlas API Integration Test Suite")
@SelectClasses({
    AtlasClustersClientIntegrationTest.class,
    AtlasProjectsClientIntegrationTest.class,
    AtlasDatabaseUsersClientIntegrationTest.class,
    AtlasNetworkAccessClientIntegrationTest.class,
    AtlasFlexClustersClientIntegrationTest.class
    // Note: Backup tests would require M10+ clusters, so not included in main suite
    // AtlasBackupsClientIntegrationTest.class
})
public class AtlasApiIntegrationTestSuite {
    // This class is just a test suite runner - no implementation needed
}