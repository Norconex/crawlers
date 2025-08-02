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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.ClusterNode;
import com.norconex.crawler.core2.cluster.Counter;

class HazelcastClusterTest {

    private HazelcastCluster cluster;
    private HazelcastClusterConfig config;

    @BeforeEach
    void setUp() {
        config = new HazelcastClusterConfig();
        config.setClusterName("test-integration-cluster-" + System.currentTimeMillis());
        cluster = new HazelcastCluster(config);
    }

    @AfterEach
    void tearDown() {
        if (cluster != null) {
            cluster.shutdown();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testClusterInitialization() {
        // Then
        assertNotNull(cluster.getNodeId());
        assertThat(cluster.getNodes()).isNotEmpty();
        assertEquals(1, cluster.getNodes().size());
        
        // Verify the node represents this instance
        ClusterNode localNode = cluster.getNodes().iterator().next();
        assertTrue(localNode.isLocal());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCacheManager() {
        // Given
        Cache<String> cache = cluster.getCacheManager().getCache("test-cache", String.class);
        
        // When
        cache.put("key1", "value1");
        Optional<String> result = cache.get("key1");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCounter() {
        // Given
        Counter counter = cluster.getCacheManager().getCounter("test-counter");
        
        // When
        long value = counter.incrementAndGet();
        
        // Then
        assertEquals(1, value);
        assertEquals(1, counter.get());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetLock() {
        // Given
        var lock = cluster.getCacheManager().getLock("test-lock");
        
        // When/Then - verify we can acquire and release the lock
        lock.lock();
        try {
            assertTrue(lock.isLocked());
        } finally {
            lock.unlock();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testTaskManager() throws Exception {
        // Given
        var future = cluster.getTaskManager().submitTask(() -> "task-result");
        
        // When
        String result = future.get(5, TimeUnit.SECONDS);
        
        // Then
        assertEquals("task-result", result);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testClearAll() {
        // Given
        Cache<String> cache1 = cluster.getCacheManager().getCache("test-cache1", String.class);
        Cache<String> cache2 = cluster.getCacheManager().getCache("test-cache2", String.class);
        cache1.put("key1", "value1");
        cache2.put("key2", "value2");
        
        Counter counter = cluster.getCacheManager().getCounter("test-counter");
        counter.set(10);
        
        // When
        cluster.getCacheManager().clearAll();
        
        // Then
        assertEquals(0, cache1.size());
        assertEquals(0, cache2.size());
        assertEquals(0, counter.get());
    }
}