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
package com.norconex.crawler.core.cluster;

import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.junit.WithTestWatcherLogging;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests to verify that Infinispan cache replication is working correctly
 * across multiple nodes in a cluster.
 */
@Timeout(60)
@WithTestWatcherLogging
@Slf4j
class CacheReplicationTest {

    //    /**
    //     * Tests that when node1 writes to a cache, node2 can immediately
    //     * read that value. This verifies synchronous replication is working.
    //     */
    //    @ClusterNodesTest(nodes = 2)
    //    void testSynchronousReplication(
    //            int nodeCount, List<CrawlSession> sessions) {
    //
    //        var cacheName = ClusterTestUtil.uniqueCacheName("test-sync-repl");
    //        var testKey = "test-key";
    //        var testValue = "test-value";
    //
    //        // Get cache references from both nodes
    //        var cache1 = ClusterTestUtil.stringCache(sessions.get(0), cacheName);
    //        var cache2 = ClusterTestUtil.stringCache(sessions.get(1), cacheName);
    //
    //        // Node 1 writes
    //        LOG.info("Node 1 writing key '{}' with value '{}'",
    //                testKey, testValue);
    //        cache1.put(testKey, testValue);
    //
    //        // Node 2 should be able to read it
    //        assertThatNoException().isThrownBy(() -> {
    //            var maxWaitMs = 5000;
    //            var elapsed = 0L;
    //            while (elapsed < maxWaitMs) {
    //                var readValue = cache2.get(testKey).orElse(null);
    //                if (testValue.equals(readValue)) {
    //                    LOG.info("Node 2 successfully read value after {}ms",
    //                            elapsed);
    //                    return;
    //                }
    //                Sleeper.sleepMillis(100);
    //                elapsed += 100;
    //            }
    //            throw new AssertionError(
    //                    "Node 2 did not see value from Node 1 after "
    //                            + maxWaitMs + "ms");
    //        });
    //
    //        var finalValue = cache2.get(testKey).orElse(null);
    //        assertThat(finalValue)
    //                .as("Node 2 should see the value written by Node 1")
    //                .isEqualTo(testValue);
    //    }
    //
    //    /**
    //     * Tests that multiple writes from different nodes all get replicated
    //     * to all nodes in the cluster.
    //     */
    //    @ClusterNodesTest(nodes = 2)
    //    void testBidirectionalReplication(
    //            int nodeCount, List<CrawlSession> sessions) {
    //
    //        var cacheName = ClusterTestUtil.uniqueCacheName("test-bidir-repl");
    //
    //        // Get cache references from both nodes
    //        var cache1 = ClusterTestUtil.stringCache(sessions.get(0), cacheName);
    //        var cache2 = ClusterTestUtil.stringCache(sessions.get(1), cacheName);
    //
    //        // Node 1 writes key1
    //        LOG.info("Node 1 writing key1");
    //        cache1.put("key1", "value-from-node1");
    //
    //        // Node 2 writes key2
    //        LOG.info("Node 2 writing key2");
    //        cache2.put("key2", "value-from-node2");
    //
    //        // Both nodes should see both keys
    //        assertThatNoException().isThrownBy(() -> {
    //            var maxWaitMs = 5000;
    //            var elapsed = 0L;
    //            while (elapsed < maxWaitMs) {
    //                var key1OnNode2 = cache2.get("key1").orElse(null);
    //                var key2OnNode1 = cache1.get("key2").orElse(null);
    //
    //                if ("value-from-node1".equals(key1OnNode2)
    //                        && "value-from-node2".equals(key2OnNode1)) {
    //                    LOG.info("Both nodes see both keys after {}ms", elapsed);
    //                    return;
    //                }
    //                Sleeper.sleepMillis(100);
    //                elapsed += 100;
    //            }
    //            throw new AssertionError(
    //                    "Not all keys replicated to all nodes after "
    //                            + maxWaitMs + "ms");
    //        });
    //
    //        // Final verification
    //        assertThat(cache1.get("key2").orElse(null))
    //                .as("Node 1 should see key2 from Node 2")
    //                .isEqualTo("value-from-node2");
    //
    //        assertThat(cache2.get("key1").orElse(null))
    //                .as("Node 2 should see key1 from Node 1")
    //                .isEqualTo("value-from-node1");
    //    }
    //
    //    /**
    //     * Tests that cache listeners are triggered when remote nodes
    //     * modify the cache. This is critical for the stop mechanism.
    //     */
    //    @ClusterNodesTest(nodes = 2)
    //    void testCacheListenerNotification(
    //            int nodeCount, List<CrawlSession> sessions) {
    //
    //        var cacheName = ClusterTestUtil.uniqueCacheName("test-listener");
    //        var testKey = "notification-test";
    //        var testValue = "listener-triggered";
    //
    //        // Track what node 2's listener sees
    //        ConcurrentMap<String, String> observedOnNode2 =
    //                new ConcurrentHashMap<>();
    //
    //        // Get cache references
    //        var cache1 = ClusterTestUtil.stringCache(sessions.get(0), cacheName);
    //        var cache2 = ClusterTestUtil.stringCache(sessions.get(1), cacheName);
    //
    //        // Get underlying Infinispan cache to add listener
    //        var infinispanCache2 =
    //                ((InfinispanCacheAdapter<String>) cache2).vendor();
    //
    //        var listener = new TestListener(observedOnNode2);
    //        infinispanCache2.addListener(listener);
    //
    //        // Give listener registration time to propagate
    //        Sleeper.sleepMillis(500);
    //
    //        // Node 1 writes (should trigger listener on node 2)
    //        LOG.info("Node 1 writing key '{}'", testKey);
    //        cache1.put(testKey, testValue);
    //
    //        // Wait for listener to fire on node 2
    //        assertThatNoException().isThrownBy(() -> {
    //            var maxWaitMs = 5000;
    //            var elapsed = 0L;
    //            while (elapsed < maxWaitMs) {
    //                if (observedOnNode2.containsKey(testKey)) {
    //                    LOG.info("Node 2 listener fired after {}ms", elapsed);
    //                    return;
    //                }
    //                Sleeper.sleepMillis(100);
    //                elapsed += 100;
    //            }
    //            throw new AssertionError(
    //                    "Node 2 listener did not fire after "
    //                            + maxWaitMs + "ms");
    //        });
    //
    //        assertThat(observedOnNode2.get(testKey))
    //                .as("Node 2 listener should have observed the value")
    //                .isEqualTo(testValue);
    //    }
    //
    //    @org.infinispan.notifications.Listener(clustered = true)
    //    static class TestListener {
    //        private final ConcurrentMap<String, String> observed;
    //
    //        TestListener(ConcurrentMap<String, String> observed) {
    //            this.observed = observed;
    //        }
    //
    //        @org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated
    //        public void entryCreated(
    //                org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent<
    //                        String, String> event) {
    //            if (!event.isPre()) {
    //                LOG.info("Listener observed creation: key={}, value={}",
    //                        event.getKey(), event.getValue());
    //                observed.put(event.getKey(), event.getValue());
    //            }
    //        }
    //    }
    //
    //    /**
    //     * Tests that the admin cache (used for stop signals) replicates
    //     * correctly across nodes. This is the most direct test for the
    //     * stop mechanism.
    //     */
    //    @ClusterNodesTest(nodes = 2)
    //    void testAdminCacheReplication(
    //            int nodeCount, List<CrawlSession> sessions) {
    //
    //        var adminCacheName = "admin-cache";
    //        var stopKey = "STOP";
    //        var stopValue = "1";
    //
    //        // Get admin cache references from both nodes
    //        var adminCache1 = ClusterTestUtil.stringCache(
    //                sessions.get(0), adminCacheName);
    //        var adminCache2 = ClusterTestUtil.stringCache(
    //                sessions.get(1), adminCacheName);
    //
    //        LOG.info("Node 1 writing STOP signal to admin cache");
    //        adminCache1.put(stopKey, stopValue);
    //
    //        // Node 2 should see the STOP signal
    //        assertThatNoException().isThrownBy(() -> {
    //            var maxWaitMs = 5000;
    //            var elapsed = 0L;
    //            while (elapsed < maxWaitMs) {
    //                if (adminCache2.containsKey(stopKey)) {
    //                    LOG.info("Node 2 saw STOP signal after {}ms", elapsed);
    //                    return;
    //                }
    //                Sleeper.sleepMillis(100);
    //                elapsed += 100;
    //            }
    //            throw new AssertionError(
    //                    "Node 2 did not see STOP signal after "
    //                            + maxWaitMs + "ms");
    //        });
    //
    //        assertThat(adminCache2.get(stopKey).orElse(null))
    //                .as("Node 2 should see STOP signal from Node 1")
    //                .isEqualTo(stopValue);
    //    }
}
