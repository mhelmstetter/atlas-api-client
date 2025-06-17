package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas database user management
 */
@Command(
    name = "database-users", 
    description = "Manage Atlas database users (placeholder - implementation pending)"
)
public class DatabaseUsersCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("🚧 Database Users management - Coming soon!");
        System.out.println("This will provide CRUD operations for Atlas database users.");
        return 0;
    }
}