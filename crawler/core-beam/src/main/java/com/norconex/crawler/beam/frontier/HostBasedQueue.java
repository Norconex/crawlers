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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a separate queue for each host to ensure politeness.
 * @author Norconex Inc.
 */
public class HostBasedQueue implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(HostBasedQueue.class);
    
    private final int queueCapacity;
    private final Map<String, HostQueue> hostQueues = new ConcurrentHashMap<>();
    private final AtomicLong totalUrls = new AtomicLong(0);
    private final Queue<String> hostRotation = new PriorityQueue<>();
    
    /**
     * Creates a host-based queue with the specified capacity.
     * @param queueCapacity the maximum number of URLs per host queue
     */
    public HostBasedQueue(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    
    /**
     * Add a URL to its host queue.
     * @param url the URL to enqueue
     * @return true if the URL was added, false if the queue is full
     */
    public boolean enqueue(FrontierUrl url) {
        if (url == null || url.getHost() == null) {
            return false;
        }
        
        String host = url.getHost();
        HostQueue queue = hostQueues.computeIfAbsent(host, h -> new HostQueue());
        
        boolean added = queue.add(url);
        if (added) {
            totalUrls.incrementAndGet();
            synchronized (hostRotation) {
                if (!hostRotation.contains(host)) {
                    hostRotation.add(host);
                }
            }
        }
        return added;
    }
    
    /**
     * Get the next URL to process, maintaining politeness by rotating hosts.
     * @return the next URL or null if no URLs are available
     */
    public FrontierUrl dequeue() {
        if (isEmpty()) {
            return null;
        }
        
        synchronized (hostRotation) {
            if (hostRotation.isEmpty()) {
                return null;
            }
            
            // Round-robin among hosts
            String host = hostRotation.poll();
            HostQueue queue = hostQueues.get(host);
            FrontierUrl url = queue.poll();
            
            // If the host queue still has URLs, add it back to rotation
            if (!queue.isEmpty()) {
                hostRotation.add(host);
            }
            
            if (url != null) {
                totalUrls.decrementAndGet();
            }
            
            return url;
        }
    }
    
    /**
     * Returns the total number of URLs in all host queues.
     * @return the total number of URLs
     */
    public long size() {
        return totalUrls.get();
    }
    
    /**
     * Check if all host queues are empty.
     * @return true if all queues are empty, false otherwise
     */
    public boolean isEmpty() {
        return totalUrls.get() == 0;
    }
    
    /**
     * Get a snapshot of all URLs for a specific host.
     * @param host the host name
     * @return list of URLs for the host
     */
    public List<FrontierUrl> getUrlsForHost(String host) {
        HostQueue queue = hostQueues.get(host);
        if (queue == null) {
            return List.of();
        }
        
        return queue.getSnapshot();
    }
    
    /**
     * Get a map of host names to their queue sizes.
     * @return map of host to queue size
     */
    public Map<String, Integer> getHostQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        
        for (Map.Entry<String, HostQueue> entry : hostQueues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        
        return sizes;
    }
    
    /**
     * Per-host queue with priority ordering.
     */
    private class HostQueue implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final PriorityQueue<FrontierUrl> queue = new PriorityQueue<>(
            (a, b) -> Integer.compare(a.getPriority(), b.getPriority())
        );
        
        public synchronized boolean add(FrontierUrl url) {
            if (queue.size() >= queueCapacity) {
                LOG.debug("Host queue for {} is full, URL discarded: {}", 
                        url.getHost(), url.getUrl());
                return false;
            }
            return queue.offer(url);
        }
        
        public synchronized FrontierUrl poll() {
            return queue.poll();
        }
        
        public synchronized boolean isEmpty() {
            return queue.isEmpty();
        }
        
        public synchronized int size() {
            return queue.size();
        }
        
        public synchronized List<FrontierUrl> getSnapshot() {
            return new ArrayList<>(queue);
        }
    }
}