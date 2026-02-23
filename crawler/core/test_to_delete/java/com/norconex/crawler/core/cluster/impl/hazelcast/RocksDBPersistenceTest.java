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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.config.QueueStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests RocksDB persistence for both MapStore and QueueStore
 * without the full crawler infrastructure. This isolates whether
 * persistence issues are with RocksDB itself or with crawler logic.
 *
 * Tests run sequentially to avoid RocksDB locking conflicts.
 */
@Slf4j
@Execution(ExecutionMode.SAME_THREAD)
class RocksDBPersistenceTest {

    private @TempDir Path tempDir;

    // Unique test ID to prevent cluster name collisions
    private static final AtomicInteger testCounter = new AtomicInteger(0);

    @AfterEach
    void cleanup() {
        LOG.info("=== Cleaning up after test ===");

        // Shutdown all Hazelcast instances
        Hazelcast.shutdownAll();

        // Close all RocksDB queue instances (clears static map)
        //  RocksDBQueueStore.closeAll();

        // Give OS time to release file locks
        sleep(1000);

        LOG.info("=== Cleanup complete ===");
    }

    @Test
    void testMapStorePersistence() {
        var dbDir = tempDir.resolve("mapstore-test").toString();
        var mapName = "testMap";

        // Phase 1: Create Hazelcast instance, add data, then shutdown
        LOG.info("=== Phase 1: Creating Hazelcast and storing data ===");
        var hz1 = createHazelcastWithMapStore(mapName, dbDir);
        try {
            var map1 = hz1.<String, String>getMap(mapName);

            // Store 10 items
            for (var i = 0; i < 10; i++) {
                map1.put("key-" + i, "value-" + i);
            }

            assertThat(map1.size()).isEqualTo(10);
            LOG.info("Stored 10 items in map");

            // Verify items are in memory
            assertThat(map1.get("key-0")).isEqualTo("value-0");
            assertThat(map1.get("key-9")).isEqualTo("value-9");

        } finally {
            LOG.info("Shutting down Hazelcast instance 1");
            hz1.shutdown();
        }

        // Give RocksDB time to close cleanly
        sleep(500);

        // Phase 2: Create new Hazelcast instance and verify data is restored
        LOG.info("=== Phase 2: Creating new Hazelcast and loading data ===");
        var hz2 = createHazelcastWithMapStore(mapName, dbDir);
        try {
            var map2 = hz2.<String, String>getMap(mapName);

            // The map should load data from RocksDB
            LOG.info("Map size after reload: {}", map2.size());

            // Verify all items are restored
            assertThat(map2.size()).isEqualTo(10);
            assertThat(map2.get("key-0")).isEqualTo("value-0");
            assertThat(map2.get("key-5")).isEqualTo("value-5");
            assertThat(map2.get("key-9")).isEqualTo("value-9");

            LOG.info("Successfully restored all 10 items from RocksDB");

        } finally {
            LOG.info("Shutting down Hazelcast instance 2");
            hz2.shutdown();
        }
    }

