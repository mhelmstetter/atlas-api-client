# Atlas CLI - Comprehensive Command-Line Interface

The Atlas CLI provides a unified command-line interface for all MongoDB Atlas operations, exposing the complete Atlas API functionality through an intuitive command structure.

## 🚀 Quick Start

### Installation & Setup

1. **Build the CLI**:
   ```bash
   mvn clean package
   ```

2. **Configure Credentials** (create `atlas-client.properties`):
   ```properties
   apiPublicKey=your_atlas_api_public_key
   apiPrivateKey=your_atlas_api_private_key
   testProjectId=your_default_project_id
   testOrgId=your_organization_id
   ```

3. **Run the CLI**:
   ```bash
   java -jar bin/AtlasClient.jar --help
   # or use the new CLI main class:
   java -cp bin/AtlasClient.jar com.mongodb.atlas.api.cli.AtlasCliMain --help
   ```

## 📋 Available Commands

### Core Management Operations

#### 🏗️ **Cluster Management** (`clusters`)
Manage dedicated Atlas clusters (M10+ instances):

```bash
# List all clusters
atlas-cli clusters list --project <project-id>

# Get cluster details
atlas-cli clusters get my-cluster --project <project-id>

# Create a new cluster
atlas-cli clusters create my-new-cluster \
    --size M10 \
    --version 7.0 \
    --region US_EAST_1 \
    --provider AWS \
    --wait

# Update cluster configuration
atlas-cli clusters update my-cluster \
    --size M20 \
    --disk-size 20 \
    --enable-backup true

# Delete a cluster
atlas-cli clusters delete my-cluster --force

# Monitor cluster status
atlas-cli clusters status my-cluster --wait IDLE --timeout 1800
```

#### ⚡ **Flex Clusters** (`flex-clusters`)
Manage serverless Flex clusters (pay-as-you-go):

```bash
# List Flex clusters
atlas-cli flex-clusters list --project <project-id>

# Create a Flex cluster
atlas-cli flex-clusters create my-flex-cluster \
    --region US_WEST_2 \
    --provider AWS \
    --wait

# Get Flex cluster status
atlas-cli flex-clusters status my-flex-cluster

# Delete Flex cluster
atlas-cli flex-clusters delete my-flex-cluster
```

#### 🔑 **API Key Management** (`api-keys`)
Manage programmatic API keys:

```bash
# List all API keys
atlas-cli api-keys list --org <org-id>

# Create a new API key
atlas-cli api-keys create "My Monitoring Key" \
    --roles ORG_READ_ONLY \
    --access-list "192.168.1.100,10.0.0.0/24"

# Get API key details
atlas-cli api-keys get <api-key-id> --org <org-id>

# Update API key
atlas-cli api-keys update <api-key-id> \
    --description "Updated description" \
    --roles ORG_READ_ONLY,ORG_GROUP_CREATOR

# Manage access lists (IP whitelisting)
atlas-cli api-keys access-list <api-key-id> --list
atlas-cli api-keys access-list <api-key-id> --add "203.0.113.0/24"
atlas-cli api-keys access-list <api-key-id> --remove "192.168.1.100"

# Assign API key to specific projects
atlas-cli api-keys assign <api-key-id> \
    --project <project-id> \
    --roles GROUP_READ_ONLY,GROUP_CLUSTER_MANAGER

# Delete API key
atlas-cli api-keys delete <api-key-id> --force
```

### Coming Soon 🚧

The following commands are planned and will provide full Atlas API coverage:

- **`database-users`** - Database user management
- **`network-access`** - IP whitelisting and VPC configuration
- **`backups`** - Backup and restore operations
- **`projects`** - Project CRUD operations
- **`metrics`** - Metrics collection and analysis
- **`logs`** - Database and audit log access

## 🎛️ Global Options

### Authentication & Configuration

```bash
# Override configuration file
atlas-cli --config /path/to/custom.properties clusters list

# Override individual credentials
atlas-cli --api-public-key <key> --api-private-key <key> clusters list

# Override project/organization
atlas-cli --project-id <id> --org-id <id> clusters list
```

