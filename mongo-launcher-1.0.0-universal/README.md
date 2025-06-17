# MongoLauncher

MongoLauncher is a comprehensive framework for launching and managing MongoDB clusters, both in Atlas and locally. It provides a unified API for cluster lifecycle management and an interactive command-line interface for seamless cluster operations.

## Features

- **Interactive CLI**: Claude-like interactive prompting for guided cluster setup
- **Configuration Management**: Persistent, platform-specific configuration storage
- **Unified API**: Single interface for managing both Atlas and local MongoDB clusters
- **Atlas Integration**: Leverage the atlas-api-client for cloud cluster management
- **Local Clusters**: Launch standalone, replica set, and sharded clusters locally
- **Version Management**: Automatic MongoDB version installation and management (compatible with `m` tool)
- **Cross-Platform**: Native installers for macOS, Linux, and Windows
- **Flexible Configuration**: JSON-based cluster specifications with smart defaults
- **mlaunch Inspired**: Local cluster functionality inspired by mtools/mlaunch

## Project Structure

```
mongo-launcher/
├── src/main/java/com/mongodb/launcher/     # Core API and CLI classes
├── bin/                                    # Built CLI executable and tools
│   ├── mongo-launcher.jar                  # Main executable JAR
│   ├── mongo-launcher                      # Unix/Linux/macOS launcher script
│   ├── mongo-launcher.bat                  # Windows launcher script
│   ├── install.sh                          # Unix/Linux/macOS installer
│   ├── install.bat                         # Windows installer
│   └── package.sh                          # Distribution packaging script
├── dist/                                   # Distribution packages (generated)
├── INSTALL.md                              # Detailed installation guide
└── pom.xml                                 # Maven configuration
```

## Installation

### Quick Install (Recommended)

#### macOS/Linux
```bash
curl -L -o install.sh https://raw.githubusercontent.com/mhelmstetter/mongo-launcher/main/bin/install.sh
chmod +x install.sh && ./install.sh
```

#### Windows (Run as Administrator)
```cmd
curl -L -o install.bat https://raw.githubusercontent.com/mhelmstetter/mongo-launcher/main/bin/install.bat
install.bat
```

#### Homebrew (macOS)
```bash
brew tap mhelmstetter/mongo-launcher
brew install mongo-launcher
```

### Building from Source

```bash
git clone <repository>
cd mongo-launcher
mvn clean package
```

This creates:
- `bin/mongo-launcher.jar` - The executable JAR
- `bin/mongo-launcher` - Unix/Linux/macOS executable script  
- `bin/mongo-launcher.bat` - Windows batch script

## Quick Start

### Interactive Mode (Default)

MongoLauncher features an interactive mode that guides you through cluster setup:

```bash
mongo-launcher launch
```

This will prompt you for:
- Cluster type (local/atlas)
- Cluster name and MongoDB version
- Topology (standalone/replica set/sharded)
- Configuration options specific to your choice
- Confirmation before creation

### Non-Interactive Mode

For automation or when you know exactly what you want:

```bash
# Local cluster
mongo-launcher launch --type local --name my-cluster --mongo-version 7.0 --non-interactive

# Atlas cluster  
mongo-launcher launch --type atlas --name prod-cluster --project-id your-project-id --instance-size M10 --non-interactive

# Using a specification file
mongo-launcher launch cluster-spec.json
```

### Configuration Management

Set up persistent defaults to streamline your workflow:

```bash
# Show current configuration
mongo-launcher config show

# Set your Atlas project ID
mongo-launcher config set defaultAtlasProjectId "your-atlas-project-id"

# Set default MongoDB version
mongo-launcher config set defaultMongoVersion "7.0"

# Enable/disable interactive mode
mongo-launcher config set interactiveMode true

# Get a specific setting
mongo-launcher config get defaultMongoVersion

# Reset to defaults
mongo-launcher config reset
```

### Cluster Specification Format

#### Local Cluster Example
```json
{
  "type": "local",
  "name": "local-test-cluster",
  "mongoVersion": "7.0",
  "port": 27017,
  "topology": "REPLICA_SET",
  "replicaSetSize": 3,
  "enableAuth": false,
  "dataPath": "/tmp/mongo-data",
  "logPath": "/tmp/mongo-logs/mongod.log"
}
```

#### Atlas Cluster Example
```json
{
  "type": "atlas",
  "name": "atlas-test-cluster", 
  "mongoVersion": "7.0",
  "projectId": "your-atlas-project-id",
  "instanceSize": "M10",
  "region": "US_EAST_1",
  "cloudProvider": "AWS",
  "topology": "REPLICA_SET",
  "enableBackup": true
}
```

## API Usage

```java
// Create cluster manager
ClusterManager manager = new ClusterManager(List.of(
    new AtlasClusterLauncher(),
    new LocalClusterLauncher()
));

// Create cluster specification
LocalClusterSpec spec = new LocalClusterSpec("test-cluster", "7.0");
spec.setTopology(LocalClusterSpec.LocalTopology.REPLICA_SET);
spec.setReplicaSetSize(3);

// Launch cluster
ClusterInstance cluster = manager.launch(spec);
System.out.println("Connection: " + cluster.getConnectionString());

// Use cluster...

// Clean up
manager.destroy(cluster);
```

## CLI Commands

### Cluster Management
- `launch` - Launch a new cluster (interactive by default)
- `status` - Get cluster status  
- `stop` - Stop a running cluster
- `destroy` - Destroy cluster and clean up resources
- `list` - List all managed clusters

### Configuration Management
- `config show` - Display all configuration settings
- `config set <key> <value>` - Set a configuration value
- `config get <key>` - Get a specific configuration value
- `config unset <key>` - Remove a configuration setting
- `config reset` - Reset to default configuration

