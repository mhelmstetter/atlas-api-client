# Atlas Cluster Creation Integration Guide

This guide shows how to integrate real Atlas cluster creation into the mongo-launcher project.

## Overview

The atlas-api-client has been enhanced with cluster creation capabilities. The mongo-launcher can now use these APIs to create real Atlas clusters instead of returning stubbed responses.

## Enhanced Features Added

### 1. AtlasClustersClient - New Cluster Creation Methods

```java
// Create a new Atlas cluster
public Map<String, Object> createCluster(String projectId, String clusterName, 
                                       String instanceSize, String mongoVersion, 
                                       String region, String cloudProvider)

// Get cluster status by name
public Map<String, Object> getCluster(String projectId, String clusterName)

// Wait for cluster to reach target state
public boolean waitForClusterState(String projectId, String clusterName, 
                                 String targetState, int timeoutSeconds)
```

### 2. AtlasApiBase - Enhanced HTTP Support

```java
// New method supporting POST, PUT, DELETE operations
protected String makeApiRequest(String url, HttpMethod method, String requestBody, 
                              String acceptHeader, String projectId)
```

### 3. Interactive Atlas Cluster Launcher Demo

A complete working example at `src/main/java/com/mongodb/atlas/api/launcher/AtlasClusterLauncher.java` demonstrates:
- Interactive credential prompting
- Real Atlas cluster creation
- Cluster status monitoring
- Connection string retrieval

## Integration Steps for mongo-launcher

### Step 1: Update Dependencies

Ensure mongo-launcher uses the updated atlas-client:

```xml
<dependency>
    <groupId>com.mongodb</groupId>
    <artifactId>atlas-client</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Enhance InteractivePrompt.java

Add methods to prompt for Atlas API credentials:

```java
public String promptForApiPublicKey() {
    return promptForInput("Atlas API Public Key", null);
}

public String promptForApiPrivateKey() {
    return promptForInput("Atlas API Private Key", null);
}

public String promptForCloudProvider() {
    String[] providers = {"AWS", "GCP", "AZURE"};
    return promptForChoice("Cloud Provider:", providers, "AWS");
}

public String promptForRegion() {
    String[] regions = {"US_EAST_1", "US_WEST_2", "EU_WEST_1", "AP_SOUTHEAST_1"};
    return promptForChoice("Region:", regions, "US_EAST_1");
}
```

### Step 3: Update AtlasClusterLauncher.java

Replace the stubbed implementation with real Atlas API calls:

```java
package com.mongodb.launcher.atlas;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.launcher.*;
import java.util.Map;

public class AtlasClusterLauncher implements ClusterLauncher {
    
    @Override
    public ClusterInstance launch(ClusterSpec spec) throws ClusterLaunchException {
        try {
            AtlasClusterSpec atlasSpec = (AtlasClusterSpec) spec;
            
            // Initialize Atlas API client with user credentials
            AtlasApiClient apiClient = new AtlasApiClient(
                atlasSpec.getApiPublicKey(), 
                atlasSpec.getApiPrivateKey()
            );
            
            // Create the cluster
            Map<String, Object> clusterResponse = apiClient.clusters().createCluster(
                atlasSpec.getProjectId(),
                atlasSpec.getName(),
                atlasSpec.getInstanceSize(),
                atlasSpec.getMongoVersion(),
                atlasSpec.getRegion(),
                atlasSpec.getCloudProvider()
            );
            
            // Wait for cluster to be ready (optional, configurable)
            if (atlasSpec.isWaitForReady()) {
                boolean isReady = apiClient.clusters().waitForClusterState(
                    atlasSpec.getProjectId(), 
                    atlasSpec.getName(), 
                    "IDLE", 
                    900 // 15 minute timeout
                );
                
                if (!isReady) {
                    throw new ClusterLaunchException("Timeout waiting for cluster to be ready");
                }
            }
            
            // Get final cluster details
            Map<String, Object> finalCluster = apiClient.clusters().getCluster(
                atlasSpec.getProjectId(), 
                atlasSpec.getName()
            );
            
            // Return real cluster instance
            return new AtlasClusterInstance(
                (String) finalCluster.get("id"),
                atlasSpec.getName(),
                (String) finalCluster.get("stateName"),
                extractConnectionString(finalCluster)
            );
            
        } catch (Exception e) {
            throw new ClusterLaunchException("Failed to launch Atlas cluster: " + e.getMessage(), e);
        }
    }
    
