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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

class HazelcastCounterTest {

    private HazelcastInstance hazelcastInstance;
    private HazelcastCounter counter;

    @BeforeEach
    void setUp() {
        // Create an isolated test instance of Hazelcast
        Config config = new Config();
        config.setClusterName("test-counter-cluster-" + System.currentTimeMillis());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        
        // Create the counter
        counter = new HazelcastCounter(hazelcastInstance, "test-counter");
    }

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInitialValue() {
        assertEquals(0, counter.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testIncrementAndGet() {
        assertEquals(1, counter.incrementAndGet());
        assertEquals(2, counter.incrementAndGet());
        assertEquals(2, counter.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDecrementAndGet() {
        counter.set(10);
        assertEquals(9, counter.decrementAndGet());
        assertEquals(8, counter.decrementAndGet());
        assertEquals(8, counter.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testAddAndGet() {
        assertEquals(5, counter.addAndGet(5));
        assertEquals(2, counter.addAndGet(-3));
        assertEquals(2, counter.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSetAndReset() {
        counter.set(100);
        assertEquals(100, counter.get());
        
        counter.reset();
        assertEquals(0, counter.get());
    }
}