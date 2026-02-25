/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

/**
 * Tests for {@link HazelcastCacheManager} covering methods not exercised by
 * map/queue adapter tests (cacheExists, clearCaches, getCacheSet,
 * special caches, change listeners, vendor).
 */
@Timeout(30)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastCacheManagerTest {

    private HazelcastInstance hz;
    private HazelcastCacheManager cacheManager;

    @BeforeAll
    void startHazelcast() {
        hz = HazelcastTestSupport.startNode();
        cacheManager = new HazelcastCacheManager(hz);
    }

    @AfterAll
    void stopHazelcast() {
        cacheManager.close();
    }

    // -----------------------------------------------------------------
    // getCacheMap / getCacheQueue / getCacheSet
    // -----------------------------------------------------------------

    @Test
    void testGetCacheMap_returnsNonNull() {
        var map = cacheManager.getCacheMap("mgr-test-map-" + UUID.randomUUID(),
                String.class);
        assertThat(map).isNotNull();
    }

    @Test
    void testGetCacheQueue_returnsNonNull() {
        var q = cacheManager.getCacheQueue(
                "mgr-test-queue-" + UUID.randomUUID(), String.class);
        assertThat(q).isNotNull();
    }

    @Test
    void testGetCacheSet_returnsNonNull() {
        var set = cacheManager.getCacheSet(
                "mgr-test-set-" + UUID.randomUUID());
        assertThat(set).isNotNull();
    }

    // -----------------------------------------------------------------
    // Special-purpose caches
    // -----------------------------------------------------------------

    @Test
    void testGetAdminCache_returnsNonNull() {
        assertThat(cacheManager.getAdminCache()).isNotNull();
    }

    @Test
    void testGetPipelineStepCache_returnsNonNull() {
        assertThat(cacheManager.getPipelineStepCache()).isNotNull();
    }

    @Test
    void testGetPipelineWorkerStatusCache_returnsNonNull() {
        assertThat(cacheManager.getPipelineWorkerStatusCache()).isNotNull();
    }

    // -----------------------------------------------------------------
    // cacheExists
    // -----------------------------------------------------------------

    @Test
    void testCacheExists_afterAccess_returnsTrue() {
        var name = "exists-test-" + UUID.randomUUID();
        // accessing a map creates it in HZ
        var map = cacheManager.getCacheMap(name, String.class);
        map.put("k", "v");
        assertThat(cacheManager.cacheExists(name)).isTrue();
    }

    @Test
    void testCacheExists_forNeverAccessedMap_returnsFalse() {
        // A randomly named map that was never accessed
        assertThat(
                cacheManager.cacheExists("never-accessed-" + UUID.randomUUID()))
                        .isFalse();
    }

    // -----------------------------------------------------------------
    // clearCaches
    // -----------------------------------------------------------------

    @Test
    void testClearCaches_removesAllEntries() {
        var map = cacheManager.getCacheMap("clear-test-" + UUID.randomUUID(),
                String.class);
        map.put("before-clear", "value");
        assertThat(map.isEmpty()).isFalse();

        cacheManager.clearCaches();

        assertThat(map.isEmpty()).isTrue();
    }

    // -----------------------------------------------------------------
    // vendor / getHazelcastInstance
    // -----------------------------------------------------------------

    @Test
    void testVendor_returnsHazelcastInstance() {
        assertThat(cacheManager.vendor()).isSameAs(hz);
    }

    @Test
    void testGetHazelcastInstace_byName_returnsInstance() {
        var instanceName = hz.getName();
        var found = HazelcastCacheManager.getHazelcastInstance(instanceName);
        assertThat(found).isNotNull();
    }

    @Test
    void testGetHazelcastInstance_unknownName_returnsNull() {
        var found = HazelcastCacheManager.getHazelcastInstance(
                "no-such-instance-" + UUID.randomUUID());
        assertThat(found).isNull();
    }

    // -----------------------------------------------------------------
    // addCacheEntryChangeListener / removeCacheEntryChangeListener
    // -----------------------------------------------------------------

    @Test
    void testCacheEntryChangeListener_registersAndTriggersOnPut()
            throws InterruptedException {
        var mapName = "listener-test-" + UUID.randomUUID();
        var map = cacheManager.getCacheMap(mapName, String.class);

        AtomicReference<String> captured = new AtomicReference<>();
        CacheEntryChangeListener<String> listener =
                (key, value) -> captured.set(key);

        cacheManager.addCacheEntryChangeListener(listener, mapName);
        map.put("trigger-key", "trigger-value");

        // Give the async listener time to fire
        Thread.sleep(200);
        assertThat(captured.get()).isEqualTo("trigger-key");

        // Remove listener — subsequent puts should not affect captured
        cacheManager.removeCacheEntryChangeListener(listener, mapName);
        map.put("silent-key", "silent-value");
        Thread.sleep(100);
        assertThat(captured.get()).isEqualTo("trigger-key"); // unchanged
    }

    @Test
    void testRemoveCacheEntryChangeListener_nonExistentListener_noError() {
        // Removing a listener that was never added should not throw
        var mapName = "rm-listener-" + UUID.randomUUID();
        cacheManager.getCacheMap(mapName, String.class); // ensure map exists
        CacheEntryChangeListener<StepRecord> notAdded = (key, value) -> {};
        // should complete without exception
        cacheManager.removeCacheEntryChangeListener(notAdded, mapName);
    }
}
