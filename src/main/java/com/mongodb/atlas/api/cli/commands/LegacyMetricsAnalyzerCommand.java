package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * Legacy wrapper for the existing AtlasMetricsAnalyzer
 */
@Command(
    name = "legacy-metrics-analyzer", 
    description = "Run the legacy AtlasMetricsAnalyzer (backward compatibility)"
)
public class LegacyMetricsAnalyzerCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🔄 Launching legacy AtlasMetricsAnalyzer...");
        System.out.println("(Use 'atlas-cli metrics' once the new metrics command is implemented)");
        
        // For now, just show a message - the actual implementation would delegate to AtlasMetricsAnalyzer
        System.out.println("This would delegate to: com.mongodb.atlas.api.AtlasMetricsAnalyzer");
        System.out.println("Run directly with: java -cp atlas-client.jar com.mongodb.atlas.api.AtlasMetricsAnalyzer --help");
        
        return 0;
    }
}