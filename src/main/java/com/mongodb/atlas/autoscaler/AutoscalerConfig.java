package com.mongodb.atlas.autoscaler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration class for the autoscaler
 */
public class AutoscalerConfig {
    private final Set<String> projectNames;
    private final List<ScalingRule> scalingRules;
    private final int monitoringIntervalSeconds;
    private final boolean dryRun;
	private boolean scaleAllShardsInUnison;
    
    public AutoscalerConfig(Set<String> projectNames, List<ScalingRule> scalingRules, 
                          int monitoringIntervalSeconds, boolean dryRun) {
        this.projectNames = new HashSet<>(projectNames);
        this.scalingRules = new ArrayList<>(scalingRules);
        this.monitoringIntervalSeconds = monitoringIntervalSeconds;
        this.dryRun = dryRun;
    }
    
    public boolean isScaleAllShardsInUnison() { 
    	   return scaleAllShardsInUnison; 
    	}
    
    public Set<String> getProjectNames() { return projectNames; }
    public List<ScalingRule> getScalingRules() { return scalingRules; }
    public int getMonitoringIntervalSeconds() { return monitoringIntervalSeconds; }
    public boolean isDryRun() { return dryRun; }
}