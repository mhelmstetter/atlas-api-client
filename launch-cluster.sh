#!/bin/bash

# MongoDB Atlas Interactive Cluster Launcher
# Demonstrates the enhanced atlas-api-client with real cluster creation

echo "🍃 MongoDB Atlas Interactive Cluster Launcher"
echo "=============================================="
echo ""
echo "This demo shows how to integrate real Atlas cluster creation"
echo "into the mongo-launcher interactive mode."
echo ""

# Check if project is compiled
if [ ! -d "target/classes" ]; then
    echo "📦 Compiling project..."
    mvn compile -DskipTests
fi

echo "🚀 Starting interactive cluster launcher..."
echo ""

# Run the enhanced cluster launcher
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.mongodb.atlas.api.launcher.AtlasClusterLauncher

echo ""
echo "✅ Demo complete!"
echo ""
echo "🔧 Integration Instructions:"
echo "============================"
echo "To integrate this into mongo-launcher:"
echo ""
echo "1. Copy the cluster creation methods from AtlasClustersClient"
echo "2. Add interactive prompting for API credentials in InteractivePrompt"
echo "3. Replace the stubbed AtlasClusterLauncher with real implementation"
echo "4. Use AtlasApiClient instead of mock responses"
echo ""
echo "Key changes needed in mongo-launcher:"
echo ""
echo "InteractivePrompt.java:"
echo "  - Add promptForApiPublicKey()"
echo "  - Add promptForApiPrivateKey()"
echo "  - Add promptForCloudProvider()"
echo "  - Add promptForRegion()"
echo ""
echo "AtlasClusterLauncher.java:"
echo "  - Initialize AtlasApiClient with user-provided credentials"
echo "  - Call apiClient.clusters().createCluster(...)"
echo "  - Wait for cluster ready state with waitForClusterState(...)"
echo "  - Return real cluster connection strings"
echo ""