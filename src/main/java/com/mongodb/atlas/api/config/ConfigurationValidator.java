package com.mongodb.atlas.api.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for validating Atlas configuration and providing setup guidance
 */
public class ConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);

    /**
     * Validate configuration and provide detailed feedback
     */
    public static ValidationResult validateConfiguration() {
        AtlasTestConfig config = AtlasTestConfig.getInstance();
        ValidationResult result = new ValidationResult();
        
        // Check required credentials
        if (!config.hasRequiredCredentials()) {
            result.addError("Atlas API credentials are missing or empty");
            result.addSuggestion("Set atlas.api.public.key and atlas.api.private.key in properties file");
            result.addSuggestion("Or set ATLAS_API_PUBLIC_KEY and ATLAS_API_PRIVATE_KEY environment variables");
        } else {
            result.addSuccess("Atlas API credentials are configured");
        }
        
        // Check optional test configuration
        if (config.getTestProjectId() == null) {
            result.addWarning("No test project ID specified - will use first available project");
            result.addSuggestion("Set atlas.test.project.id for consistent testing");
        } else {
            result.addSuccess("Test project ID is configured: " + config.getTestProjectId());
        }
        
        if (config.getTestOrgId() == null) {
            result.addWarning("No test organization ID specified - project creation tests will be skipped");
            result.addSuggestion("Set atlas.test.org.id to enable project creation tests");
        } else {
            result.addSuccess("Test organization ID is configured");
        }
        
        // Check test settings
        result.addInfo("Test region: " + config.getTestRegion());
        result.addInfo("Test cloud provider: " + config.getTestCloudProvider());
        result.addInfo("Test MongoDB version: " + config.getTestMongoVersion());
        
        return result;
    }
    
    /**
     * Print detailed configuration validation report
     */
    public static void printValidationReport() {
        ValidationResult result = validateConfiguration();
        
        System.out.println();
        System.out.println("=================================================");
        System.out.println("        Atlas Configuration Validation");
        System.out.println("=================================================");
        
        if (!result.errors.isEmpty()) {
            System.out.println();
            System.out.println("❌ ERRORS:");
            result.errors.forEach(error -> System.out.println("   • " + error));
        }
        
        if (!result.warnings.isEmpty()) {
            System.out.println();
            System.out.println("⚠️  WARNINGS:");
            result.warnings.forEach(warning -> System.out.println("   • " + warning));
        }
        
        if (!result.successes.isEmpty()) {
            System.out.println();
            System.out.println("✅ SUCCESS:");
            result.successes.forEach(success -> System.out.println("   • " + success));
        }
        
        if (!result.info.isEmpty()) {
            System.out.println();
            System.out.println("ℹ️  INFORMATION:");
            result.info.forEach(info -> System.out.println("   • " + info));
        }
        
        if (!result.suggestions.isEmpty()) {
            System.out.println();
            System.out.println("💡 SUGGESTIONS:");
            result.suggestions.forEach(suggestion -> System.out.println("   • " + suggestion));
        }
        
        System.out.println();
        System.out.println("=================================================");
        
        if (result.isValid()) {
            System.out.println("✅ Configuration is valid for testing!");
        } else {
            System.out.println("❌ Configuration needs attention before testing");
        }
        System.out.println("=================================================");
        System.out.println();
    }
    
    /**
     * Generate setup instructions based on current configuration state
     */
    public static void printSetupInstructions() {
        AtlasTestConfig config = AtlasTestConfig.getInstance();
        
        System.out.println();
        System.out.println("=================================================");
        System.out.println("         Atlas API Client Setup Guide");
        System.out.println("=================================================");
        
        if (!config.hasRequiredCredentials()) {
            System.out.println();
            System.out.println("📋 STEP 1: Create Atlas API Keys");
            System.out.println("   1. Log into MongoDB Atlas");
            System.out.println("   2. Go to Organization Settings → Access Manager → API Keys");
            System.out.println("   3. Create new API key with 'Project Owner' permissions");
            System.out.println("   4. Copy the public and private keys");
            
            System.out.println();
            System.out.println("📋 STEP 2: Configure Credentials");
            System.out.println("   Option A - Properties File (Recommended):");
            System.out.println("   1. Copy src/test/resources/atlas-test.properties.template");
            System.out.println("      to src/test/resources/atlas-test.properties");
            System.out.println("   2. Edit atlas-test.properties and set:");
            System.out.println("      atlas.api.public.key=your_public_key");
            System.out.println("      atlas.api.private.key=your_private_key");
            System.out.println();
            System.out.println("   Option B - Environment Variables:");
            System.out.println("   export ATLAS_API_PUBLIC_KEY=\"your_public_key\"");
            System.out.println("   export ATLAS_API_PRIVATE_KEY=\"your_private_key\"");
        } else {
            System.out.println("✅ API credentials are configured");
        }
        
        System.out.println();
        System.out.println("📋 STEP 3: Optional Test Configuration");
        System.out.println("   Set these in atlas-test.properties for better test control:");
        System.out.println("   atlas.test.project.id=your_test_project_id");
        System.out.println("   atlas.test.org.id=your_organization_id");
        System.out.println("   atlas.test.region=US_EAST_1");
        System.out.println("   atlas.test.cloud.provider=AWS");
        
        System.out.println();
        System.out.println("📋 STEP 4: Run Tests");
        System.out.println("   # Validate configuration");
        System.out.println("   mvn test -Dtest=ConfigurationValidator");
        System.out.println();
        System.out.println("   # Run specific test class");
        System.out.println("   mvn test -Dtest=AtlasProjectsClientIntegrationTest");
        System.out.println();
        System.out.println("   # Run all integration tests");
        System.out.println("   mvn test");
        
        System.out.println();
        System.out.println("=================================================");
        System.out.println();
    }
    
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        private final List<String> suggestions = new ArrayList<>();
        
        public void addError(String message) { errors.add(message); }
        public void addWarning(String message) { warnings.add(message); }
        public void addSuccess(String message) { successes.add(message); }
        public void addInfo(String message) { info.add(message); }
        public void addSuggestion(String message) { suggestions.add(message); }
        
        public boolean isValid() { return errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public List<String> getSuccesses() { return new ArrayList<>(successes); }
    }
    
    /**
     * Main method for running configuration validation
     */
    public static void main(String[] args) {
        if (args.length > 0 && "setup".equals(args[0])) {
            printSetupInstructions();
        } else {
            printValidationReport();
        }
    }
}