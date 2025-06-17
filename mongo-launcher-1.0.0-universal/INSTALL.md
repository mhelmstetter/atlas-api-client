# MongoLauncher Installation Guide

MongoLauncher provides multiple installation options to suit different environments and preferences.

## Prerequisites

- Java 17 or later
- Maven 3.6+ (for building from source)

## Installation Options

### 1. Automated Installation (Recommended)

#### Mac/Linux
```bash
# Clone the repository or download the install script
curl -L -o install.sh https://raw.githubusercontent.com/mhelmstetter/mongo-launcher/main/bin/install.sh
chmod +x install.sh
./install.sh
```

#### Windows
```cmd
REM Download and run as Administrator
curl -L -o install.bat https://raw.githubusercontent.com/mhelmstetter/mongo-launcher/main/bin/install.bat
install.bat
```

### 2. Homebrew Installation (macOS)

```bash
# Add the tap and install
brew tap mhelmstetter/mongo-launcher
brew install mongo-launcher

# Or install directly with full tap name
brew install mhelmstetter/mongo-launcher/mongo-launcher
```

### 3. Manual Installation

#### Build from Source
```bash
# Clone the repository
git clone https://github.com/mongodb/mongo-launcher.git
cd mongo-launcher

# Build the project
mvn clean package -DskipTests

# The executable JAR will be in bin/mongo-launcher.jar
```

#### Manual Setup
1. Copy `bin/mongo-launcher.jar` to your desired location
2. Create a wrapper script:

**Unix/Linux/macOS:**
```bash
#!/bin/bash
exec java -jar /path/to/mongo-launcher.jar "$@"
```

**Windows:**
```cmd
@echo off
java -jar "C:\path\to\mongo-launcher.jar" %*
```

3. Add the wrapper script location to your PATH

### 4. Direct Execution
```bash
# After building, you can run directly
java -jar bin/mongo-launcher.jar --help
```

## Post-Installation Setup

### Initial Configuration
After installation, set up your initial configuration:

```bash
# Show current configuration
mongo-launcher config show

# Set your Atlas Project ID (if using Atlas clusters)
mongo-launcher config set defaultAtlasProjectId "your-atlas-project-id"

# Set default MongoDB version
mongo-launcher config set defaultMongoVersion "7.0"

# Enable/disable interactive mode
mongo-launcher config set interactiveMode true
```

### Configuration Locations

- **macOS**: `~/.mongo-launcher/config.json`
- **Linux**: `~/.config/mongo-launcher/config.json`
- **Windows**: `%APPDATA%\MongoLauncher\config.json`

## Usage Examples

### Interactive Mode (Default)
```bash
# Launch with interactive prompts
mongo-launcher launch

# This will guide you through:
# - Cluster type (local/atlas)
# - Cluster name
# - MongoDB version
# - Topology options
# - And more...
```

### Non-Interactive Mode
```bash
# Launch local cluster
mongo-launcher launch --type local --name test-cluster --mongo-version 7.0 --non-interactive

# Launch Atlas cluster
mongo-launcher launch --type atlas --name prod-cluster --project-id "your-project-id" --instance-size M10 --non-interactive
```

### Configuration Management
```bash
# Show all settings
mongo-launcher config show

# Get specific setting
mongo-launcher config get defaultMongoVersion

# Set custom property
mongo-launcher config set myCustomProperty "custom-value"

# Remove setting
mongo-launcher config unset myCustomProperty

# Reset to defaults
mongo-launcher config reset --force
```

## Troubleshooting

### Common Issues

1. **Java not found**
   - Ensure Java 17+ is installed and in PATH
   - On macOS: `brew install openjdk@17`
   - On Ubuntu: `sudo apt install openjdk-17-jdk`

2. **Permission denied (Linux/macOS)**
   - Ensure the script is executable: `chmod +x install.sh`
   - Run installer with sudo if installing system-wide

3. **PATH not updated (Windows)**
   - Restart Command Prompt or PowerShell
   - Manually add installation directory to PATH

4. **Configuration not persisting**
   - Check write permissions to config directory
   - Verify config directory exists and is writable

### Getting Help

```bash
# General help
mongo-launcher --help

# Command-specific help
mongo-launcher launch --help
mongo-launcher config --help

# Show version
mongo-launcher --version
```

## Uninstallation

### Automated Installation
- **Linux/macOS**: Remove `/usr/local/bin/mongo-launcher` and `/usr/local/share/mongo-launcher/`
- **Windows**: Use Windows "Add or Remove Programs" or manually delete installation directory

### Homebrew
```bash
brew uninstall mongo-launcher
```

### Manual Installation
1. Remove the JAR file and wrapper script
2. Remove from PATH
3. Optionally remove configuration directory

### Clean Up Configuration
```bash
# Remove configuration directory
# macOS/Linux
rm -rf ~/.mongo-launcher
rm -rf ~/.config/mongo-launcher

# Windows
rmdir /s "%APPDATA%\MongoLauncher"
```