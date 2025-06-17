package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas project management
 */
@Command(
    name = "projects", 
    description = "Manage Atlas projects (placeholder - implementation pending)"
)
public class ProjectsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🚧 Project management - Coming soon!");
        System.out.println("This will provide project CRUD operations and configuration.");
        return 0;
    }
}