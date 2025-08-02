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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.Counter;

/**
 * Hazelcast implementation of the CacheManager interface.
 */
public class HazelcastCacheManager implements CacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastCacheManager.class);
    
    private final HazelcastInstance hazelcastInstance;
    private final ConcurrentMap<String, Cache<?>> caches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    
    public HazelcastCacheManager(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = Objects.requireNonNull(
                hazelcastInstance, "Hazelcast instance cannot be null");
        LOG.info("Hazelcast cache manager initialized");
    }

    @Override
    public <T> Cache<T> getCache(String name, Class<T> type) {
        Objects.requireNonNull(name, "Cache name cannot be null");
        Objects.requireNonNull(type, "Cache type cannot be null");
        
        @SuppressWarnings("unchecked")
        Cache<T> cache = (Cache<T>) caches.computeIfAbsent(name, 
                n -> new HazelcastCacheAdapter<>(hazelcastInstance, n, type));
        
        return cache;
    }

    @Override
    public Counter getCounter(String name) {
        Objects.requireNonNull(name, "Counter name cannot be null");
        
        return counters.computeIfAbsent(name, 
                n -> new HazelcastCounter(hazelcastInstance, n));
    }

    @Override
    public Lock getLock(String name) {
        Objects.requireNonNull(name, "Lock name cannot be null");
        return hazelcastInstance.getCPSubsystem().getLock(name);
    }

    @Override
    public void clearAll() {
        LOG.info("Clearing all caches and counters...");
        try {
            for (Cache<?> cache : caches.values()) {
                try {
                    cache.clear();
                } catch (Exception e) {
                    LOG.error("Error clearing cache: {}", cache, e);
                }
            }
            for (Counter counter : counters.values()) {
                try {
                    counter.reset();
                } catch (Exception e) {
                    LOG.error("Error resetting counter: {}", counter, e);
                }
            }
        } catch (Exception e) {
            throw new CacheException("Failed to clear all caches and counters", e);
        }
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down Hazelcast cache manager...");
        caches.clear();
        counters.clear();
        // The actual Hazelcast instance is shutdown by the HazelcastCluster
    }
}