#!/bin/bash

# Homebrew Tap Setup Script for MongoLauncher
# Run this script to create and publish your Homebrew tap

set -e

GITHUB_USERNAME="mhelmstetter"
TAP_REPO="homebrew-mongo-launcher"
TEMP_DIR="/tmp/homebrew-tap-setup"

echo "Setting up Homebrew tap for MongoLauncher..."

# Clean and create temp directory
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

# Clone your new homebrew tap repository
echo "Cloning homebrew tap repository..."
git clone "https://github.com/${GITHUB_USERNAME}/${TAP_REPO}.git"
cd "$TAP_REPO"

# Create the formula directory structure
mkdir -p Formula

# Copy and update the formula
echo "Creating Homebrew formula..."
cat > Formula/mongo-launcher.rb << 'EOF'
class MongoLauncher < Formula
  desc "MongoDB Cluster Management Tool with Interactive CLI"
  homepage "https://github.com/mhelmstetter/mongo-launcher"
  url "https://github.com/mhelmstetter/mongo-launcher/archive/refs/tags/v1.0.0.tar.gz"
  sha256 "REPLACE_WITH_ACTUAL_SHA256"
  license "Apache-2.0"
  
  depends_on "openjdk@17"
  depends_on "maven" => :build

  def install
    # Build the project
    system "mvn", "clean", "package", "-DskipTests"
    
    # Install JAR file
    libexec.install "bin/mongo-launcher.jar"
    
    # Create wrapper script
    (bin/"mongo-launcher").write <<~EOS
      #!/bin/bash
      exec "#{Formula["openjdk@17"].opt_bin}/java" -jar "#{libexec}/mongo-launcher.jar" "$@"
    EOS
    
    # Make wrapper executable
    chmod 0755, bin/"mongo-launcher"
  end

  test do
    # Test that the application runs and shows version
    assert_match "1.0.0", shell_output("#{bin}/mongo-launcher --version")
  end

  def caveats
    <<~EOS
      MongoLauncher has been installed!
      
      Configuration directory: ~/.mongo-launcher
      
      Getting started:
        mongo-launcher config show      # Show current configuration
        mongo-launcher config set defaultAtlasProjectId "your-project-id"
        mongo-launcher launch --help    # Get help for launching clusters
      
      For interactive mode (default):
        mongo-launcher launch
      
      For non-interactive mode:
        mongo-launcher launch --type local --name my-cluster --non-interactive
    EOS
  end
end
EOF

# Create README for the tap
cat > README.md << 'EOF'
# MongoLauncher Homebrew Tap

This is the official Homebrew tap for MongoLauncher - MongoDB Cluster Management Tool.

## Installation

```bash
brew tap mhelmstetter/mongo-launcher
brew install mongo-launcher
```

## Usage

```bash
# Interactive mode (guided setup)
mongo-launcher launch

# Non-interactive mode
mongo-launcher launch --type local --name my-cluster --non-interactive

# Configuration management
mongo-launcher config show
mongo-launcher config set defaultAtlasProjectId "your-project-id"
```

## About MongoLauncher

MongoLauncher is a comprehensive framework for launching and managing MongoDB clusters, both in Atlas and locally. It provides:

- Interactive CLI with Claude-like prompting
- Cross-platform configuration management
- Atlas and local cluster support
- MongoDB version management compatible with 'm' tool
- Automated installers for multiple platforms

For more information, visit: https://github.com/mhelmstetter/mongo-launcher
EOF

echo "Homebrew tap files created successfully!"
echo ""
echo "Next steps:"
echo "1. Get the SHA256 of your v1.0.0 release:"
echo "   curl -L https://github.com/mhelmstetter/mongo-launcher/archive/refs/tags/v1.0.0.tar.gz | shasum -a 256"
echo ""
echo "2. Edit Formula/mongo-launcher.rb and replace 'REPLACE_WITH_ACTUAL_SHA256' with the actual SHA256"
echo ""
echo "3. Commit and push:"
echo "   git add ."
echo "   git commit -m 'Add MongoLauncher formula v1.0.0'"
echo "   git push origin main"
echo ""
echo "4. Test installation:"
echo "   brew tap mhelmstetter/mongo-launcher"
echo "   brew install mongo-launcher"
EOF

chmod +x /Users/mh/git/atlas-api-client/homebrew-tap-setup.sh