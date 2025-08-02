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
package com.norconex.crawler.beam.scheduler;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;

/**
 * Caches robots.txt rules to avoid repeatedly fetching them.
 * @author Norconex Inc.
 */
public class RobotsTxtCache implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(RobotsTxtCache.class);
    
    private final transient Cache<String, BaseRobotRules> cache;
    private final transient Map<String, Boolean> failedHosts;
    
    /**
     * Creates a new robots.txt cache.
     */
    public RobotsTxtCache() {
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(10000)
                .build();
        failedHosts = new ConcurrentHashMap<>();
    }
    
    /**
     * Check if a URL is allowed by the site's robots.txt file.
     * @param urlString the URL to check
     * @param userAgent the user agent to check against
     * @return true if the URL is allowed, false otherwise
     * @throws IOException if an error occurs fetching the robots.txt file
     */
    public boolean isAllowed(String urlString, String userAgent) throws IOException {
        if (urlString == null || urlString.isEmpty()) {
            return true;
        }
        
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            
            // Skip check for hosts that previously failed
            if (failedHosts.containsKey(host)) {
                return true;
            }
            
            BaseRobotRules rules = getRobotRules(url, userAgent);
            return rules.isAllowed(urlString);
            
        } catch (MalformedURLException e) {
            LOG.debug("Invalid URL for robots.txt check: {}", urlString);
            return true;
        }
    }
    
    /**
     * Get the crawl delay specified in robots.txt for a URL.
     * @param urlString the URL to check
     * @param userAgent the user agent to check against
     * @return the crawl delay in milliseconds, or 0 if none specified
     * @throws IOException if an error occurs fetching the robots.txt file
     */
    public long getCrawlDelay(String urlString, String userAgent) throws IOException {
        if (urlString == null || urlString.isEmpty()) {
            return 0;
        }
        
        try {
            URL url = new URL(urlString);
            BaseRobotRules rules = getRobotRules(url, userAgent);
            return rules.getCrawlDelay() * 1000; // Convert seconds to milliseconds
            
        } catch (MalformedURLException e) {
            LOG.debug("Invalid URL for robots.txt crawl delay check: {}", urlString);
            return 0;
        }
    }
    
    private BaseRobotRules getRobotRules(URL url, String userAgent) throws IOException {
        String host = url.getHost();
        String cacheKey = host + ":" + userAgent;
        
        BaseRobotRules rules = cache.getIfPresent(cacheKey);
        if (rules != null) {
            return rules;
        }
        
        rules = fetchRobotRules(url, userAgent);
        cache.put(cacheKey, rules);
        
        return rules;
    }
    
    private BaseRobotRules fetchRobotRules(URL url, String userAgent) {
        String host = url.getHost();
        String protocol = url.getProtocol();
        String robotsUrl = protocol + "://" + host + "/robots.txt";
        
        SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
        
        try {
            // Use crawlercommons RobotsParser
            SimpleRobotRules rules = fetchAndParseRobotsTxt(robotsUrl, userAgent, parser);
            return rules;
            
        } catch (Exception e) {
            LOG.debug("Failed to fetch robots.txt for {}: {}", host, e.getMessage());
            failedHosts.put(host, Boolean.TRUE);
            
            // Return rules that allow everything if robots.txt can't be fetched
            return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
        }
    }
    
    private SimpleRobotRules fetchAndParseRobotsTxt(
            String robotsUrl, String userAgent, SimpleRobotRulesParser parser) {
        // This is a simplified implementation
        // In a real implementation, use a proper HTTP client to fetch robots.txt
        
        // For the sake of this skeleton, we're returning a permissive robots.txt
        // The actual implementation would fetch and parse the real robots.txt
        
        return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
    }
}