package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * Legacy wrapper for the existing AtlasClusterLauncher
 */
@Command(
    name = "legacy-cluster-launcher", 
    description = "Run the legacy AtlasClusterLauncher (backward compatibility)"
)
public class LegacyClusterLauncherCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🔄 Launching legacy AtlasClusterLauncher...");
        System.out.println("(Use 'atlas-cli clusters create' or 'atlas-cli interactive' for the new interface)");
        
        // For now, just show a message - the actual implementation would delegate to AtlasClusterLauncher
        System.out.println("This would delegate to: com.mongodb.atlas.api.AtlasClusterLauncher");
        System.out.println("Run directly with: java -cp atlas-client.jar com.mongodb.atlas.api.AtlasClusterLauncher");
        
        return 0;
    }
}