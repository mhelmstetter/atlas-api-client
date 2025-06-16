# Atlas API Client Integration Tests

This document describes how to run the comprehensive integration tests for the MongoDB Atlas API client.

## Prerequisites

1. **MongoDB Atlas Account**: You need a valid MongoDB Atlas account
2. **API Keys**: Create Atlas API keys with appropriate permissions
3. **Test Project**: Have a test project available (or organization access to create projects)

## Setting Up API Credentials

### Creating Atlas API Keys

1. Log into MongoDB Atlas
2. Go to Organization Settings → Access Manager → API Keys
3. Create a new API key with the following permissions:
   - Project Owner (for full testing capabilities)
   - Organization Member (for project creation tests)

### Environment Variables

Set the following environment variables before running tests:

```bash
# Required for all tests
export ATLAS_API_PUBLIC_KEY="your_atlas_api_public_key"
export ATLAS_API_PRIVATE_KEY="your_atlas_api_private_key"

# Optional - if not set, tests will use the first available project
export ATLAS_TEST_PROJECT_ID="your_test_project_id"

# Optional - required only for project creation tests
export ATLAS_TEST_ORG_ID="your_organization_id"
```

## Running Tests

### All Integration Tests
```bash
mvn test
```

### Specific Test Classes
```bash
# Test only clusters API
mvn test -Dtest=AtlasClustersClientIntegrationTest

# Test only projects API
mvn test -Dtest=AtlasProjectsClientIntegrationTest

# Test only database users API
mvn test -Dtest=AtlasDatabaseUsersClientIntegrationTest

# Test only network access API
mvn test -Dtest=AtlasNetworkAccessClientIntegrationTest

# Test only Flex clusters API
mvn test -Dtest=AtlasFlexClustersClientIntegrationTest
```

### Specific Test Methods
```bash
# Test only cluster creation
mvn test -Dtest=AtlasClustersClientIntegrationTest#testCreateCluster

# Test only database user lifecycle
mvn test -Dtest=AtlasDatabaseUsersClientIntegrationTest#testDatabaseUserLifecycle

# Test only Flex cluster creation
mvn test -Dtest=AtlasFlexClustersClientIntegrationTest#testCreateFlexCluster
```

### Compile Only (Skip Tests)
```bash
mvn compile -DskipTests
```

## Test Coverage

### AtlasClustersClient Tests
- ✅ List clusters in project
- ✅ Get cluster details
- ✅ Create cluster (M0 free tier)
- ✅ Delete cluster
- ✅ Wait for cluster state
- ✅ Complete cluster lifecycle (create → delete)
- ✅ Get processes for project/cluster
- ✅ Error handling for invalid operations

### AtlasProjectsClient Tests
- ✅ List all projects
- ✅ Get projects with filtering
- ✅ Get project by ID
- ✅ Get project by name
- ✅ Get project teams and users
- ✅ Get project settings
- ✅ Create and delete project (requires org access)
- ✅ Error handling for invalid operations

### AtlasDatabaseUsersClient Tests
- ✅ List database users
- ✅ Create read-only user
- ✅ Create read-write user
- ✅ Get database user details
- ✅ Update database user roles
- ✅ Delete database user
- ✅ Complete user lifecycle (create → update → delete)
- ✅ Custom roles assignment
- ✅ Error handling for invalid operations

### AtlasNetworkAccessClient Tests
- ✅ List IP access list entries
- ✅ Add IP address to access list
- ✅ Add CIDR block to access list
- ✅ Get specific access list entry
- ✅ Update access list entry comment
- ✅ Delete access list entry
- ✅ Complete network access lifecycle
- ✅ Allow access from anywhere (with immediate cleanup)
- ✅ Error handling for invalid operations

### AtlasFlexClustersClient Tests
- ✅ List Flex clusters in project
- ✅ Get Flex cluster details
- ✅ Create Flex cluster (cost-effective alternative to M0/M10+)
- ✅ Update Flex cluster (termination protection)
- ✅ Delete Flex cluster
- ✅ Wait for Flex cluster state
- ✅ Complete Flex cluster lifecycle (create → delete)
- ✅ Termination protection enable/disable
- ✅ Flex cluster upgrade structure validation
- ✅ Get connection information
- ✅ Flex-specific feature validation
- ✅ Error handling for invalid operations

### AtlasBackupsClient Tests
- ⚠️ **Note**: Backup tests are not included as they require dedicated clusters (M10+)
- The client is implemented and ready for testing with appropriate clusters

## Test Behavior

### Safety Measures
- Tests use **test-specific naming** with timestamps to avoid conflicts
- **Automatic cleanup** after each test (in `@AfterEach` methods)
- Tests **skip gracefully** if required credentials are not available
- Network access tests use **RFC5737 test IPs** (203.0.113.x)
- "Allow from anywhere" test includes **immediate cleanup** for security

### Non-Destructive Testing
- Tests only create and manage resources with "test-" prefixes
- Tests use M0 (free tier) clusters when possible
- Tests clean up all created resources automatically
- Tests skip operations that require elevated permissions if not available

### Error Testing
- Tests verify proper error handling for invalid operations
- Tests confirm API exceptions are thrown for non-existent resources
- Tests validate input validation and proper error messages

## Troubleshooting

### Common Issues

1. **Missing API Keys**
   ```
   org.opentest4j.TestAbortedException: Assumption failed: Atlas API credentials not provided
   ```
   - Solution: Set `ATLAS_API_PUBLIC_KEY` and `ATLAS_API_PRIVATE_KEY` environment variables

2. **No Projects Available**
   ```
   org.opentest4j.TestAbortedException: Assumption failed: No Atlas projects available for testing
   ```
   - Solution: Create a project in Atlas or set `ATLAS_TEST_PROJECT_ID`

3. **Permission Errors**
   ```
   AtlasApiException: Failed to create cluster/user/etc.
   ```
   - Solution: Ensure API key has appropriate permissions (Project Owner recommended)

4. **Network Timeouts**
   - Atlas API operations can be slow, especially cluster creation
   - Tests include appropriate timeouts (5 minutes for cluster creation)
   - Check network connectivity to Atlas API endpoints

### Debugging

Enable debug logging by setting:
```bash
export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.log.com.mongodb.atlas=DEBUG"
mvn test
```

### Test Reports

Maven Surefire generates test reports in:
- `target/surefire-reports/` - Detailed test results
- Console output includes test progress and any failures

## Best Practices

1. **Use a dedicated test project** to avoid interfering with production resources
2. **Run tests in a safe environment** (development/staging)
3. **Monitor Atlas usage** as tests may create billable resources (though they clean up)
4. **Set reasonable timeout expectations** for cluster operations
5. **Review test logs** for any cleanup failures that might leave resources

## Contributing

When adding new tests:
1. Extend `AtlasIntegrationTestBase` for common setup
2. Use `@AfterEach` cleanup methods for resource management
3. Use descriptive test names and logging
4. Test both success and error scenarios
5. Ensure tests are idempotent and don't interfere with each other