package com.helmstetter.atlas.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles mapping between processes and their clusters
 */
public class ClusterMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterMapper.class);
    
    private final AtlasApiClient apiClient;
    
    public ClusterMapper(AtlasApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    /**
     * Build a mapping of process hostnames to their cluster names
     * Uses the clusters endpoint to get accurate cluster information
     * 
     * @param projectId The project ID
     * @return Map of process hostnames to cluster names
     */
    public Map<String, String> buildProcessToClusterMapping(String projectId) {
        // Get all clusters in the project
        List<Map<String, Object>> clusters = apiClient.getClusters(projectId);
        logger.info("Found {} clusters in project {}", clusters.size(), projectId);
        
        // Get all processes for this project
        List<Map<String, Object>> processes = apiClient.getProcesses(projectId);
        
        // Create the hostname to cluster name mapping
        Map<String, String> hostnameToClusterMap = new HashMap<>();
        
        // For each cluster, find its processes
        for (Map<String, Object> cluster : clusters) {
            String clusterName = (String) cluster.get("name");
            
            try {
                // Get processes specifically for this cluster
                List<Map<String, Object>> clusterProcesses = apiClient.getProcessesForCluster(projectId, clusterName);
                
                // Add each process hostname to our mapping
                for (Map<String, Object> process : clusterProcesses) {
                    String hostname = (String) process.get("hostname");
                    if (hostname != null) {
                        hostnameToClusterMap.put(hostname, clusterName);
                    }
                }
                
                logger.info("Mapped {} processes to cluster '{}'", clusterProcesses.size(), clusterName);
            } catch (Exception e) {
                logger.warn("Could not get processes for cluster {}: {}", clusterName, e.getMessage());
            }
        }
        
        // If our mapping is incomplete, use processes as a fallback
        if (hostnameToClusterMap.size() < processes.size()) {
            logger.warn("Could not map all processes to clusters ({} of {}), using hostname patterns as fallback", 
                    hostnameToClusterMap.size(), processes.size());
            
            // For unmapped processes, try to use hostname pattern matching as fallback
            for (Map<String, Object> process : processes) {
                String hostname = (String) process.get("hostname");
                if (hostname == null || hostnameToClusterMap.containsKey(hostname)) {
                    continue; // Skip if already mapped or null
                }
                
                // Try to find which cluster this might belong to
                for (Map<String, Object> cluster : clusters) {
                    String clusterName = (String) cluster.get("name");
                    
                    // Check if hostname contains the cluster name or vice versa
                    if (hostname.contains(clusterName) || 
                        (clusterName.length() > 3 && hostname.contains(clusterName.substring(0, 3)))) {
                        hostnameToClusterMap.put(hostname, clusterName);
                        break;
                    }
                }
                
                // If still not mapped, just mark as unknown
                if (!hostnameToClusterMap.containsKey(hostname)) {
                    hostnameToClusterMap.put(hostname, "unknown");
                }
            }
        }
        
        return hostnameToClusterMap;
    }
}