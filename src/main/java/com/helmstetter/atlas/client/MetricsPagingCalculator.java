package com.helmstetter.atlas.client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class MetricsPagingCalculator {
    /**
     * Parse granularity to seconds
     * @param granularity ISO 8601 duration string (e.g., "PT10S", "PT1M")
     * @return Number of seconds between data points
     */
    public static int parseGranularityToSeconds(String granularity) {
        if (granularity == null) {
            throw new IllegalArgumentException("Granularity cannot be null");
        }
        
        // Remove "PT" prefix and parse the number
        String valueStr = granularity.substring(2, granularity.length() - 1);
        int value = Integer.parseInt(valueStr);
        
        // Determine unit (seconds or minutes)
        char unit = granularity.charAt(granularity.length() - 1);
        
        switch (unit) {
            case 'S': // Seconds
                return value;
            case 'M': // Minutes
                return value * 60;
            default:
                throw new IllegalArgumentException("Unsupported granularity unit: " + unit);
        }
    }
    
    /**
     * Calculate total expected data points in a time window
     * @param startTime Start of the time window
     * @param endTime End of the time window
     * @param granularity Granularity of measurements
     * @return Total number of expected data points
     */
    public static int calculateExpectedDataPoints(
            Instant startTime, 
            Instant endTime, 
            String granularity) {
        
        // Validate inputs
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start and end times must not be null");
        }
        
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        
        // Calculate total seconds in the time window
        long totalSeconds = ChronoUnit.SECONDS.between(startTime, endTime);
        
        // Get seconds between data points
        int granularitySeconds = parseGranularityToSeconds(granularity);
        
        // Calculate expected number of data points
        // Add 1 to include both start and end points
        int expectedDataPoints = (int) (totalSeconds / granularitySeconds) + 1;
        
        return expectedDataPoints;
    }
    
    /**
     * Calculate expected number of pages
     * @param totalDataPoints Total number of data points
     * @param pageSize Number of data points per page
     * @return Number of pages
     */
    public static int calculateExpectedPages(int totalDataPoints, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        
        // Calculate pages, rounding up
        return (int) Math.ceil((double) totalDataPoints / pageSize);
    }
    
    /**
     * Print detailed breakdown of expected data collection
     */
    public static void printDataCollectionBreakdown(
            Instant startTime, 
            Instant endTime, 
            String granularity, 
            int pageSize) {
        
        int expectedDataPoints = calculateExpectedDataPoints(startTime, endTime, granularity);
        int expectedPages = calculateExpectedPages(expectedDataPoints, pageSize);
        
        System.out.println("Data Collection Breakdown:");
        System.out.println("-------------------------");
        System.out.println("Start Time: " + startTime);
        System.out.println("End Time: " + endTime);
        System.out.println("Time Window: " + ChronoUnit.SECONDS.between(startTime, endTime) + " seconds");
        System.out.println("Granularity: " + granularity);
        System.out.println("Expected Data Points: " + expectedDataPoints);
        System.out.println("Page Size: " + pageSize);
        System.out.println("Expected Pages: " + expectedPages);
    }
    
    /**
     * Validate actual data collection against expectations
     */
    public static void validateDataCollection(
            Instant startTime, 
            Instant endTime, 
            String granularity, 
            int pageSize, 
            int actualPages, 
            int actualDataPoints) {
        
        int expectedDataPoints = calculateExpectedDataPoints(startTime, endTime, granularity);
        int expectedPages = calculateExpectedPages(expectedDataPoints, pageSize);
        
        System.out.println("Data Collection Validation:");
        System.out.println("-------------------------");
        System.out.println("Expected Data Points: " + expectedDataPoints);
        System.out.println("Actual Data Points: " + actualDataPoints);
        System.out.println("Expected Pages: " + expectedPages);
        System.out.println("Actual Pages: " + actualPages);
        
        // Validate data points
        double percentageDifference = Math.abs(expectedDataPoints - actualDataPoints) / 
                (double) expectedDataPoints * 100.0;
        
        if (percentageDifference > 10) {
            System.err.println("WARNING: Significant deviation in data points!");
            System.err.printf("Percentage Difference: %.2f%%\n", percentageDifference);
        }
        
        // Validate pages
        if (actualPages > expectedPages * 1.2) {
            System.err.println("WARNING: More pages fetched than expected!");
        }
    }
}