### Output Formatting

```bash
# Table format (default)
atlas-cli clusters list --format table

# JSON output
atlas-cli clusters list --format json

# CSV export
atlas-cli clusters list --format csv
```

### Debugging & Verbose Output

```bash
# Enable verbose output
atlas-cli --verbose clusters create my-cluster

# Show detailed error information
atlas-cli --verbose api-keys create "Test Key" --roles INVALID_ROLE
```

## 🖥️ Interactive Mode

Launch interactive mode for guided operations:

```bash
# Start interactive mode
atlas-cli --interactive

# Or use the interactive command
atlas-cli interactive
```

Interactive mode provides a menu-driven interface:

```
🍃 MongoDB Atlas CLI - Interactive Mode
=====================================

Available Commands:
  1. Cluster Management
  2. API Key Management  
  3. Database Users
  4. Network Access
  5. Backups & Restore
  6. Projects
  7. Metrics & Monitoring
  8. Logs
  9. Configuration
  0. Exit

Select an option [0-9]:
```

## ⚙️ Configuration Management

### Configuration File Priority

1. **Command-line options** (highest priority)
2. **Environment variables**
3. **Properties file**
4. **Default values** (lowest priority)

### Properties File Format

Create `atlas-client.properties` in your working directory:

```properties
# Required API credentials
apiPublicKey=your_atlas_api_public_key
apiPrivateKey=your_atlas_api_private_key

# Default project and organization
testProjectId=your_default_project_id
testOrgId=your_organization_id

# Optional defaults
testRegion=US_EAST_1
testCloudProvider=AWS
testMongoVersion=7.0
debugLevel=0
```

### Environment Variables

```bash
export ATLAS_API_PUBLIC_KEY="your_public_key"
export ATLAS_API_PRIVATE_KEY="your_private_key"
export ATLAS_TEST_PROJECT_ID="your_project_id"
export ATLAS_TEST_ORG_ID="your_org_id"
```

## 📖 Command Examples

### Complete Cluster Lifecycle

```bash
# 1. Create a production cluster
atlas-cli clusters create production-app \
    --size M30 \
    --version 7.0 \
    --region US_EAST_1 \
    --provider AWS \
    --wait

# 2. Monitor cluster status
atlas-cli clusters status production-app

# 3. Scale up for peak traffic
atlas-cli clusters update production-app --size M40

# 4. Enable backup
atlas-cli clusters update production-app --enable-backup true

# 5. Get connection details
atlas-cli clusters get production-app --format json | jq '.connectionStrings'
```

### API Key Management Workflow

```bash
# 1. Create monitoring API key
atlas-cli api-keys create "Production Monitoring" \
    --roles ORG_READ_ONLY \
    --access-list "10.0.0.0/8,172.16.0.0/12"

# 2. Create development API key
atlas-cli api-keys create "Development Access" \
    --roles ORG_MEMBER,ORG_GROUP_CREATOR

# 3. Assign development key to specific project
atlas-cli api-keys assign <dev-key-id> \
    --project <dev-project-id> \
    --roles GROUP_CLUSTER_MANAGER,GROUP_DATA_ACCESS_READ_WRITE

# 4. List all keys to verify
atlas-cli api-keys list --format table
```

### Flex Cluster for Development

```bash
# 1. Create cost-effective Flex cluster
atlas-cli flex-clusters create dev-sandbox \
    --region US_WEST_2 \
    --provider AWS \
    --wait

# 2. Check status
atlas-cli flex-clusters status dev-sandbox

# 3. Clean up when done
atlas-cli flex-clusters delete dev-sandbox --force
```

## 🔧 Advanced Usage

### Batch Operations with Shell Scripts

