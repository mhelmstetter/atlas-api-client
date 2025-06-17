#!/bin/bash

# MongoLauncher Installation Script for Mac/Linux
# This script installs MongoLauncher to /usr/local/bin and sets up necessary configurations

set -e  # Exit on any error

INSTALL_DIR="/usr/local/bin"
LAUNCHER_DIR="/usr/local/share/mongo-launcher"
SCRIPT_NAME="mongo-launcher"
JAR_NAME="mongo-launcher.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_color() {
    printf "${1}${2}${NC}\n"
}

print_color $BLUE "┌─────────────────────────────────────────────┐"
print_color $BLUE "│          MongoLauncher Installer           │"
print_color $BLUE "│     MongoDB Cluster Management Tool        │"
print_color $BLUE "└─────────────────────────────────────────────┘"
echo

# Check if running as root
if [[ $EUID -eq 0 ]]; then
    print_color $YELLOW "Warning: Running as root. This will install system-wide."
    SUDO=""
else
    print_color $BLUE "Installing MongoLauncher (requires sudo for system installation)..."
    SUDO="sudo"
fi

# Check if Java is installed
print_color $BLUE "Checking Java installation..."
if ! command -v java &> /dev/null; then
    print_color $RED "Error: Java is not installed or not in PATH."
    print_color $YELLOW "Please install Java 17 or later and try again."
    print_color $YELLOW "  On macOS: brew install openjdk@17"
    print_color $YELLOW "  On Ubuntu: sudo apt install openjdk-17-jdk"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ $JAVA_VERSION -lt 17 ]]; then
    print_color $RED "Error: Java 17 or later is required. Found version: $JAVA_VERSION"
    exit 1
fi

print_color $GREEN "✓ Java $JAVA_VERSION found"

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if JAR file exists
if [[ ! -f "$SCRIPT_DIR/bin/$JAR_NAME" ]]; then
    print_color $RED "Error: $JAR_NAME not found in $SCRIPT_DIR/bin/"
    print_color $YELLOW "Please build the project first: mvn package"
    exit 1
fi

print_color $BLUE "Installing MongoLauncher..."

# Create installation directory
$SUDO mkdir -p "$LAUNCHER_DIR"

# Copy JAR file
print_color $BLUE "Copying JAR file to $LAUNCHER_DIR..."
$SUDO cp "$SCRIPT_DIR/bin/$JAR_NAME" "$LAUNCHER_DIR/"

# Create wrapper script
print_color $BLUE "Creating wrapper script..."
$SUDO tee "$INSTALL_DIR/$SCRIPT_NAME" > /dev/null << 'EOF'
#!/bin/bash

# MongoLauncher wrapper script
LAUNCHER_DIR="/usr/local/share/mongo-launcher"
JAR_FILE="$LAUNCHER_DIR/mongo-launcher.jar"

if [[ ! -f "$JAR_FILE" ]]; then
    echo "Error: MongoLauncher JAR file not found at $JAR_FILE"
    echo "Please reinstall MongoLauncher"
    exit 1
fi

# Pass all arguments to the JAR
exec java -jar "$JAR_FILE" "$@"
EOF

# Make script executable
$SUDO chmod +x "$INSTALL_DIR/$SCRIPT_NAME"

print_color $GREEN "✓ MongoLauncher installed successfully!"
echo

# Verify installation
print_color $BLUE "Verifying installation..."
if command -v mongo-launcher &> /dev/null; then
    print_color $GREEN "✓ mongo-launcher command is available"
    echo
    print_color $BLUE "Testing installation..."
    mongo-launcher --version
    echo
else
    print_color $YELLOW "Warning: mongo-launcher command not found in PATH"
    print_color $YELLOW "You may need to restart your terminal or add $INSTALL_DIR to your PATH"
fi

print_color $GREEN "Installation complete!"
echo
print_color $BLUE "Usage:"
print_color $BLUE "  mongo-launcher launch --help    # Get help for launching clusters"
print_color $BLUE "  mongo-launcher config show      # Show current configuration"
print_color $BLUE "  mongo-launcher --help           # Show all available commands"
echo
print_color $BLUE "Configuration:"
print_color $BLUE "  Config directory: ~/.mongo-launcher"
print_color $BLUE "  To set defaults: mongo-launcher config set <key> <value>"
echo

# Offer to create initial configuration
if [[ ! -f "$HOME/.mongo-launcher/config.json" ]]; then
    echo
    read -p "Would you like to set up initial configuration? [Y/n]: " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
        print_color $BLUE "Setting up initial configuration..."
        
        # Ask for Atlas Project ID
        read -p "Enter your Atlas Project ID (optional): " ATLAS_PROJECT_ID
        if [[ ! -z "$ATLAS_PROJECT_ID" ]]; then
            mongo-launcher config set defaultAtlasProjectId "$ATLAS_PROJECT_ID"
            print_color $GREEN "✓ Atlas Project ID set"
        fi
        
        # Ask for default MongoDB version
        read -p "Enter default MongoDB version [7.0]: " MONGO_VERSION
        MONGO_VERSION=${MONGO_VERSION:-7.0}
        mongo-launcher config set defaultMongoVersion "$MONGO_VERSION"
        print_color $GREEN "✓ Default MongoDB version set to $MONGO_VERSION"
        
        print_color $GREEN "Initial configuration complete!"
        echo
        print_color $BLUE "View your configuration: mongo-launcher config show"
    fi
fi

print_color $GREEN "MongoLauncher is ready to use!"