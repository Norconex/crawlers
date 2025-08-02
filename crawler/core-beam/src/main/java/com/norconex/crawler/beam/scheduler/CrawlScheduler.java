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

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.beam.frontier.FrontierUrl;

/**
 * The scheduler component that manages politeness and scheduling of URLs for fetching.
 * @author Norconex Inc.
 */
public class CrawlScheduler implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CrawlScheduler.class);
    
    private final SchedulerConfig config;
    private final Map<String, HostState> hostStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService;
    private final RobotsTxtCache robotsCache;
    
    /**
     * Create a new crawler scheduler with the specified configuration.
     * @param config the scheduler configuration
     */
    public CrawlScheduler(SchedulerConfig config) {
        this.config = config;
        this.executorService = Executors.newScheduledThreadPool(config.getSchedulerThreads());
        this.robotsCache = new RobotsTxtCache();
        
        // Start a cleanup task for expired host states
        executorService.scheduleAtFixedRate(
            this::cleanupExpiredHostStates, 
            30, 30, TimeUnit.SECONDS
        );
    }
    
    /**
     * Check if a URL is allowed to be fetched according to politeness rules.
     * @param url the URL to check
     * @return true if the URL can be fetched, false otherwise
     */
    public boolean canFetch(FrontierUrl url) {
        if (url == null) {
            return false;
        }
        
        String host = url.getHost();
        HostState hostState = getOrCreateHostState(host);
        
        synchronized (hostState) {
            // Check if we're already fetching the maximum number of URLs for this host
            if (hostState.getActiveFetches() >= config.getMaxRequestsPerHost()) {
                return false;
            }
            
            // Check if enough time has passed since the last fetch
            long now = System.currentTimeMillis();
            if (now - hostState.getLastFetchTime() < calculatePolitenesDelayForHost(host)) {
                return false;
            }
            
            // Check robots.txt if enabled
            if (config.isRespectRobotsTxt() && !isAllowedByRobots(url)) {
                LOG.debug("URL blocked by robots.txt: {}", url.getUrl());
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * Notify the scheduler that a fetch has started for a URL.
     * @param url the URL being fetched
     */
    public void notifyFetchStarted(FrontierUrl url) {
        if (url == null) {
            return;
        }
        
        String host = url.getHost();
        HostState hostState = getOrCreateHostState(host);
        
        synchronized (hostState) {
            hostState.setLastFetchTime(System.currentTimeMillis());
            hostState.setActiveFetches(hostState.getActiveFetches() + 1);
        }
    }
    
    /**
     * Notify the scheduler that a fetch has completed for a URL.
     * @param url the URL that was fetched
     * @param success whether the fetch was successful
     * @param responseTimeMs the time taken for the response
     */
    public void notifyFetchCompleted(FrontierUrl url, boolean success, long responseTimeMs) {
        if (url == null) {
            return;
        }
        
        String host = url.getHost();
        HostState hostState = getOrCreateHostState(host);
        
        synchronized (hostState) {
            hostState.setActiveFetches(hostState.getActiveFetches() - 1);
            hostState.setLastActive(System.currentTimeMillis());
            
            if (success) {
                hostState.setSuccessfulFetches(hostState.getSuccessfulFetches() + 1);
                
                // Update average response time for adaptive politeness
                if (config.isEnableAdaptivePoliteness() && responseTimeMs > 0) {
                    long currentAvg = hostState.getAverageResponseTimeMs();
                    long newFetchCount = hostState.getSuccessfulFetches();
                    
                    if (currentAvg == 0) {
                        hostState.setAverageResponseTimeMs(responseTimeMs);
                    } else {
                        // Weighted moving average
                        long newAvg = (currentAvg * (newFetchCount - 1) + responseTimeMs) / newFetchCount;
                        hostState.setAverageResponseTimeMs(newAvg);
                    }
                }
            } else {
                hostState.setFailedFetches(hostState.getFailedFetches() + 1);
            }
        }
    }
    
    /**
     * Shutdown the scheduler and release resources.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Create a Beam transform for scheduling URLs based on politeness rules.
     * @return the scheduler transform
     */
    public PTransform<PCollection<FrontierUrl>, PCollection<KV<String, FrontierUrl>>> 
            createSchedulerTransform() {
        return new SchedulerTransform();
    }
    
    private HostState getOrCreateHostState(String host) {
        return hostStates.computeIfAbsent(host, h -> new HostState());
    }
    
    private long calculatePolitenesDelayForHost(String host) {
        HostState hostState = hostStates.get(host);
        if (hostState == null || !config.isEnableAdaptivePoliteness()) {
            return config.getPolitenessDelayMs();
        }
        
        // For adaptive politeness, use the host's average response time to adjust delay
        // Slow sites get more delay to avoid overloading them
        long avgResponseTime = hostState.getAverageResponseTimeMs();
        if (avgResponseTime <= 0) {
            return config.getPolitenessDelayMs();
        }
        
        // Scale politeness delay based on response time
        // Fast sites (< 100ms): use minimum delay
        // Slow sites: use proportionally more delay, up to a reasonable maximum
        if (avgResponseTime < 100) {
            return config.getPolitenessDelayMs();
        } else {
            return Math.min(
                config.getPolitenessDelayMs() * (avgResponseTime / 100),
                config.getPolitenessDelayMs() * 5 // Cap at 5x the configured delay
            );
        }
    }
    
    private boolean isAllowedByRobots(FrontierUrl url) {
        try {
            return robotsCache.isAllowed(url.getUrl(), "NorconexBeamCrawler");
        } catch (Exception e) {
            LOG.warn("Error checking robots.txt for {}: {}", url.getUrl(), e.getMessage());
            return true; // Allow by default if robots.txt check fails
        }
    }
    
    private void cleanupExpiredHostStates() {
        long now = System.currentTimeMillis();
        long timeout = config.getIdleTimeoutMs();
        
        hostStates.entrySet().removeIf(entry -> {
            HostState state = entry.getValue();
            return state.getActiveFetches() == 0 && 
                   now - state.getLastActive() > timeout;
        });
    }
    
    /**
     * Beam transform for the scheduler.
     */
    private class SchedulerTransform extends 
            PTransform<PCollection<FrontierUrl>, PCollection<KV<String, FrontierUrl>>> {
        private static final long serialVersionUID = 1L;

        @Override
        public PCollection<KV<String, FrontierUrl>> expand(PCollection<FrontierUrl> input) {
            return input.apply("ScheduleUrls", ParDo.of(new DoFn<FrontierUrl, KV<String, FrontierUrl>>() {
                private static final long serialVersionUID = 1L;
                
                @ProcessElement
                public void processElement(@Element FrontierUrl url, OutputReceiver<KV<String, FrontierUrl>> out) {
                    if (canFetch(url)) {
                        notifyFetchStarted(url);
                        out.output(KV.of(url.getHost(), url));
                    }
                }
            }));
        }
    }
}