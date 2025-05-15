package com.helmstetter.atlas.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles processing and optimization of SVG content
 */
public class SvgProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(SvgProcessor.class);
    
    private final int width;
    private final int height;
    
    // Regex patterns for SVG processing
    private final Pattern clipPattern = Pattern.compile("<clipPath id=\"([^\"]+)\"><rect ([^>]+)/></clipPath>");
    private final Pattern dimPattern = Pattern.compile("width=\"([^\"]+)\" height=\"([^\"]+)\"");
    private final Pattern transformPattern = Pattern.compile(
            "<g class=\"(plot|axis)\"([^>]*)transform=\"translate\\(([^,]+),([^)]+)\\)([^\"]*)\">"); 
    
    public SvgProcessor(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    /**
     * Apply all optimizations to an SVG element
     */
    public String optimize(String svgElement) {
        String optimized = svgElement;
        
        // Add viewBox and preserveAspectRatio if missing
        optimized = addSvgAttributes(optimized);
        
        // Optimize clip paths
        optimized = optimizeClipPaths(optimized);
        
        // Optimize transforms
        optimized = optimizeTransforms(optimized);
        
        return optimized;
    }
    
    /**
     * Add required SVG attributes if they are missing
     */
    private String addSvgAttributes(String svgElement) {
        String result = svgElement;
        
        // Add viewBox if missing
        if (!result.contains("viewBox=")) {
            result = result.replace("<svg ", 
                    String.format("<svg viewBox=\"0 0 %d %d\" ", width, height));
        }
        
        // Add preserveAspectRatio if missing
        if (!result.contains("preserveAspectRatio=")) {
            result = result.replace("<svg ", "<svg preserveAspectRatio=\"none\" ");
        }
        
        return result;
    }
    
    /**
     * Optimize clip paths to allow more chart area
     */
    private String optimizeClipPaths(String svgElement) {
        Matcher clipMatcher = clipPattern.matcher(svgElement);
        
        StringBuffer sb = new StringBuffer();
        while (clipMatcher.find()) {
            String clipId = clipMatcher.group(1);
            String rectAttrs = clipMatcher.group(2);
            
            // Extract width and height
            Matcher dimMatcher = dimPattern.matcher(rectAttrs);
            
            if (dimMatcher.find()) {
                try {
                    double width = Double.parseDouble(dimMatcher.group(1));
                    double height = Double.parseDouble(dimMatcher.group(2));
                    
                    // Increase dimensions by 10%
                    double newWidth = width * 1.10;
                    double newHeight = height * 1.10;
                    
                    // Replace with new dimensions
                    String newRectAttrs = rectAttrs.replace(
                            "width=\"" + dimMatcher.group(1) + "\" height=\"" + dimMatcher.group(2) + "\"",
                            String.format("width=\"%.1f\" height=\"%.1f\"", newWidth, newHeight)
                    );
                    
                    // Rebuild the clip path with new dimensions
                    clipMatcher.appendReplacement(sb, "<clipPath id=\"" + clipId + "\"><rect " + newRectAttrs + "/></clipPath>");
                    
                    logger.debug("Expanded clip path {} from {}x{} to {}x{}", 
                            clipId, width, height, newWidth, newHeight);
                } catch (NumberFormatException e) {
                    // If we can't parse the numbers, keep original
                    clipMatcher.appendReplacement(sb, clipMatcher.group(0));
                }
            } else {
                // No width/height found, keep original
                clipMatcher.appendReplacement(sb, clipMatcher.group(0));
            }
        }
        clipMatcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Optimize transform attributes to reduce margins
     */
    private String optimizeTransforms(String svgElement) {
        Matcher transformMatcher = transformPattern.matcher(svgElement);
        
        StringBuffer sb = new StringBuffer();
        while (transformMatcher.find()) {
            String elementClass = transformMatcher.group(1);
            String extraAttrs = transformMatcher.group(2);
            String translateX = transformMatcher.group(3);
            String translateY = transformMatcher.group(4);
            String restOfTransform = transformMatcher.group(5);
            
            try {
                double x = Double.parseDouble(translateX);
                double y = Double.parseDouble(translateY);
                
                // Different optimization depending on element type
                if ("plot".equals(elementClass)) {
                    // For plot elements, reduce margins but keep some space for axes
                    // Only reduce if they seem excessively large
                    if (x > 40) {
                        x = Math.max(35, x * 0.85); // Reduce but keep some margin
                    }
                    if (y > 20) {
                        y = Math.max(15, y * 0.85); // Reduce but keep some margin
                    }
                }
                
                // Rebuild transform with adjusted values
                transformMatcher.appendReplacement(sb, 
                    String.format("<g class=\"%s\"%stransform=\"translate(%.1f,%.1f)%s\">", 
                            elementClass, extraAttrs, x, y, restOfTransform));
                
            } catch (NumberFormatException e) {
                // Keep original if we can't parse the numbers
                transformMatcher.appendReplacement(sb, transformMatcher.group(0));
            }
        }
        transformMatcher.appendTail(sb);
        
        return sb.toString();
    }
}