/*
 * Copyright 2014-2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.beam.frontier;

import java.io.Serializable;

/**
 * Calculates priority for URLs in the frontier.
 * @author Norconex Inc.
 */
public class UrlPrioritizer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int priorityLevels;
    
    /**
     * Creates a URL prioritizer with the specified number of priority levels.
     * @param priorityLevels the number of priority levels (0 is highest priority)
     */
    public UrlPrioritizer(int priorityLevels) {
        this.priorityLevels = Math.max(1, priorityLevels);
    }
    
    /**
     * Calculate the priority for a URL.
     * @param url the frontier URL
     * @return the priority level (0 is highest)
     */
    public int calculatePriority(FrontierUrl url) {
        if (url == null) {
            return priorityLevels - 1; // Lowest priority
        }
        
        // Seed URLs always get highest priority
        if (url.isSeed()) {
            return 0;
        }
        
        // Calculate priority based on depth
        // Breadth-first approach: lower depth = higher priority
        int depthPriority = Math.min(url.getDepth(), priorityLevels - 1);
        
        // We can incorporate other factors like URL patterns, content type expectations, etc.
        // For now, we just use depth-based priority
        
        return depthPriority;
    }
    
    /**
     * Calculate priority based on URL path features.
     * @param url the URL string
     * @return the priority adjustment (-1 for higher priority, 0 for neutral, 1 for lower priority)
     */
    public int calculatePathPriority(String url) {
        if (url == null || url.isEmpty()) {
            return 0;
        }
        
        // Example: prioritize "index" pages
        if (url.matches(".*/(index|home)\\.(html|htm|php|jsp|asp)$")) {
            return -1; // Higher priority
        }
        
        // Example: lower priority for file downloads
        if (url.matches(".+\\.(pdf|doc|docx|xls|xlsx|zip|rar|gz)$")) {
            return 1; // Lower priority
        }
        
        return 0; // Neutral priority
    }
}