    @Test
    void testQueueStorePersistence() {
        var dbDir = tempDir.resolve("queuestore-test").toString();
        var queueName = "testQueue";

        // Phase 1: Create Hazelcast instance, add data, then shutdown
        LOG.info("=== Phase 1: Creating Hazelcast and queueing items ===");
        var hz1 = createHazelcastWithQueueStore(queueName, dbDir);
        try {
            var queue1 = hz1.<String>getQueue(queueName);

            // Queue 10 items
            for (var i = 0; i < 10; i++) {
                queue1.offer("item-" + i);
            }

            assertThat(queue1.size()).isEqualTo(10);
            LOG.info("Queued 10 items");

            // Poll 2 items (simulating partial processing)
            var item1 = queue1.poll();
            var item2 = queue1.poll();

            assertThat(item1).isEqualTo("item-0");
            assertThat(item2).isEqualTo("item-1");
            assertThat(queue1.size()).isEqualTo(8);
            LOG.info("Polled 2 items, 8 remaining");

        } finally {
            LOG.info("Shutting down Hazelcast instance 1");
            hz1.shutdown();
        }

        // Give RocksDB time to close cleanly
        sleep(500);

        // Phase 2: Create new Hazelcast instance and verify data is restored
        LOG.info(
                "=== Phase 2: Creating new Hazelcast and checking queue ===");
        var hz2 = createHazelcastWithQueueStore(queueName, dbDir);
        try {
            var queue2 = hz2.<String>getQueue(queueName);

            // The queue should load remaining data from RocksDB
            LOG.info("Queue size after reload: {}", queue2.size());

            // Verify remaining 8 items are restored
            assertThat(queue2.size()).isEqualTo(8);

            // Verify items are in correct order
            assertThat(queue2.poll()).isEqualTo("item-2");
            assertThat(queue2.poll()).isEqualTo("item-3");
            assertThat(queue2.size()).isEqualTo(6);

            LOG.info(
                    "Successfully restored 8 items from RocksDB in FIFO order");

        } finally {
            LOG.info("Shutting down Hazelcast instance 2");
            hz2.shutdown();
        }
    }

    @Test
    void testQueueStoreWithMultipleInstancesSeparateDBs() {
        var dbDir = tempDir.resolve("queuestore-multi-test").toString();
        var queueName = "sharedQueue";

        // Phase 1: Two instances with SEPARATE DBs (like in ClusterResumeTest)
        LOG.info(
                "=== Phase 1: Creating 2 instances with SEPARATE RocksDB dirs ===");
        var hz1 = createHazelcastWithQueueStore(queueName, dbDir + "/node-0");
        var hz2 = createHazelcastWithQueueStore(queueName, dbDir + "/node-1");
        try {
            var queue1 = hz1.<String>getQueue(queueName);
            var queue2 = hz2.<String>getQueue(queueName);

            // Queue 10 items from hz1 - they will be distributed across nodes
            for (var i = 0; i < 10; i++) {
                queue1.offer("item-" + i);
            }

            sleep(500); // Let cluster sync

            // Get current distribution
            var hz1Size = queue1.size();
            var hz2Size = queue2.size();
            LOG.info("After queueing: hz1={}, hz2={}", hz1Size, hz2Size);

            // Poll 2 items total
            queue1.poll();
            queue2.poll();

            sleep(500); // Let cluster sync

            var remainingHz1 = queue1.size();
            var remainingHz2 = queue2.size();
            LOG.info("After polling: hz1={}, hz2={}", remainingHz1,
                    remainingHz2);

        } finally {
            LOG.info("Shutting down both instances");
            hz1.shutdown();
            hz2.shutdown();
        }

        sleep(500);

        // Phase 2: Restart with same separate DBs
        LOG.info("=== Phase 2: Restarting with SAME separate DB dirs ===");
        var hz3 = createHazelcastWithQueueStore(queueName, dbDir + "/node-0");
        var hz4 = createHazelcastWithQueueStore(queueName, dbDir + "/node-1");
        try {
            var queue3 = hz3.<String>getQueue(queueName);
            var queue4 = hz4.<String>getQueue(queueName);

            sleep(500); // Let cluster sync

            var restoredHz3 = queue3.size();
            var restoredHz4 = queue4.size();
            var totalRestored = restoredHz3 + restoredHz4;

            LOG.info("After restart: hz3={}, hz4={}, total={}",
                    restoredHz3, restoredHz4, totalRestored);

            // This test demonstrates the partition problem:
            // Items stored in node-0's RocksDB may now belong to node-1's
            // partition and vice versa, so restoration is unreliable
            LOG.info(
                    "NOTE: Total may not be 8 due to partition redistribution!");
            LOG.info(
                    "This demonstrates why queue persistence is unreliable in multi-node setups.");

        } finally {
            hz3.shutdown();
            hz4.shutdown();
        }
    }

