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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core2.cluster.Cache;

class HazelcastCacheAdapterTest {

    private HazelcastInstance hazelcastInstance;
    private HazelcastCacheAdapter<TestObject> cache;

    @BeforeEach
    void setUp() {
        // Create an isolated test instance of Hazelcast
        Config config = new Config();
        config.setClusterName("test-cache-cluster-" + System.currentTimeMillis());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        
        // Create the cache adapter
        cache = new HazelcastCacheAdapter<>(hazelcastInstance, "test-cache", TestObject.class);
    }

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutAndGet() {
        // Given
        TestObject obj = new TestObject("test1", 123);
        
        // When
        cache.put("key1", obj);
        Optional<TestObject> retrieved = cache.get("key1");
        
        // Then
        assertTrue(retrieved.isPresent());
        assertEquals("test1", retrieved.get().getName());
        assertEquals(123, retrieved.get().getValue());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutWithTTL() throws InterruptedException {
        // Given
        TestObject obj = new TestObject("test2", 456);
        
        // When
        cache.put("key2", obj, 500); // TTL of 500ms
        Optional<TestObject> beforeExpiry = cache.get("key2");
        
        // Wait for expiration
        Thread.sleep(1000);
        Optional<TestObject> afterExpiry = cache.get("key2");
        
        // Then
        assertTrue(beforeExpiry.isPresent());
        assertFalse(afterExpiry.isPresent());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testContainsAndRemove() {
        // Given
        TestObject obj = new TestObject("test3", 789);
        cache.put("key3", obj);
        
        // When & Then
        assertTrue(cache.contains("key3"));
        
        // When
        cache.remove("key3");
        
        // Then
        assertFalse(cache.contains("key3"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testClearAndSize() {
        // Given
        cache.put("key1", new TestObject("test1", 1));
        cache.put("key2", new TestObject("test2", 2));
        cache.put("key3", new TestObject("test3", 3));
        
        // When & Then
        assertEquals(3, cache.size());
        
        // When
        cache.clear();
        
        // Then
        assertEquals(0, cache.size());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetKeysMatching() {
        // Given
        cache.put("prefix_key1", new TestObject("test1", 1));
        cache.put("prefix_key2", new TestObject("test2", 2));
        cache.put("other_key", new TestObject("test3", 3));
        
        // When
        Set<String> matchingKeys = cache.getKeysMatching("prefix_.*");
        
        // Then
        assertThat(matchingKeys).hasSize(2);
        assertThat(matchingKeys).contains("prefix_key1", "prefix_key2");
        assertThat(matchingKeys).doesNotContain("other_key");
    }
    
    // Simple test object class
    public static class TestObject {
        private String name;
        private int value;
        
        // Default constructor needed for serialization
        public TestObject() {}
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() {
            return name;
        }
        
        public int getValue() {
            return value;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public void setValue(int value) {
            this.value = value;
        }
    }
}