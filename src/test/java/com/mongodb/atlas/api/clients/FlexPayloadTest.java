package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Quick test to check Flex cluster payload format
 */
public class FlexPayloadTest {
    
    public static void main(String[] args) throws Exception {
        // Simulate the buildFlexClusterSpec method
        String clusterName = "test-flex-cluster";
        String mongoVersion = "7.0"; 
        String region = "US_WEST_2";
        String cloudProvider = "AWS";
        
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", clusterName);
        
        // Flex cluster provider settings - according to Atlas API docs
        Map<String, Object> providerSettings = new HashMap<>();
        providerSettings.put("backingProviderName", cloudProvider.toUpperCase());
        providerSettings.put("regionName", region.toUpperCase());
        
        spec.put("providerSettings", providerSettings);
        spec.put("terminationProtectionEnabled", false);
        
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(spec);
        
        System.out.println("Generated JSON payload:");
        System.out.println(json);
        
        // Pretty print
        Object obj = mapper.readValue(json, Object.class);
        String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        System.out.println("\nPretty formatted:");
        System.out.println(prettyJson);
    }
}