    @Test
    void testMapStoreLoadAllKeys() {
        var dbDir = tempDir.resolve("mapstore-loadall-test").toString();
        var mapName = "testLoadAllMap";

        // Phase 1: Store data with explicit flush
        LOG.info("=== Phase 1: Storing data ===");
        var hz1 = createHazelcastWithMapStore(mapName, dbDir);
        try {
            var map1 = hz1.<String, String>getMap(mapName);

            // Store data
            Map<String, String> batch = new HashMap<>();
            for (var i = 0; i < 10; i++) {
                batch.put("key-" + i, "value-" + i);
            }
            map1.putAll(batch);

            assertThat(map1.size()).isEqualTo(10);
            LOG.info("Stored 10 items using putAll");

        } finally {
            hz1.shutdown();
        }

        sleep(500);

        // Phase 2: Verify loadAllKeys is called and data is restored
        LOG.info(
                "=== Phase 2: Reloading with EAGER mode (should call loadAllKeys) ===");
        var hz2 = createHazelcastWithMapStore(mapName, dbDir);
        try {
            var map2 = hz2.<String, String>getMap(mapName);

            // With EAGER mode, the map should automatically load all keys
            LOG.info("Map size after EAGER load: {}", map2.size());

            assertThat(map2.size()).isEqualTo(10);

            // Verify data integrity
            for (var i = 0; i < 10; i++) {
                assertThat(map2.get("key-" + i)).isEqualTo("value-" + i);
            }

            LOG.info("All keys successfully loaded via loadAllKeys()");

        } finally {
            hz2.shutdown();
        }
    }

    private HazelcastInstance createHazelcastWithMapStore(
            String mapName, String dbDir) {
        var testId = testCounter.incrementAndGet();
        var config = new Config();
        config.setClusterName("map-test-" + testId);
        config.setInstanceName("map-instance-" + testId);

        // Disable phone home to prevent warnings during shutdown
        config.setProperty("hazelcast.phone.home.enabled", "false");

        // Disable all network join mechanisms to ensure isolation
        config.getNetworkConfig().getJoin().getMulticastConfig()
                .setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig()
                .setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig()
                .setEnabled(false);

        var mapConfig = new MapConfig(mapName)
                .setBackupCount(0); // Single node, no backups needed

        var mapStoreConfig = new MapStoreConfig()
                .setEnabled(true)
                .setInitialLoadMode(MapStoreConfig.InitialLoadMode.EAGER)
                .setWriteDelaySeconds(0)
                .setWriteCoalescing(false)
                //         .setClassName(RocksDBMapStore.class.getName())
                .setProperty("database.dir", dbDir);

        mapConfig.setMapStoreConfig(mapStoreConfig);
        config.addMapConfig(mapConfig);

        LOG.info("Creating Hazelcast instance '{}' with MapStore at: {}",
                config.getInstanceName(), dbDir);
        return Hazelcast.newHazelcastInstance(config);
    }

    private HazelcastInstance createHazelcastWithQueueStore(
            String queueName, String dbDir) {
        var testId = testCounter.incrementAndGet();
        var config = new Config();
        config.setClusterName("queue-test-" + testId);
        config.setInstanceName("queue-instance-" + testId);

        // Disable phone home to suppress shutdown warnings
        config.setProperty("hazelcast.phone.home.enabled", "false");

        // Disable all network join mechanisms to ensure isolation
        config.getNetworkConfig().getJoin().getMulticastConfig()
                .setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig()
                .setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig()
                .setEnabled(false);

        var queueConfig = new QueueConfig(queueName)
                .setBackupCount(0); // Single node, no backups needed

        var queueStoreConfig = new QueueStoreConfig()
                .setEnabled(true)
                //                .setFactoryClassName(
                //                        RocksDBQueueStoreFactory.class.getName())
                .setProperty("database.dir", dbDir);

        queueConfig.setQueueStoreConfig(queueStoreConfig);
        config.addQueueConfig(queueConfig);

        LOG.info("Creating Hazelcast instance '{}' with QueueStore at: {}",
                config.getInstanceName(), dbDir);
        return Hazelcast.newHazelcastInstance(config);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