### Version Management
- `version list` - List installed MongoDB versions
- `version install <version>` - Install a specific MongoDB version
- `version remove <version>` - Remove an installed version
- `version available` - List available versions for download
- `version info` - Show installation information and detected locations

## Configuration

MongoLauncher stores configuration in platform-specific locations:

- **macOS**: `~/.mongo-launcher/config.json`
- **Linux**: `~/.config/mongo-launcher/config.json`
- **Windows**: `%APPDATA%\MongoLauncher\config.json`

### Common Configuration Options

| Setting | Description | Default |
|---------|-------------|---------|
| `defaultMongoVersion` | Default MongoDB version for new clusters | `7.0` |
| `interactiveMode` | Enable interactive prompting | `true` |
| `defaultAtlasProjectId` | Default Atlas project ID | _(not set)_ |
| `defaultInstanceSize` | Default Atlas instance size | `M10` |
| `defaultRegion` | Default Atlas region | `US_EAST_1` |
| `defaultCloudProvider` | Default Atlas cloud provider | `AWS` |
| `defaultDataPath` | Default data directory for local clusters | _platform-specific_ |
| `defaultLogPath` | Default log directory for local clusters | _platform-specific_ |

## MongoDB Version Management

MongoLauncher includes a built-in version manager compatible with the `m` tool's directory structure. It automatically downloads and manages MongoDB versions as needed.

### Version Storage & Detection
- **Automatic Detection**: Scans multiple locations for existing `m` installations:
  - `~/.local/m/versions/[version]` (user-local)
  - `/usr/local/m/versions/[version]` (system-wide)
  - `/opt/m/versions/[version]` (alternative system location)
- **Smart Installation**: Prefers writable locations with existing versions
- **Read-Only Support**: Can use versions from read-only system installations
- **Custom Location**: Use `M_PREFIX` environment variable to override

### Version Commands
```bash
# List available versions
./bin/mongo-launcher version available

# Install a specific version
./bin/mongo-launcher version install 7.0.6

# Install latest in a series
./bin/mongo-launcher version install 7.0

# Install latest version
./bin/mongo-launcher version install latest

# List installed versions
./bin/mongo-launcher version list

# Remove a version
./bin/mongo-launcher version remove 6.0.13

# Show installation information
./bin/mongo-launcher version info
```

### Version Specification
You can specify MongoDB versions in several ways:
- **Exact version**: `"7.0.6"`, `"6.0.13"`
- **Major.Minor**: `"7.0"`, `"6.0"` (uses latest patch)
- **Major only**: `"7"`, `"6"` (uses latest minor.patch)

### Automatic Installation
When launching a local cluster, if the specified MongoDB version isn't installed, MongoLauncher will automatically download and install it.

## Requirements

- Java 17+
- Internet connection (for version downloads)
- Atlas API credentials (for Atlas clusters)

## Local Cluster Features

- **Standalone**: Single mongod instance
- **Replica Set**: Multi-node replica set with automatic initialization
- **Sharded**: Sharded cluster with config servers and mongos routers (planned)
- **Authentication**: Optional auth setup
- **Custom Options**: Support for additional mongod parameters
- **Process Management**: Automatic process lifecycle management

## Atlas Integration

MongoLauncher integrates with the `atlas-api-client` project to provide:

- Cluster creation and deletion
- Status monitoring
- Connection string retrieval
- Configuration management

## Extension Points

The framework is designed to be extensible:

- Implement `ClusterLauncher<T>` for new cluster types
- Use Java ServiceLoader for automatic discovery
- Custom cluster specifications by extending `ClusterSpec`

## Distribution and Packaging

MongoLauncher provides comprehensive distribution options for different platforms.

### Creating Distribution Packages

```bash
# Create all platform-specific packages
./bin/package.sh
```

This generates:
- `mongo-launcher-1.0.0-universal.tar.gz/.zip` - Cross-platform package
- `mongo-launcher-1.0.0-macos.tar.gz` - macOS-specific with Homebrew formula
- `mongo-launcher-1.0.0-linux.tar.gz` - Linux-specific package
- `mongo-launcher-1.0.0-windows.zip` - Windows-specific package
- `checksums.sha256` - Package verification checksums

### Package Contents

Each package includes:
- `mongo-launcher.jar` - Main executable JAR
- `install.sh`/`install.bat` - Automated installer scripts
- `mongo-launcher`/`mongo-launcher.bat` - Portable launcher scripts
- `INSTALL.md` - Detailed installation documentation

### Deployment Options

1. **GitHub Releases**: Upload generated packages as release artifacts
2. **Homebrew**: Available via tap `mhelmstetter/mongo-launcher`
3. **Direct Download**: Distribute packages via web hosting
4. **Package Managers**: Adapt for apt, yum, chocolatey, etc.

## Development

### Building from Source

```bash
git clone <repository>
cd mongo-launcher
mvn clean package
```

### Running Tests

```bash
mvn test
```

### Development Workflow

1. Make changes to source code
2. Build and test: `mvn clean package`
3. Test CLI: `./bin/mongo-launcher --help`
4. Create distribution: `./bin/package.sh`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the full build: `mvn clean package`
6. Submit a pull request

## Troubleshooting

### Common Issues

- **Java not found**: Ensure Java 17+ is installed and in PATH
- **Permission denied**: Make scripts executable with `chmod +x`
- **Configuration not persisting**: Check write permissions to config directory
- **Version downloads failing**: Verify internet connection and firewall settings

### Getting Help

```bash
# General help
mongo-launcher --help

# Command-specific help
mongo-launcher launch --help
mongo-launcher config --help

# Show version and build info
mongo-launcher --version
```

For detailed installation instructions, see [INSTALL.md](INSTALL.md).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.