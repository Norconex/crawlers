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
import java.util.List;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The URL Frontier manages the list of URLs to be crawled.
 * It provides prioritization and host-based queuing to support politeness.
 * @author Norconex Inc.
 */
public class UrlFrontier implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(UrlFrontier.class);

    private final FrontierConfig config;
    private final UrlPrioritizer prioritizer;
    private final HostBasedQueue hostQueues;
    
    /**
     * Creates a new URL Frontier with the given configuration.
     * @param config the frontier configuration
     */
    public UrlFrontier(FrontierConfig config) {
        this.config = config;
        this.prioritizer = new UrlPrioritizer(config.getPriorityLevels());
        this.hostQueues = new HostBasedQueue(config.getQueueCapacity());
    }
    
    /**
     * Add a single URL to the frontier.
     * @param url the URL to add
     */
    public void add(FrontierUrl url) {
        if (url == null || url.getUrl() == null) {
            return;
        }
        
        int priority = prioritizer.calculatePriority(url);
        url.setPriority(priority);
        
        hostQueues.enqueue(url);
        LOG.debug("Added URL to frontier: {}", url.getUrl());
    }
    
    /**
     * Add multiple URLs to the frontier.
     * @param urls the URLs to add
     */
    public void addAll(List<FrontierUrl> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        
        for (FrontierUrl url : urls) {
            add(url);
        }
        LOG.debug("Added {} URLs to frontier", urls.size());
    }
    
    /**
     * Get the next URL to crawl, respecting host politeness.
     * @return the next URL or null if none available
     */
    public FrontierUrl getNext() {
        return hostQueues.dequeue();
    }
    
    /**
     * Returns the number of URLs in the frontier.
     * @return the size of the frontier
     */
    public long size() {
        return hostQueues.size();
    }
    
    /**
     * Check if the frontier is empty.
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return hostQueues.isEmpty();
    }
    
    /**
     * Create a Beam transform for processing URLs in the frontier.
     * @return a PTransform that processes URLs from the frontier
     */
    public PTransform<PCollection<String>, PCollection<FrontierUrl>> createUrlProcessor() {
        return new UrlProcessor();
    }
    
    /**
     * Beam PTransform implementation for URL frontier processing.
     */
    private class UrlProcessor extends PTransform<PCollection<String>, PCollection<FrontierUrl>> {
        private static final long serialVersionUID = 1L;
        
        @Override
        public PCollection<FrontierUrl> expand(PCollection<String> input) {
            return input.apply("ProcessUrlFrontier", ParDo.of(new DoFn<String, FrontierUrl>() {
                private static final long serialVersionUID = 1L;
                
                @ProcessElement
                public void processElement(@Element String url, OutputReceiver<FrontierUrl> out) {
                    FrontierUrl frontierUrl = FrontierUrl.createSeed(url, "default");
                    int priority = prioritizer.calculatePriority(frontierUrl);
                    frontierUrl.setPriority(priority);
                    out.output(frontierUrl);
                }
            }));
        }
    }
}