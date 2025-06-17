# Atlas Programmatic API Keys Management Guide

This guide covers how to use the `AtlasProgrammaticAPIKeysClient` to manage MongoDB Atlas programmatic API keys.

## Overview

Programmatic API keys provide secure, programmatic access to Atlas resources. They can be scoped to:
- **Organization level**: Access to all projects within an organization
- **Project level**: Access to specific projects only

## Quick Start

### Setup

```java
import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProgrammaticAPIKeysClient;
import com.mongodb.atlas.api.config.AtlasTestConfig;

// Initialize the client
AtlasTestConfig config = AtlasTestConfig.getInstance();
AtlasApiBase apiBase = new AtlasApiBase(
    config.getApiPublicKey(),
    config.getApiPrivateKey()
);
AtlasProgrammaticAPIKeysClient apiKeysClient = new AtlasProgrammaticAPIKeysClient(apiBase);
```

### Configuration

Add to your `atlas-test.properties`:
```properties
apiPublicKey=your_admin_api_public_key
apiPrivateKey=your_admin_api_private_key
testOrgId=your_organization_id
testProjectId=your_project_id
```

## Core Operations

### 1. List Organization API Keys

```java
String orgId = "your-org-id";
List<Map<String, Object>> apiKeys = apiKeysClient.getOrganizationAPIKeys(orgId);

for (Map<String, Object> apiKey : apiKeys) {
    System.out.println("API Key ID: " + apiKey.get("id"));
    System.out.println("Description: " + apiKey.get("desc"));
    System.out.println("Roles: " + apiKey.get("roles"));
}
```

### 2. Create a New API Key

```java
String orgId = "your-org-id";
String description = "API Key for automated monitoring";
List<String> roles = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY
);

Map<String, Object> response = apiKeysClient.createOrganizationAPIKey(orgId, description, roles);

String apiKeyId = (String) response.get("id");
String publicKey = (String) response.get("publicKey");
String privateKey = (String) response.get("privateKey");

// IMPORTANT: Save the private key immediately - it cannot be retrieved again!
System.out.println("New API Key Created:");
System.out.println("ID: " + apiKeyId);
System.out.println("Public Key: " + publicKey);
System.out.println("Private Key: " + privateKey);
```

### 3. Get API Key Details

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";

Map<String, Object> apiKey = apiKeysClient.getOrganizationAPIKey(orgId, apiKeyId);
System.out.println("API Key Description: " + apiKey.get("desc"));
System.out.println("Roles: " + apiKey.get("roles"));
```

### 4. Update API Key

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";
String newDescription = "Updated API Key for CI/CD";
List<String> newRoles = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY,
    AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_GROUP_CREATOR
);

Map<String, Object> response = apiKeysClient.updateOrganizationAPIKey(
    orgId, apiKeyId, newDescription, newRoles);
```

### 5. Delete API Key

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";

Map<String, Object> response = apiKeysClient.deleteOrganizationAPIKey(orgId, apiKeyId);
System.out.println("API Key deleted successfully");
```

## Access List Management (IP Whitelisting)

### Add IP Addresses to Access List

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";
List<String> accessListEntries = Arrays.asList(
    "192.168.1.100",           // Single IP
    "10.0.0.0/24",            // CIDR block
    "172.16.0.0/16"           // Another CIDR block
);

Map<String, Object> response = apiKeysClient.createAPIKeyAccessList(
    orgId, apiKeyId, accessListEntries);
```

### Get Access List

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";

List<Map<String, Object>> accessList = apiKeysClient.getAPIKeyAccessList(orgId, apiKeyId);

for (Map<String, Object> entry : accessList) {
    System.out.println("Entry: " + entry.get("ipAddress") + " / " + entry.get("cidrBlock"));
}
```

### Remove IP from Access List

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";
String ipToRemove = "192.168.1.100";

Map<String, Object> response = apiKeysClient.deleteAPIKeyAccessListEntry(
    orgId, apiKeyId, ipToRemove);
```

## Available Roles

### Organization Roles

```java
// Available organization-level roles
AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_OWNER              // Full organization access
AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_MEMBER             // Standard member access
AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_GROUP_CREATOR      // Can create projects
AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_BILLING_ADMIN      // Billing management
AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY          // Read-only access
```

