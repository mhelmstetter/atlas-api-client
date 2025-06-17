# Atlas Interactive Cluster Launcher

A standalone tool for creating and managing MongoDB Atlas clusters with an interactive command-line interface.

## Quick Start

### Prerequisites
- Java 17 or higher
- Atlas API credentials (Public Key and Private Key)
- Atlas Project ID

### Running the Launcher

**macOS/Linux:**
```bash
./atlas-launcher
```

**Windows:**
```cmd
atlas-launcher.bat
```

**Alternative (any platform):**
```bash
java -cp bin/AtlasClient.jar com.mongodb.atlas.api.launcher.AtlasClusterLauncher
```

## Features

### Interactive Cluster Creation
- **Credential Prompting**: Securely enter Atlas API keys
- **Project Selection**: Specify your Atlas project ID
- **Cluster Configuration**: Choose instance size, MongoDB version, cloud provider, and region
- **Real-time Status**: Monitor cluster creation progress
- **Connection Strings**: Get actual cluster connection details

### Supported Configurations

**Instance Sizes:**
- M0 (Free Tier)
- M2, M5, M10, M20, M30 (Dedicated)

**MongoDB Versions:**
- 6.0, 7.0, 8.0

**Cloud Providers:**
- AWS, Google Cloud (GCP), Microsoft Azure

**Regions:**
- US_EAST_1, US_WEST_2, EU_WEST_1, AP_SOUTHEAST_1

## Usage Example

```
🍃 Welcome to MongoDB Atlas Cluster Launcher
============================================

Atlas API Public Key: [enter your public key]
Atlas API Private Key: [enter your private key]
🔑 Authenticating with Atlas API...
✅ Successfully connected to Atlas API

Atlas Project ID: [enter your project ID]

📋 Cluster Configuration
------------------------
Cluster Name [test-cluster]: my-new-cluster

Instance Size:
  1. M0 (default)
  2. M2
  3. M5
  4. M10
  5. M20
  6. M30
Enter choice (1-6) [M0]: 4

MongoDB Version:
  1. 6.0
  2. 7.0 (default)
  3. 8.0
Enter choice (1-3) [7.0]: 

Cloud Provider:
  1. AWS (default)
  2. GCP
  3. AZURE
Enter choice (1-3) [AWS]: 

Region:
  1. US_EAST_1 (default)
  2. US_WEST_2
  3. EU_WEST_1
  4. AP_SOUTHEAST_1
Enter choice (1-4) [US_EAST_1]: 

📊 Cluster Summary
------------------
Name: my-new-cluster
Instance Size: M10
MongoDB Version: 7.0
Cloud Provider: AWS
Region: US_EAST_1
Project ID: 507f1f77bcf86cd799439011

Proceed with cluster creation? [Y/n]: Y

🚀 Creating Atlas cluster 'my-new-cluster'...
This may take several minutes...
✅ Cluster creation initiated successfully!
Cluster ID: 507f1f77bcf86cd799439012
Status: CREATING

Wait for cluster to be ready? [Y/n]: Y
⏳ Waiting for cluster to reach IDLE state...
This typically takes 7-10 minutes for new clusters.
🎉 Cluster is ready!
Connection String: mongodb+srv://my-new-cluster.abc123.mongodb.net/

✅ Atlas launcher session complete!
```

## Getting Atlas API Credentials

1. Log in to [MongoDB Atlas](https://cloud.mongodb.com)
2. Go to **Project Settings** → **Access Manager** → **API Keys**
3. Click **Create API Key**
4. Choose appropriate permissions (Project Data Access Admin recommended)
5. Copy the **Public Key** and **Private Key**
6. Whitelist your IP address for API access

## Building from Source

```bash
# Clone the repository
git clone https://github.com/mhelmstetter/atlas-api-client.git
cd atlas-api-client

# Build the project
mvn clean package -DskipTests

# Run the launcher
./atlas-launcher
```

## Integration with mongo-launcher

This enhanced atlas-api-client can be integrated with the mongo-launcher project to replace stubbed cluster creation with real Atlas API calls. See `INTEGRATION-GUIDE.md` for detailed instructions.

## Error Handling

The launcher handles common scenarios:

- **Invalid API credentials**: Clear error messages with authentication failure details
- **Network issues**: Retry logic and connection timeout handling
- **Cluster creation failures**: Detailed error reporting from Atlas API
- **Rate limiting**: Automatic throttling to respect Atlas API limits

## Security Notes

- API credentials are only stored in memory during execution
- Credentials are not logged or persisted to disk
- All API communication uses HTTPS with proper authentication
- Consider using environment variables for automation scenarios

## Troubleshooting

**"Failed to connect to Atlas API"**
- Verify API credentials are correct
- Check that your IP is whitelisted for API access
- Ensure network connectivity to Atlas

**"Cluster creation failed"**
- Check Atlas billing and account limits
- Verify project permissions for cluster creation
- Review cluster name uniqueness within project

**"Java not found"**
- Install Java 17 or higher
- Verify `java` command is in your PATH

## Support

For issues related to:
- **Atlas API**: Check [MongoDB Atlas documentation](https://docs.atlas.mongodb.com/api/)
- **This launcher**: Report issues on the GitHub repository
- **mongo-launcher integration**: See `INTEGRATION-GUIDE.md`