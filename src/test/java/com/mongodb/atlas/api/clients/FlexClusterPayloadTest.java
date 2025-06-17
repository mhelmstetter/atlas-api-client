package com.mongodb.atlas.api.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

public class FlexClusterPayloadTest {

    @Test
    void testFlexClusterPayloadGeneration() throws Exception {
        AtlasFlexClustersClient client = new AtlasFlexClustersClient(null);
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Use reflection to call the private buildFlexClusterSpec method
        Method buildMethod = AtlasFlexClustersClient.class.getDeclaredMethod(
            "buildFlexClusterSpec", String.class, String.class, String.class, String.class);
        buildMethod.setAccessible(true);
        
        Map<String, Object> spec = (Map<String, Object>) buildMethod.invoke(
            client, "test-cluster", "7.0", "US_EAST_1", "AWS");
        
        String json = objectMapper.writeValueAsString(spec);
        System.out.println("=== ACTUAL FLEX CLUSTER PAYLOAD ===");
        System.out.println(json);
        System.out.println("====================================");
        
        // Print individual fields for debugging
        System.out.println("Individual fields:");
        spec.forEach((key, value) -> {
            System.out.println("  " + key + ": " + value);
        });
    }
}