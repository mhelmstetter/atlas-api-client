#!/bin/bash

# Update Homebrew formula to use pre-built JAR instead of building from source

set -e

echo "🍺 Updating MongoLauncher Homebrew Formula"
echo "=========================================="

# Create temporary directory
TEMP_DIR="/tmp/homebrew-formula-update"
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

echo "📥 Cloning homebrew tap repository..."
git clone https://github.com/mhelmstetter/homebrew-mongo-launcher.git
cd homebrew-mongo-launcher

echo "🔄 Updating formula to use pre-built JAR..."

# Replace the formula with the fixed version
cp "/Users/mh/git/atlas-api-client/mongo-launcher-fixed-formula.rb" "Formula/mongo-launcher.rb"

echo "📝 Committing changes..."
git add Formula/mongo-launcher.rb
git commit -m "Fix formula to use pre-built JAR from GitHub release

- Change URL to download JAR directly from GitHub release
- Remove Maven build dependency and build process  
- Use correct SHA256 for pre-built JAR file
- Fixes dependency resolution issues with atlas-client
- Significantly faster installation (no compilation required)"

echo "🚀 Pushing to GitHub..."
git push origin main

echo "✅ Homebrew formula updated successfully!"
echo ""
echo "The formula now:"
echo "- Downloads pre-built JAR from GitHub release"
echo "- Only requires Java 17 (no Maven needed)"
echo "- Installs much faster (no compilation)"
echo "- Avoids dependency resolution issues"
echo ""
echo "Users should now be able to install with:"
echo "  brew tap mhelmstetter/mongo-launcher"
echo "  brew install mongo-launcher"