    private String extractConnectionString(Map<String, Object> cluster) {
        // Extract connection string from cluster response
        Map<String, Object> connectionStrings = (Map<String, Object>) cluster.get("connectionStrings");
        if (connectionStrings != null) {
            return (String) connectionStrings.get("standardSrv");
        }
        return "mongodb+srv://" + cluster.get("name") + ".mongodb.net/";
    }
}
```

### Step 4: Update AtlasClusterSpec.java

Add fields for API credentials and cloud configuration:

```java
public class AtlasClusterSpec extends ClusterSpec {
    private String apiPublicKey;
    private String apiPrivateKey;
    private String projectId;
    private String instanceSize = "M10";
    private String cloudProvider = "AWS";
    private String region = "US_EAST_1";
    private boolean waitForReady = true;
    
    // Getters and setters...
}
```

### Step 5: Update LaunchCommand.java

Modify the `createAtlasSpec` method to gather API credentials:

```java
private AtlasClusterSpec createAtlasSpec(String clusterName, String version, 
                                       ConfigManager configManager, InteractivePrompt prompt, boolean interactive) {
    AtlasClusterSpec spec = new AtlasClusterSpec(clusterName, version);
    
    // API Credentials
    String apiPublicKey = configManager.getApiPublicKey();
    if (apiPublicKey == null && interactive) {
        apiPublicKey = prompt.promptForApiPublicKey();
    }
    if (apiPublicKey == null) {
        throw new IllegalArgumentException("Atlas API public key is required");
    }
    spec.setApiPublicKey(apiPublicKey);
    
    String apiPrivateKey = configManager.getApiPrivateKey();
    if (apiPrivateKey == null && interactive) {
        apiPrivateKey = prompt.promptForApiPrivateKey();
    }
    if (apiPrivateKey == null) {
        throw new IllegalArgumentException("Atlas API private key is required");
    }
    spec.setApiPrivateKey(apiPrivateKey);
    
    // Project ID (existing logic)
    String atlasProjectId = projectId != null ? projectId : configManager.getDefaultAtlasProjectId();
    if (atlasProjectId == null && interactive) {
        atlasProjectId = prompt.promptForInput("Atlas Project ID", null);
    }
    if (atlasProjectId == null) {
        throw new IllegalArgumentException("Atlas project ID is required");
    }
    spec.setProjectId(atlasProjectId);
    
    // Cloud configuration
    if (interactive) {
        String cloudProvider = prompt.promptForCloudProvider();
        String region = prompt.promptForRegion();
        spec.setCloudProvider(cloudProvider);
        spec.setRegion(region);
    }
    
    // Instance size (existing logic)
    String size = instanceSize != null ? instanceSize : configManager.getConfig().getDefaultInstanceSize();
    if (interactive && instanceSize == null) {
        String[] sizes = {"M0", "M2", "M5", "M10", "M20", "M30", "M40", "M50"};
        size = prompt.promptForChoice("Instance size:", sizes, size);
    }
    spec.setInstanceSize(size);
    
    return spec;
}
```

## Testing the Integration

1. **Run the demo launcher:**
   ```bash
   ./launch-cluster.sh
   ```

2. **Test interactive mode:**
   ```bash
   mongo-launcher launch
   ```
   - Should prompt for Atlas API credentials
   - Should prompt for cloud provider and region
   - Should create real Atlas clusters

3. **Test with config file:**
   ```bash
   mongo-launcher config set-api-key <public-key> <private-key>
   mongo-launcher launch cluster-spec.json
   ```

## Security Considerations

1. **API Key Storage:**
   - Store API keys securely in user's config directory
   - Consider using OS keychain integration
   - Never log API keys in plain text

2. **Input Validation:**
   - Validate all user inputs before making API calls
   - Handle API authentication failures gracefully
   - Provide clear error messages for invalid configurations

## Error Handling

The integration should handle these common scenarios:

1. **Invalid API credentials**
2. **Network connectivity issues**
3. **Atlas API rate limiting**
4. **Cluster creation failures**
5. **Timeout waiting for cluster ready state**

## Configuration Management

Add these configuration options to ConfigManager:

```java
public class ConfigManager {
    // Existing fields...
    
    private String apiPublicKey;
    private String apiPrivateKey;
    private String defaultCloudProvider = "AWS";
    private String defaultRegion = "US_EAST_1";
    private boolean waitForClusterReady = true;
    private int clusterTimeoutSeconds = 900; // 15 minutes
    
    // Getters and setters...
}
```

This integration transforms mongo-launcher from a demonstration tool into a fully functional Atlas cluster management platform with real cluster creation capabilities.