```bash
#!/bin/bash
# Create multiple clusters for different environments

ENVIRONMENTS=("dev" "staging" "prod")
SIZES=("M10" "M20" "M30")

for i in "${!ENVIRONMENTS[@]}"; do
    ENV="${ENVIRONMENTS[$i]}"
    SIZE="${SIZES[$i]}"
    
    echo "Creating $ENV cluster with size $SIZE..."
    atlas-cli clusters create "app-$ENV" \
        --size "$SIZE" \
        --version 7.0 \
        --region US_EAST_1 \
        --provider AWS &
done

wait
echo "All clusters created!"
```

### JSON Processing with jq

```bash
# Get all cluster names
atlas-cli clusters list --format json | jq -r '.[].name'

# Find clusters by size
atlas-cli clusters list --format json | jq '.[] | select(.instanceSizeName == "M10")'

# Extract connection strings
atlas-cli clusters get my-cluster --format json | \
    jq -r '.connectionStrings.standardSrv'
```

### Integration with CI/CD

```bash
# GitLab CI example
deploy_cluster:
  script:
    - atlas-cli clusters create "$CI_ENVIRONMENT_NAME-app" \
        --size M10 \
        --version 7.0 \
        --wait
    - CONNECTION_STRING=$(atlas-cli clusters get "$CI_ENVIRONMENT_NAME-app" \
        --format json | jq -r '.connectionStrings.standardSrv')
    - echo "CONNECTION_STRING=$CONNECTION_STRING" >> deploy.env
  artifacts:
    reports:
      dotenv: deploy.env
```

## 🆘 Error Handling & Troubleshooting

### Common Issues

1. **Authentication Errors**:
   ```bash
   ❌ Error: Invalid API credentials
   ```
   - Verify your API keys in the configuration
   - Check that keys have necessary permissions

2. **Project Not Found**:
   ```bash
   ❌ Error: Project ID is required
   ```
   - Set `testProjectId` in configuration
   - Use `--project` flag to specify project

3. **Rate Limiting**:
   ```bash
   ❌ Error: Rate limit exceeded
   ```
   - The CLI automatically handles rate limiting
   - Use `--verbose` to see rate limit status

### Debug Mode

Enable verbose logging for troubleshooting:

```bash
atlas-cli --verbose clusters create test-cluster
```

This shows:
- API request details
- Response times
- Rate limiting information
- Detailed error messages

## 🔄 Legacy Compatibility

The CLI maintains backward compatibility with existing tools:

```bash
# Legacy metrics analyzer
atlas-cli legacy-metrics-analyzer

# Legacy cluster launcher  
atlas-cli legacy-cluster-launcher

# Direct execution (still supported)
java -cp atlas-client.jar com.mongodb.atlas.api.AtlasMetricsAnalyzer --help
```

## 🛠️ Build Configuration

To use the new CLI as the default main class, update `pom.xml`:

```xml
<transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
    <mainClass>com.mongodb.atlas.api.cli.AtlasCliMain</mainClass>
</transformer>
```

## 📚 Additional Resources

- **API Documentation**: [MongoDB Atlas Administration API](https://docs.atlas.mongodb.com/reference/api-resources-spec/)
- **API Keys Guide**: See `API-KEYS-GUIDE.md` in this repository
- **Cluster Management**: See `CLUSTER-MANAGEMENT.md` in this repository
- **Atlas Documentation**: [MongoDB Atlas Docs](https://docs.atlas.mongodb.com/)

## 🤝 Contributing

The CLI is modular and extensible. To add new commands:

1. Create a new command class in `src/main/java/com/mongodb/atlas/api/cli/commands/`
2. Add the command to the `subcommands` list in `AtlasCliMain.java`
3. Follow the existing patterns for error handling and output formatting

## 📋 Roadmap

- ✅ Cluster management (regular and Flex)
- ✅ API key management
- ✅ Output formatting (JSON, CSV, Table)
- ✅ Interactive mode foundation
- 🚧 Database user management
- 🚧 Network access configuration
- 🚧 Backup and restore operations
- 🚧 Metrics integration
- 🚧 Shell completion
- 🚧 Configuration validation

---

*The Atlas CLI provides comprehensive command-line access to MongoDB Atlas operations, making it easy to automate, script, and integrate Atlas management into your workflows.*