# MongoDB Atlas API Client - Setup Guide

This guide will help you configure the Atlas API client for testing and development.

## Quick Start

### 1. Create Atlas API Keys

1. **Log into MongoDB Atlas** (https://cloud.mongodb.com)
2. **Navigate to API Keys**: Organization Settings → Access Manager → API Keys
3. **Create new API Key** with **Project Owner** permissions
4. **Copy the public and private keys** (you'll only see the private key once!)

### 2. Configure Credentials

**Option A: Properties File (Recommended)**

```bash
# Copy the template
cp src/test/resources/atlas-test.properties.template src/test/resources/atlas-test.properties

# Edit the properties file
nano src/test/resources/atlas-test.properties
```

Set your credentials:
```properties
atlas.api.public.key=your_atlas_api_public_key_here
atlas.api.private.key=your_atlas_api_private_key_here
```

**Option B: Environment Variables**

```bash
export ATLAS_API_PUBLIC_KEY="your_public_key"
export ATLAS_API_PRIVATE_KEY="your_private_key"
```

### 3. Validate Configuration

```bash
# Check if your configuration is working
mvn compile exec:java -Dexec.mainClass="com.mongodb.atlas.api.config.ConfigurationValidator"

# Or get setup instructions
mvn compile exec:java -Dexec.mainClass="com.mongodb.atlas.api.config.ConfigurationValidator" -Dexec.args="setup"
```

### 4. Run Tests

```bash
# Test basic functionality
mvn test -Dtest=AtlasProjectsClientIntegrationTest

# Run all tests
mvn test
```

## Configuration Options

### Required Settings

| Property | Environment Variable | Description |
|----------|---------------------|-------------|
| `atlas.api.public.key` | `ATLAS_API_PUBLIC_KEY` | Your Atlas API public key |
| `atlas.api.private.key` | `ATLAS_API_PRIVATE_KEY` | Your Atlas API private key |

### Optional Settings

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `atlas.test.project.id` | `ATLAS_TEST_PROJECT_ID` | _(first available)_ | Specific project for testing |
| `atlas.test.org.id` | `ATLAS_TEST_ORG_ID` | _(none)_ | Organization for project creation tests |
| `atlas.test.region` | - | `US_EAST_1` | Default region for test clusters |
| `atlas.test.cloud.provider` | - | `AWS` | Default cloud provider |
| `atlas.test.mongo.version` | - | `7.0` | Default MongoDB version |
| `atlas.debug.level` | - | `0` | Debug logging level (0-2) |
| `atlas.rate.limit.enabled` | - | `true` | Enable rate limiting protection |

## Example Configurations

### Development Setup
```properties
# Basic development configuration
atlas.api.public.key=abcdef123456
atlas.api.private.key=12345678-1234-1234-1234-123456789abc
atlas.test.project.id=507f1f77bcf86cd799439011
atlas.debug.level=1
```

### CI/CD Setup
```properties
# Production-like CI/CD configuration
atlas.api.public.key=${ATLAS_API_PUBLIC_KEY}
atlas.api.private.key=${ATLAS_API_PRIVATE_KEY}
atlas.test.project.id=${ATLAS_TEST_PROJECT_ID}
atlas.rate.limit.enabled=true
atlas.debug.level=0
```

### Multi-Cloud Testing
```properties
# Test different cloud providers
atlas.test.cloud.provider=GCP
atlas.test.region=US_CENTRAL1_A
atlas.test.mongo.version=6.0
```

## Configuration Precedence

The configuration system loads settings in this order (later values override earlier ones):

1. **Properties file** (`atlas-test.properties`)
2. **Environment variables** (`ATLAS_API_PUBLIC_KEY`, etc.)
3. **System properties** (command line `-D` flags)

### Example Using System Properties
```bash
mvn test -Datlas.test.region=EU_WEST_1 -Datlas.debug.level=2
```

## Security Best Practices

### 🔒 Protecting Your API Keys

1. **Never commit API keys to version control**
   ```bash
   # Add to .gitignore
   echo "src/test/resources/atlas-test.properties" >> .gitignore
   ```

2. **Use environment variables in CI/CD**
   ```yaml
   # GitHub Actions example
   env:
     ATLAS_API_PUBLIC_KEY: ${{ secrets.ATLAS_API_PUBLIC_KEY }}
     ATLAS_API_PRIVATE_KEY: ${{ secrets.ATLAS_API_PRIVATE_KEY }}
   ```

3. **Rotate keys regularly** in Atlas console

4. **Use minimal permissions** - "Project Read Only" for read-only tests

### 🎯 Test Safety

- Tests use **test-specific naming** with timestamps
- **Automatic cleanup** prevents resource accumulation
- **M0/Flex clusters** minimize costs
- **Test IP ranges** (RFC5737) for network tests

## Troubleshooting

### Common Issues

**❌ "Atlas API credentials not provided"**
```bash
# Check if configuration is loaded
mvn compile exec:java -Dexec.mainClass="com.mongodb.atlas.api.config.ConfigurationValidator"
```

**❌ "No Atlas projects available for testing"**
- Ensure your API key has access to at least one project
- Or set `atlas.test.project.id` to a specific project

**❌ "403 Forbidden" errors**
- Check API key permissions (need "Project Owner" for full testing)
- Verify the API key hasn't expired

**❌ "Connection timeout" errors**
- Check network connectivity to `cloud.mongodb.com`
- Verify firewall settings

### Debug Mode

Enable detailed logging:
```properties
atlas.debug.level=2
```

Or via command line:
```bash
mvn test -Datlas.debug.level=2 -Dtest=AtlasProjectsClientIntegrationTest
```

### Test Reports

Maven generates detailed test reports:
- **Console output**: Real-time test progress
- **Surefire reports**: `target/surefire-reports/`
- **Logs**: Check for Atlas API request/response details

## Advanced Configuration

### Custom Properties File Location

```bash
# Use a different properties file
mvn test -Datlas.config.file=/path/to/custom.properties
```

### Profile-Based Configuration

Create multiple configuration files for different environments:

```bash
# Development
cp atlas-test.properties.template atlas-dev.properties

# Staging
cp atlas-test.properties.template atlas-staging.properties

# Use specific profile
mvn test -Datlas.config.file=atlas-dev.properties
```

### Integration with IDEs

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Add VM options: `-Datlas.debug.level=1`
3. Add environment variables in "Environment variables" section

**VS Code:**
Add to `.vscode/settings.json`:
```json
{
    "java.test.config": {
        "vmArgs": ["-Datlas.debug.level=1"],
        "env": {
            "ATLAS_API_PUBLIC_KEY": "your_key",
            "ATLAS_API_PRIVATE_KEY": "your_secret"
        }
    }
}
```

## Support

If you encounter issues:

1. **Validate configuration** with the ConfigurationValidator
2. **Check test reports** in `target/surefire-reports/`
3. **Enable debug logging** with `atlas.debug.level=2`
4. **Review MongoDB Atlas documentation** for API changes
5. **Check network connectivity** to Atlas API endpoints

## Next Steps

Once configured:
- Review the [Testing Guide](README-TESTING.md) for detailed test information
- Explore the [API Documentation](src/main/java/com/mongodb/atlas/api/clients/) for client usage
- Run the full test suite to verify everything works