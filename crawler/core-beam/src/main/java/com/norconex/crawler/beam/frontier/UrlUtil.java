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
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Utility class for URL operations.
 * @author Norconex Inc.
 */
public class UrlUtil implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Extract the host from a URL.
     * @param url the URL string
     * @return the host name or IP address
     */
    public static String extractHost(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost();
        } catch (MalformedURLException e) {
            // Handle invalid URLs gracefully
            return url;
        }
    }
    
    /**
     * Calculate the depth of a URL based on its path components.
     * @param url the URL string
     * @return the depth of the URL path
     */
    public static int calculateDepth(String url) {
        if (url == null || url.isEmpty()) {
            return 0;
        }
        
        try {
            URL parsedUrl = new URL(url);
            String path = parsedUrl.getPath();
            
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return 0;
            }
            
            // Count segments separated by '/'
            String[] segments = path.split("/");
            int depth = 0;
            for (String segment : segments) {
                if (segment != null && !segment.isEmpty()) {
                    depth++;
                }
            }
            
            return depth;
            
        } catch (MalformedURLException e) {
            return 0;
        }
    }
    
    /**
     * Check if the URL is a seed URL (starting point).
     * @param url the frontier URL
     * @return true if it's a seed URL, false otherwise
     */
    public static boolean isSeedUrl(FrontierUrl url) {
        return url != null && url.isSeed();
    }
    
    /**
     * Resolves a relative URL against a base URL.
     * @param baseUrl the base URL
     * @param relativeUrl the relative URL
     * @return the absolute URL
     */
    public static String resolveUrl(String baseUrl, String relativeUrl) {
        if (baseUrl == null || relativeUrl == null) {
            return relativeUrl;
        }
        
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl; // Already absolute
        }
        
        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, relativeUrl);
            return resolved.toString();
        } catch (MalformedURLException e) {
            return relativeUrl; // Return as-is if resolution fails
        }
    }
}