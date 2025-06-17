# MongoLauncher Homebrew Tap

This is the official Homebrew tap for MongoLauncher - MongoDB Cluster Management Tool.

## Installation

```bash
brew tap mhelmstetter/mongo-launcher
brew install mongo-launcher
```

## Quick Start

```bash
# Interactive mode (guided setup)
mongo-launcher launch

# Show configuration
mongo-launcher config show

# Set Atlas project ID for cloud clusters
mongo-launcher config set defaultAtlasProjectId "your-project-id"

# Launch local cluster (non-interactive)
mongo-launcher launch --type local --name my-cluster --non-interactive

# Launch Atlas cluster (non-interactive)
mongo-launcher launch --type atlas --name prod-cluster --project-id "your-project-id" --non-interactive
```

## Features

MongoLauncher provides:

- **Interactive CLI**: Claude-like prompting for guided cluster setup
- **Configuration Management**: Persistent, platform-specific settings
- **Atlas Integration**: Cloud cluster management via Atlas API
- **Local Clusters**: Standalone, replica set, and sharded deployments
- **Version Management**: Compatible with MongoDB `m` tool
- **Cross-Platform**: Native support for macOS, Linux, and Windows

## Requirements

- Java 17 or later
- Maven (for building from source)
- Internet connection (for MongoDB version downloads)
- Atlas API credentials (for Atlas clusters)

## Documentation

For complete documentation, visit: https://github.com/mhelmstetter/mongo-launcher

## Support

- [Issues](https://github.com/mhelmstetter/mongo-launcher/issues)
- [Installation Guide](https://github.com/mhelmstetter/mongo-launcher/blob/main/INSTALL.md)
- [Configuration Guide](https://github.com/mhelmstetter/mongo-launcher#configuration)