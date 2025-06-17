package com.mongodb.atlas.api.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.mongodb.atlas.api.clients.AtlasApiBase;
import com.mongodb.atlas.api.clients.AtlasProgrammaticAPIKeysClient;
import com.mongodb.atlas.api.cli.AtlasCliMain.GlobalConfig;
import com.mongodb.atlas.api.cli.utils.OutputFormatter;
import com.mongodb.atlas.api.config.AtlasTestConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI commands for Atlas programmatic API key management
 */
@Command(
    name = "api-keys",
    description = "Manage Atlas programmatic API keys",
    subcommands = {
        ApiKeysCommand.ListCommand.class,
        ApiKeysCommand.GetCommand.class,
        ApiKeysCommand.CreateCommand.class,
        ApiKeysCommand.UpdateCommand.class,
        ApiKeysCommand.DeleteCommand.class,
        ApiKeysCommand.AccessListCommand.class,
        ApiKeysCommand.AssignCommand.class
    }
)
public class ApiKeysCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'atlas-cli api-keys --help' to see available API key commands");
        return 0;
    }

    @Command(name = "list", description = "List all API keys in an organization")
    static class ListCommand implements Callable<Integer> {
        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                List<Map<String, Object>> apiKeys = client.getOrganizationAPIKeys(effectiveOrgId);
                
                if (apiKeys.isEmpty()) {
                    System.out.println("📭 No API keys found in organization " + effectiveOrgId);
                    return 0;
                }

                System.out.println("🔍 Found " + apiKeys.size() + " API key(s) in organization " + effectiveOrgId);
                OutputFormatter.printApiKeys(apiKeys, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error listing API keys: " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get details of a specific API key")
    static class GetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key ID")
        private String apiKeyId;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                Map<String, Object> apiKey = client.getOrganizationAPIKey(effectiveOrgId, apiKeyId);
                
                System.out.println("🔑 API Key Details: " + apiKeyId);
                OutputFormatter.printApiKeyDetails(apiKey, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error getting API key '" + apiKeyId + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new API key")
    static class CreateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key description")
        private String description;

        @Option(names = {"-r", "--roles"}, 
                description = "Comma-separated list of roles (ORG_OWNER, ORG_READ_ONLY, etc.)",
                required = true,
                split = ",")
        private List<String> roles;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Option(names = {"--access-list"}, 
                description = "Comma-separated list of IP addresses/CIDR blocks for access list",
                split = ",")
        private List<String> accessList;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                System.out.println("🚀 Creating API key '" + description + "'...");
                System.out.println("   Roles: " + String.join(", ", roles));
                
                Map<String, Object> apiKey = client.createOrganizationAPIKey(effectiveOrgId, description, roles);
                String apiKeyId = (String) apiKey.get("id");
                
                System.out.println("✅ API key created successfully!");
                OutputFormatter.printApiKeyDetails(apiKey, GlobalConfig.getFormat());
                
                // Add access list if provided
                if (accessList != null && !accessList.isEmpty()) {
                    System.out.println("🔒 Adding access list entries...");
                    client.createAPIKeyAccessList(effectiveOrgId, apiKeyId, accessList);
                    System.out.println("✅ Access list entries added: " + String.join(", ", accessList));
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error creating API key '" + description + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "update", description = "Update an API key")
    static class UpdateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key ID")
        private String apiKeyId;

        @Option(names = {"-d", "--description"}, description = "New description")
        private String description;

        @Option(names = {"-r", "--roles"}, 
                description = "Comma-separated list of new roles",
                split = ",")
        private List<String> roles;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            if (description == null && roles == null) {
                System.err.println("❌ Error: Either --description or --roles must be specified");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                // Get current API key details
                Map<String, Object> currentApiKey = client.getOrganizationAPIKey(effectiveOrgId, apiKeyId);
                
                String effectiveDescription = description != null ? description : (String) currentApiKey.get("desc");
                List<String> effectiveRoles = roles != null ? roles : (List<String>) currentApiKey.get("roles");
                
                System.out.println("🔧 Updating API key '" + apiKeyId + "'...");
                if (description != null) {
                    System.out.println("   New description: " + description);
                }
                if (roles != null) {
                    System.out.println("   New roles: " + String.join(", ", roles));
                }
                
                Map<String, Object> result = client.updateOrganizationAPIKey(
                    effectiveOrgId, apiKeyId, effectiveDescription, effectiveRoles);
                
                System.out.println("✅ API key updated successfully!");
                OutputFormatter.printApiKeyDetails(result, GlobalConfig.getFormat());
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error updating API key '" + apiKeyId + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete an API key")
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key ID")
        private String apiKeyId;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Option(names = {"-f", "--force"}, description = "Skip confirmation prompt")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            if (!force) {
                System.out.print("⚠️ Are you sure you want to delete API key '" + apiKeyId + "'? [y/N]: ");
                String confirmation = System.console() != null ? 
                    System.console().readLine() : 
                    new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirmation) && !"yes".equalsIgnoreCase(confirmation)) {
                    System.out.println("❌ Operation cancelled");
                    return 0;
                }
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                System.out.println("🗑️ Deleting API key '" + apiKeyId + "'...");
                
                client.deleteOrganizationAPIKey(effectiveOrgId, apiKeyId);
                
                System.out.println("✅ API key deleted successfully!");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error deleting API key '" + apiKeyId + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "access-list", description = "Manage API key access lists (IP whitelisting)")
    static class AccessListCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key ID")
        private String apiKeyId;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Option(names = {"--list"}, description = "List current access list entries")
        private boolean list;

        @Option(names = {"--add"}, 
                description = "Add IP addresses/CIDR blocks to access list",
                split = ",")
        private List<String> addEntries;

        @Option(names = {"--remove"}, description = "Remove IP address/CIDR block from access list")
        private String removeEntry;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                if (list) {
                    List<Map<String, Object>> accessList = client.getAPIKeyAccessList(effectiveOrgId, apiKeyId);
                    
                    System.out.println("🔒 Access List for API key '" + apiKeyId + "':");
                    if (accessList.isEmpty()) {
                        System.out.println("   No access list entries found");
                    } else {
                        for (Map<String, Object> entry : accessList) {
                            String ip = (String) entry.get("ipAddress");
                            String cidr = (String) entry.get("cidrBlock");
                            System.out.println("   - " + (ip != null ? ip : cidr));
                        }
                    }
                }
                
                if (addEntries != null && !addEntries.isEmpty()) {
                    System.out.println("🔒 Adding access list entries...");
                    client.createAPIKeyAccessList(effectiveOrgId, apiKeyId, addEntries);
                    System.out.println("✅ Access list entries added: " + String.join(", ", addEntries));
                }
                
                if (removeEntry != null) {
                    System.out.println("🔓 Removing access list entry: " + removeEntry);
                    client.deleteAPIKeyAccessListEntry(effectiveOrgId, apiKeyId, removeEntry);
                    System.out.println("✅ Access list entry removed");
                }
                
                if (!list && addEntries == null && removeEntry == null) {
                    System.out.println("Use --list, --add, or --remove options");
                    return 1;
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error managing access list for API key '" + apiKeyId + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }

    @Command(name = "assign", description = "Assign API key to specific projects with roles")
    static class AssignCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "API key ID")
        private String apiKeyId;

        @Option(names = {"-p", "--project"}, 
                description = "Project ID to assign to",
                required = true)
        private String projectId;

        @Option(names = {"-r", "--roles"}, 
                description = "Comma-separated list of project roles",
                required = true,
                split = ",")
        private List<String> roles;

        @Option(names = {"-o", "--org"}, description = "Organization ID (overrides config)")
        private String orgId;

        @Override
        public Integer call() throws Exception {
            AtlasTestConfig config = GlobalConfig.getAtlasConfig();
            String effectiveOrgId = orgId != null ? orgId : config.getTestOrgId();
            
            if (effectiveOrgId == null) {
                System.err.println("❌ Error: Organization ID is required. Use --org or set testOrgId in config.");
                return 1;
            }

            try {
                AtlasApiBase apiBase = new AtlasApiBase(config.getApiPublicKey(), config.getApiPrivateKey());
                AtlasProgrammaticAPIKeysClient client = new AtlasProgrammaticAPIKeysClient(apiBase);
                
                System.out.println("📋 Assigning API key '" + apiKeyId + "' to project '" + projectId + "'...");
                System.out.println("   Roles: " + String.join(", ", roles));
                
                List<Map<String, Object>> assignments = Arrays.asList(
                    AtlasProgrammaticAPIKeysClient.createProjectAssignment(projectId, roles)
                );
                
                client.assignAPIKeyToProjects(effectiveOrgId, apiKeyId, assignments);
                
                System.out.println("✅ API key assigned to project successfully!");
                
                return 0;
            } catch (Exception e) {
                System.err.println("❌ Error assigning API key '" + apiKeyId + "': " + e.getMessage());
                if (GlobalConfig.isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
    }
}