### Project Roles

```java
// Available project-level roles
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_OWNER                 // Full project access
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_CLUSTER_MANAGER       // Cluster management
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_DATA_ACCESS_ADMIN     // Database user management
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_DATA_ACCESS_READ_ONLY // Read-only database access
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_DATA_ACCESS_READ_WRITE// Read-write database access
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_MONITORING_ADMIN      // Monitoring configuration
AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_READ_ONLY             // Read-only project access
```

## Project Assignment

### Assign API Key to Specific Projects

```java
String orgId = "your-org-id";
String apiKeyId = "your-api-key-id";

// Create project assignments
List<Map<String, Object>> projectAssignments = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.createProjectAssignment(
        "project-1-id", 
        Arrays.asList(AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_READ_ONLY)
    ),
    AtlasProgrammaticAPIKeysClient.createProjectAssignment(
        "project-2-id", 
        Arrays.asList(AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_CLUSTER_MANAGER)
    )
);

Map<String, Object> response = apiKeysClient.assignAPIKeyToProjects(
    orgId, apiKeyId, projectAssignments);
```

## Common Use Cases

### 1. Monitoring API Key

```java
// Create read-only API key for monitoring tools
String description = "Monitoring API Key - Prometheus/Grafana";
List<String> roles = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_READ_ONLY
);

Map<String, Object> response = apiKeysClient.createOrganizationAPIKey(
    orgId, description, roles);

// Restrict to monitoring server IPs
List<String> monitoringIPs = Arrays.asList("10.1.1.100", "10.1.1.101");
apiKeysClient.createAPIKeyAccessList(orgId, apiKeyId, monitoringIPs);
```

### 2. CI/CD API Key

```java
// Create API key for CI/CD pipeline
String description = "CI/CD Pipeline API Key";
List<String> roles = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.OrganizationRoles.ORG_GROUP_CREATOR,
    AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_CLUSTER_MANAGER
);

Map<String, Object> response = apiKeysClient.createOrganizationAPIKey(
    orgId, description, roles);

// Restrict to CI/CD server IP range
List<String> cicdIPs = Arrays.asList("192.168.100.0/24");
apiKeysClient.createAPIKeyAccessList(orgId, apiKeyId, cicdIPs);
```

### 3. Developer API Key

```java
// Create project-specific API key for development
String description = "Development API Key - Project Alpha";
List<String> roles = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.ProjectRoles.GROUP_DATA_ACCESS_READ_WRITE
);

Map<String, Object> response = apiKeysClient.createOrganizationAPIKey(
    orgId, description, roles);

// Assign only to specific project
List<Map<String, Object>> projectAssignments = Arrays.asList(
    AtlasProgrammaticAPIKeysClient.createProjectAssignment(
        "dev-project-id", roles)
);
apiKeysClient.assignAPIKeyToProjects(orgId, apiKeyId, projectAssignments);
```

## Security Best Practices

1. **Store Private Keys Securely**: Never log or store private keys in plain text
2. **Use Least Privilege**: Assign minimal necessary roles
3. **Implement IP Restrictions**: Always use access lists to limit API key usage
4. **Regular Rotation**: Periodically rotate API keys
5. **Monitor Usage**: Track API key usage through Atlas audit logs
6. **Delete Unused Keys**: Remove API keys that are no longer needed

## Error Handling

```java
try {
    Map<String, Object> response = apiKeysClient.createOrganizationAPIKey(
        orgId, description, roles);
    // Handle success
} catch (AtlasApiBase.AtlasApiException e) {
    logger.error("Failed to create API key: {}", e.getMessage());
    // Handle specific Atlas API errors
} catch (Exception e) {
    logger.error("Unexpected error: {}", e.getMessage());
    // Handle other errors
}
```

## Testing

Run the integration tests:

```bash
mvn test -Dtest=AtlasProgrammaticAPIKeysClientTest
```

Make sure your `atlas-test.properties` includes:
- `testOrgId`: Your Atlas organization ID
- Valid admin API credentials with organization-level permissions

## API Reference

For complete API documentation, see:
- [MongoDB Atlas Administration API - Programmatic API Keys](https://www.mongodb.com/docs/atlas/reference/api-resources-spec/v2/#tag/Programmatic-API-Keys)