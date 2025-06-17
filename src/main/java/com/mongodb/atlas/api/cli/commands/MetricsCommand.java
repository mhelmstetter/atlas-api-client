package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas metrics and monitoring
 */
@Command(
    name = "metrics", 
    description = "Analyze Atlas metrics and monitoring data (placeholder - implementation pending)"
)
public class MetricsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🚧 Metrics and Monitoring - Coming soon!");
        System.out.println("This will provide access to the existing AtlasMetricsAnalyzer functionality.");
        System.out.println("For now, use: atlas-cli legacy-metrics-analyzer");
        return 0;
    }
}