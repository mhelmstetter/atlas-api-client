package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * CLI command for interactive mode
 */
@Command(
    name = "interactive", 
    description = "Start interactive mode with menu-driven interface"
)
public class InteractiveCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🚧 Interactive mode - Coming soon!");
        System.out.println("This will provide a menu-driven interface for all Atlas operations.");
        System.out.println("For now, use: atlas-cli --interactive");
        return 0;
    }
}