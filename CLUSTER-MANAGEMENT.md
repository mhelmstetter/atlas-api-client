# Atlas Cluster Management Strategy

This document explains the improved cluster management strategy for Atlas API integration tests, designed to reduce costs, improve efficiency, and enable better resource sharing.

## Overview

The new cluster management system addresses key challenges with Atlas testing:

- **Cost Reduction**: Reuse shared clusters across tests instead of creating new ones for each test
- **Efficiency**: Faster test execution by using existing ready clusters
- **Resource Safety**: Automatic cleanup of isolated resources while preserving shared infrastructure
- **Flexibility**: Support both shared clusters and isolated clusters based on test requirements

## Key Components

### 1. TestClusterManager

The `TestClusterManager` class orchestrates cluster lifecycle management:

```java
// Get or create a shared cluster (reused across tests)
String sharedCluster = testClusterManager.getOrCreateSharedCluster("M0", "7.0");

// Create an isolated cluster (automatically cleaned up)
String isolatedCluster = testClusterManager.createIsolatedCluster("TestClass", "testMethod", "M10", "7.0");

// Discovery and management
List<Map<String, Object>> testClusters = testClusterManager.findClustersByPattern("test-.*");
boolean isReady = testClusterManager.waitForClusterReady(clusterName, Duration.ofMinutes(15));
```

### 2. Enhanced AtlasClustersClient

New cluster discovery and management utilities:

```java
// Find clusters by pattern
List<Map<String, Object>> clusters = clustersClient.findClustersByPattern(projectId, "shared-test-.*");

// Find or create pattern
Map<String, Object> cluster = clustersClient.findOrCreateCluster(projectId, clusterName, "M10", "7.0", region, provider);

// Batch operations
List<String> deleted = clustersClient.deleteClustersByPattern(projectId, "isolated-test-.*");
```

### 3. Enhanced Integration Test Base

Convenience methods for test classes:

```java
public class MyIntegrationTest extends AtlasIntegrationTestBase {
    
    @Test
    void testWithSharedCluster() {
        // Use shared cluster for read-only tests
        String clusterName = getSharedFlexCluster();
        // Test operations that don't modify cluster
    }
    
    @Test
    void testWithIsolatedCluster(TestInfo testInfo) {
        // Create isolated cluster for destructive tests
        String clusterName = createIsolatedFlexCluster(testInfo);
        // Test cluster creation, deletion, modification
    }
}
```

## Cluster Naming Strategy

### Shared Clusters (Preserved Across Test Runs)

- **Pattern**: `shared-test-{type}-{version}`
- **Examples**: 
  - `shared-test-m0-70` (Flex cluster, MongoDB 7.0)
  - `shared-test-m10-70` (Dedicated M10, MongoDB 7.0)
- **Lifecycle**: Created once, reused across all tests, not automatically deleted

### Isolated Clusters (Test-Specific)

- **Pattern**: `isolated-test-{class}-{method}-{timestamp}`
- **Examples**: 
  - `isolated-test-atlasclusterstest-testcreation-1734364800000`
  - `isolated-test-flexclusterstest-testupgrade-1734364801000`
- **Lifecycle**: Created per test, automatically cleaned up after test suite

## Configuration Options

Configure cluster management behavior in `atlas-test.properties`:

```properties
# Enable/disable cluster reuse (default: true)
clusterReuseEnabled=true

# Enable/disable shared clusters (default: true)  
sharedClustersEnabled=true

# Cluster ready timeout in minutes (default: 15)
clusterTimeoutMinutes=15

# Cleanup isolated clusters after tests (default: true)
cleanupIsolatedClusters=true
```

### Environment-Specific Configurations

**Development Environment**:
```properties
# Maximize reuse for local development
clusterReuseEnabled=true
sharedClustersEnabled=true
cleanupIsolatedClusters=false  # Keep for debugging
clusterTimeoutMinutes=20
```

