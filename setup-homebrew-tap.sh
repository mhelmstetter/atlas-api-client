#!/bin/bash

# Setup script for MongoLauncher Homebrew Tap
# Run this after creating the homebrew-mongo-launcher repository on GitHub

set -e

echo "🍺 Setting up MongoLauncher Homebrew Tap"
echo "======================================="

# Create temporary directory
TEMP_DIR="/tmp/homebrew-tap-setup"
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

echo "📁 Creating tap repository structure..."

# Initialize git repository
git init
git branch -M main

# Create Formula directory
mkdir -p Formula

# Copy the formula file
cp "/Users/mh/git/atlas-api-client/mongo-launcher-homebrew-formula.rb" "Formula/mongo-launcher.rb"

# Copy the README
cp "/Users/mh/git/atlas-api-client/homebrew-tap-readme.md" "README.md"

# Create .gitignore
cat > .gitignore << 'EOF'
# OS files
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes
ehthumbs.db
Thumbs.db

# Editor files
*.swp
*.swo
*~
.vscode/
.idea/
EOF

echo "📝 Adding files to git..."
git add .
git commit -m "Initial MongoLauncher Homebrew formula v1.0.0

- Add Formula/mongo-launcher.rb with v1.0.0
- Interactive CLI with configuration management
- Support for Atlas and local clusters
- Compatible with openjdk@17"

echo "🔗 Adding GitHub remote..."
git remote add origin https://github.com/mhelmstetter/homebrew-mongo-launcher.git

echo "🚀 Ready to push to GitHub!"
echo ""
echo "Next steps:"
echo "1. Make sure you've created the 'homebrew-mongo-launcher' repository on GitHub"
echo "2. Run: git push -u origin main"
echo "3. Test installation: brew tap mhelmstetter/mongo-launcher && brew install mongo-launcher"
echo ""
echo "Current directory: $TEMP_DIR"
echo "You can now run: git push -u origin main"