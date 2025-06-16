package com.mongodb.atlas.api.clients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for MongoDB Atlas Cloud Backups API
 * Handles backup and restore operations
 */
public class AtlasBackupsClient {

    private static final Logger logger = LoggerFactory.getLogger(AtlasBackupsClient.class);

    private final AtlasApiBase apiBase;
    private final ObjectMapper objectMapper;

    public AtlasBackupsClient(AtlasApiBase apiBase) {
        this.apiBase = apiBase;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get backup snapshots for a cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @return List of backup snapshots
     */
    public List<Map<String, Object>> getBackupSnapshots(String projectId, String clusterName) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/snapshots";
        logger.info("Fetching backup snapshots for cluster '{}' in project {}", clusterName, projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific backup snapshot
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param snapshotId The snapshot ID
     * @return Map containing snapshot information
     */
    public Map<String, Object> getBackupSnapshot(String projectId, String clusterName, String snapshotId) {
        logger.info("Getting backup snapshot '{}' for cluster '{}' in project {}", snapshotId, clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/snapshots/" + snapshotId;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get backup snapshot '{}' for cluster '{}' in project {}: {}", 
                        snapshotId, clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get backup snapshot '" + snapshotId + "'", e);
        }
    }

    /**
     * Create an on-demand backup snapshot
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param description Description for the snapshot
     * @param retentionInDays How long to retain the snapshot (1-365 days)
     * @return Map containing snapshot creation response
     */
    public Map<String, Object> createBackupSnapshot(String projectId, String clusterName, 
                                                   String description, int retentionInDays) {
        logger.info("Creating backup snapshot for cluster '{}' in project {}", clusterName, projectId);
        
        try {
            Map<String, Object> snapshotSpec = new HashMap<>();
            snapshotSpec.put("description", description);
            snapshotSpec.put("retentionInDays", retentionInDays);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/snapshots";
            String requestBody = objectMapper.writeValueAsString(snapshotSpec);
            
            logger.debug("Backup snapshot creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Backup snapshot created successfully for cluster '{}'", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create backup snapshot for cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create backup snapshot for cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Delete a backup snapshot
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param snapshotId The snapshot ID to delete
     * @return Map containing deletion response
     */
    public Map<String, Object> deleteBackupSnapshot(String projectId, String clusterName, String snapshotId) {
        logger.info("Deleting backup snapshot '{}' for cluster '{}' in project {}", snapshotId, clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/snapshots/" + snapshotId;
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.DELETE, null, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Backup snapshot '{}' deleted successfully", snapshotId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to delete backup snapshot '{}' for cluster '{}' in project {}: {}", 
                        snapshotId, clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to delete backup snapshot '" + snapshotId + "'", e);
        }
    }

    /**
     * Get restore jobs for a cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @return List of restore jobs
     */
    public List<Map<String, Object>> getRestoreJobs(String projectId, String clusterName) {
        String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/restoreJobs";
        logger.info("Fetching restore jobs for cluster '{}' in project {}", clusterName, projectId);
        String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
        return apiBase.extractResults(responseBody);
    }

    /**
     * Get a specific restore job
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param restoreJobId The restore job ID
     * @return Map containing restore job information
     */
    public Map<String, Object> getRestoreJob(String projectId, String clusterName, String restoreJobId) {
        logger.info("Getting restore job '{}' for cluster '{}' in project {}", restoreJobId, clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/restoreJobs/" + restoreJobId;
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get restore job '{}' for cluster '{}' in project {}: {}", 
                        restoreJobId, clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get restore job '" + restoreJobId + "'", e);
        }
    }

    /**
     * Create a restore job to restore from a snapshot
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param snapshotId The snapshot ID to restore from
     * @param targetClusterName The name of the cluster to restore to
     * @return Map containing restore job creation response
     */
    public Map<String, Object> createRestoreJob(String projectId, String clusterName, 
                                               String snapshotId, String targetClusterName) {
        logger.info("Creating restore job for cluster '{}' from snapshot '{}' in project {}", 
                   clusterName, snapshotId, projectId);
        
        try {
            Map<String, Object> restoreSpec = new HashMap<>();
            restoreSpec.put("snapshotId", snapshotId);
            restoreSpec.put("deliveryType", "automated");
            restoreSpec.put("targetClusterName", targetClusterName);
            
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/restoreJobs";
            String requestBody = objectMapper.writeValueAsString(restoreSpec);
            
            logger.debug("Restore job creation payload: {}", requestBody);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.POST, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Restore job created successfully for cluster '{}'", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create restore job for cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to create restore job for cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Get backup policy for a cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @return Map containing backup policy information
     */
    public Map<String, Object> getBackupPolicy(String projectId, String clusterName) {
        logger.info("Getting backup policy for cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/policy";
            String responseBody = apiBase.getResponseBody(url, AtlasApiBase.API_VERSION_V2, projectId);
            
            return objectMapper.readValue(responseBody, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to get backup policy for cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to get backup policy for cluster '" + clusterName + "'", e);
        }
    }

    /**
     * Update backup policy for a cluster
     * 
     * @param projectId The Atlas project ID
     * @param clusterName The cluster name
     * @param policySpec The backup policy specification
     * @return Map containing update response
     */
    public Map<String, Object> updateBackupPolicy(String projectId, String clusterName, 
                                                 Map<String, Object> policySpec) {
        logger.info("Updating backup policy for cluster '{}' in project {}", clusterName, projectId);
        
        try {
            String url = AtlasApiBase.BASE_URL_V2 + "/groups/" + projectId + "/clusters/" + clusterName + "/backup/policy";
            String requestBody = objectMapper.writeValueAsString(policySpec);
            
            String responseBody = apiBase.makeApiRequest(url, HttpMethod.PATCH, requestBody, 
                                                       AtlasApiBase.API_VERSION_V2, projectId);
            
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            logger.info("Backup policy updated successfully for cluster '{}'", clusterName);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to update backup policy for cluster '{}' in project {}: {}", 
                        clusterName, projectId, e.getMessage());
            throw new AtlasApiBase.AtlasApiException(
                    "Failed to update backup policy for cluster '" + clusterName + "'", e);
        }
    }
}