**CI/CD Environment**:
```properties
# Conservative approach for CI
clusterReuseEnabled=true
sharedClustersEnabled=true
cleanupIsolatedClusters=true
clusterTimeoutMinutes=10
```

**Load Testing Environment**:
```properties
# Each test gets its own cluster
clusterReuseEnabled=false
sharedClustersEnabled=false
cleanupIsolatedClusters=true
clusterTimeoutMinutes=30
```

## Test Patterns

### Pattern 1: Read-Only Tests (Use Shared Clusters)

For tests that only read data or test non-destructive operations:

```java
@Test
void testListClusters() {
    String projectId = getTestProjectId();
    String clusterName = getSharedFlexCluster();
    
    // Verify cluster exists
    assertTrue(isClusterReady(clusterName));
    
    // Test read operations
    List<Map<String, Object>> clusters = clustersClient.getClusters(projectId);
    assertFalse(clusters.isEmpty());
}
```

### Pattern 2: Cluster Lifecycle Tests (Use Isolated Clusters)

For tests that create, modify, or delete clusters:

```java
@Test
void testClusterCreationAndDeletion(TestInfo testInfo) {
    String projectId = getTestProjectId();
    
    // Create isolated cluster for this test
    String clusterName = createIsolatedFlexCluster(testInfo);
    
    // Wait for cluster to be ready
    assertTrue(waitForClusterReady(clusterName));
    
    // Test cluster operations
    Map<String, Object> cluster = clustersClient.getCluster(projectId, clusterName);
    assertEquals("IDLE", cluster.get("stateName"));
    
    // Cluster will be automatically cleaned up by TestClusterManager
}
```

### Pattern 3: Mixed Testing Strategy

```java
@TestMethodOrder(OrderAnnotation.class)
class AtlasClusterIntegrationTest extends AtlasIntegrationTestBase {
    
    @Test
    @Order(1)
    void testBasicClusterOperations() {
        // Use shared cluster for basic read operations
        String clusterName = getSharedFlexCluster();
        // ... test read operations
    }
    
    @Test
    @Order(2)
    void testClusterCreation(TestInfo testInfo) {
        // Create isolated cluster for creation testing
        String clusterName = createIsolatedDedicatedCluster(testInfo);
        // ... test cluster creation and modification
    }
    
    @Test  
    @Order(3)
    void testClusterUpgrade(TestInfo testInfo) {
        // Another isolated cluster for upgrade testing
        String clusterName = createIsolatedFlexCluster(testInfo);
        // ... test cluster upgrades
    }
}
```

## Cost Management

### Shared Cluster Strategy

The system automatically creates shared clusters based on the most cost-effective options:

1. **Flex Clusters** (M0 equivalent): $8-30/month usage-based
   - Used for basic testing scenarios
   - Shared across all read-only tests
   - Created once, reused for entire test suite lifecycle

2. **Dedicated Clusters** (M10): ~$57/month minimum
   - Only created when specifically needed
   - Can be disabled via configuration
   - Used for advanced testing requiring dedicated resources

### Cost Optimization Tips

1. **Maximize Shared Cluster Usage**:
   ```java
   // Good: Reuse shared cluster
   String clusterName = getSharedFlexCluster();
   
   // Avoid: Creating unnecessary isolated clusters
   String clusterName = createIsolatedFlexCluster(testInfo); // Only when needed
   ```

2. **Use Flex Clusters When Possible**:
   ```java
   // Good: Use Flex for development/testing
   String clusterName = createIsolatedFlexCluster(testInfo);
   
   // Only when needed: Dedicated clusters for specific requirements
   String clusterName = createIsolatedDedicatedCluster(testInfo);
   ```

3. **Configure Cleanup Appropriately**:
   ```properties
   # Production: Clean up everything
   cleanupIsolatedClusters=true
   
   # Development: Keep for debugging (but monitor costs)
   cleanupIsolatedClusters=false
   ```

