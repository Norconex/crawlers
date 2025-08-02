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
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a URL in the crawl frontier.
 * @author Norconex Inc.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FrontierUrl implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String url;
    private String normalizedUrl;
    private String referrerUrl;
    private String host;
    private int depth;
    private int priority;
    private long discoveryTime;
    private int retryCount;
    private String tenantId;
    private boolean isSeed;
    
    /**
     * Create a new seed URL (a starting point for crawling).
     * @param url the URL string
     * @param tenantId the tenant ID for multi-tenant support
     * @return a new FrontierUrl instance
     */
    public static FrontierUrl createSeed(String url, String tenantId) {
        FrontierUrl frontierUrl = new FrontierUrl();
        frontierUrl.setUrl(url);
        frontierUrl.setNormalizedUrl(UrlNormalizer.normalize(url));
        frontierUrl.setHost(UrlUtil.extractHost(url));
        frontierUrl.setDepth(0);
        frontierUrl.setPriority(0); // Highest priority
        frontierUrl.setDiscoveryTime(System.currentTimeMillis());
        frontierUrl.setRetryCount(0);
        frontierUrl.setTenantId(tenantId);
        frontierUrl.setSeed(true);
        return frontierUrl;
    }
    
    /**
     * Create a new discovered URL.
     * @param url the URL string
     * @param referrerUrl the referrer URL
     * @param depth the crawl depth
     * @param priority the priority level (0 is highest)
     * @param tenantId the tenant ID for multi-tenant support
     * @return a new FrontierUrl instance
     */
    public static FrontierUrl createDiscovered(
            String url, String referrerUrl, int depth, int priority, String tenantId) {
        FrontierUrl frontierUrl = new FrontierUrl();
        frontierUrl.setUrl(url);
        frontierUrl.setNormalizedUrl(UrlNormalizer.normalize(url));
        frontierUrl.setReferrerUrl(referrerUrl);
        frontierUrl.setHost(UrlUtil.extractHost(url));
        frontierUrl.setDepth(depth);
        frontierUrl.setPriority(priority);
        frontierUrl.setDiscoveryTime(System.currentTimeMillis());
        frontierUrl.setRetryCount(0);
        frontierUrl.setTenantId(tenantId);
        frontierUrl.setSeed(false);
        return frontierUrl;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrontierUrl that = (FrontierUrl) o;
        return Objects.equals(normalizedUrl, that.normalizedUrl) &&
               Objects.equals(tenantId, that.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(normalizedUrl, tenantId);
    }
}