## Monitoring and Troubleshooting

### Cluster Status Monitoring

```java
// Check cluster readiness
if (!isClusterReady(clusterName)) {
    logger.warn("Cluster {} not ready, waiting...", clusterName);
    boolean ready = waitForClusterReady(clusterName, Duration.ofMinutes(20));
    assumeTrue(ready, "Cluster failed to become ready within timeout");
}
```

### Cleanup Verification

The `TestClusterManager` provides detailed summaries:

```java
@AfterAll
static void tearDown() {
    if (testClusterManager != null) {
        logger.info(testClusterManager.getClusterSummary());
        // Output shows:
        // - Total managed clusters
        // - Shared vs isolated breakdown
        // - Cleanup results
    }
}
```

### Manual Cleanup

For emergency cleanup or maintenance:

```java
// Clean up all test clusters (use with caution!)
testClusterManager.cleanupTestClusters(".*test.*");

// Clean up only isolated clusters
testClusterManager.cleanupIsolatedClusters();

// Clean up clusters older than X days (future enhancement)
testClusterManager.cleanupByAge(Duration.ofDays(7));
```

## Migration Guide

### Updating Existing Tests

1. **Identify Test Type**:
   - Read-only tests → Use `getSharedFlexCluster()`
   - Destructive tests → Use `createIsolatedCluster(testInfo, ...)`

2. **Remove Manual Cluster Creation**:
   ```java
   // Old approach
   @BeforeEach
   void setup() {
       clusterName = "test-cluster-" + System.currentTimeMillis();
       clustersClient.createCluster(projectId, clusterName, ...);
   }
   
   // New approach
   @Test
   void myTest(TestInfo testInfo) {
       String clusterName = createIsolatedFlexCluster(testInfo);
       // Test continues...
   }
   ```

3. **Remove Manual Cleanup**:
   ```java
   // Old approach
   @AfterEach
   void cleanup() {
       try {
           clustersClient.deleteCluster(projectId, clusterName);
       } catch (Exception e) {
           // Handle cleanup failures
       }
   }
   
   // New approach: Automatic cleanup via TestClusterManager
   // No manual cleanup needed for isolated clusters
   ```

### Backwards Compatibility

The new system is designed to be backwards compatible:

- Existing manual cluster creation still works
- Tests can opt-in to new cluster management gradually
- Legacy cleanup methods remain functional

## Best Practices

1. **Choose the Right Cluster Type**:
   - Shared clusters for read-only, non-destructive tests
   - Isolated clusters for creation, deletion, modification tests

2. **Implement Proper Timeouts**:
   ```java
   // Always verify cluster readiness before testing
   assertTrue(waitForClusterReady(clusterName), 
              "Cluster not ready for testing");
   ```

3. **Use Descriptive Test Names**:
   ```java
   // Good: Descriptive test method names help with cluster naming
   @Test
   void testClusterCreationWithCustomSettings(TestInfo testInfo) { ... }
   
   // Isolated cluster name: isolated-test-mytest-testclustercreationwithcustomsettings-timestamp
   ```

4. **Monitor Resource Usage**:
   - Review cluster summaries in test logs
   - Set up alerts for unexpected cluster costs
   - Regularly audit clusters in Atlas console

5. **Configuration Management**:
   - Use environment-specific configuration files
   - Document any custom configuration requirements
   - Test configuration changes in staging environments

## Future Enhancements

Planned improvements to the cluster management system:

1. **Cross-Project Cluster Discovery**: Find reusable clusters across projects
2. **Advanced Cleanup Scheduling**: Time-based and usage-based cleanup policies
3. **Cost Monitoring Integration**: Real-time cost tracking and alerts
4. **Cluster Pool Management**: Pre-created cluster pools for faster test execution
5. **Cluster Health Monitoring**: Automated cluster health checks and recovery
6. **Integration with CI/CD**: Enhanced integration for different CI environments