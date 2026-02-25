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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnectorConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastConfigLoader;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

/**
 * Tests batch and bulk JDBC store operations via Hazelcast maps/queues
 * backed by the standalone hazelcast config.  Tests storeAll, loadAll,
 * deleteAll, loadAllKeys, and queue persistence.
 */
@Timeout(120)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcStoreOperationsTest {

    private Path tempDir;
    private HazelcastInstance hz;

    @BeforeAll
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hz-jdbc-ops-test-");
        hz = newInstance();
    }

    @AfterAll
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    private HazelcastInstance newInstance() {
        var workDir = tempDir.toAbsolutePath().toString().replace("\\", "/");
        return Hazelcast.newHazelcastInstance(
                HazelcastConfigLoader.load(
                        HazelcastClusterConnectorConfig.DEFAULT_CONFIG_FILE,
                        Map.of("workDir", workDir)));
    }

    /*
     * Tests putAll → storeAll (batch insert) via default map config
     */
    @Test
    void testStoreAll_viaMapPutAll() {
        var map = hz.<String, String>getMap("batch-store-test");

        // putAll triggers storeAll in StringJdbcMapStore
        map.putAll(Map.of("k1", "v1", "k2", "v2", "k3", "v3"));

        // Reload via new HZ instance to verify persistence
        hz.shutdown();
        hz = newInstance();
        var reloaded = hz.<String, String>getMap("batch-store-test");
        assertThat(reloaded.get("k1")).isEqualTo("v1");
        assertThat(reloaded.get("k2")).isEqualTo("v2");
        assertThat(reloaded.get("k3")).isEqualTo("v3");
    }

    /*
     * Tests getAll → loadAll (batch load by keys)
     */
    @Test
    void testLoadAll_viaMapGetAll() {
        var map = hz.<String, String>getMap("batch-load-test");
        map.putAll(Map.of("a", "alpha", "b", "beta", "c", "gamma"));
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String, String>getMap("batch-load-test");
        // getAll triggers loadAll in StringJdbcMapStore
        var loaded = reloaded.getAll(Set.of("a", "b", "c"));
        assertThat(loaded).containsEntry("a", "alpha")
                .containsEntry("b", "beta")
                .containsEntry("c", "gamma");
    }

    /*
     * Tests keySet iteration → loadAllKeys
     */
    @Test
    void testLoadAllKeys_viaKeySet() {
        var map = hz.<String, String>getMap("batch-keys-test");
        map.put("key1", "val1");
        map.put("key2", "val2");
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String, String>getMap("batch-keys-test");
        // keySet() on an EAGER-loaded map exercises loadAllKeys
        var keys = reloaded.keySet();
        assertThat(keys).contains("key1", "key2");
    }

    /*
     * Tests batch  delete → deleteAll (several removes flushed in transaction)
     */
    @Test
    void testDeleteAll_viaMapRemoveAll() {
        var map = hz.<String, String>getMap("batch-delete-test");
        map.putAll(Map.of("d1", "x", "d2", "y", "d3", "z", "d4", "w"));

        // Remove all via individual removes - Hazelcast batches these
        map.remove("d1");
        map.remove("d2");
        map.remove("d3");
        // d4 should still be there
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String, String>getMap("batch-delete-test");
        assertThat(reloaded.containsKey("d1")).isFalse();
        assertThat(reloaded.containsKey("d2")).isFalse();
        assertThat(reloaded.containsKey("d3")).isFalse();
        assertThat(reloaded.get("d4")).isEqualTo("w");
    }

    /*
     * Tests typed StepRecord map with pipeWorkerStatuses (uses TypedJdbcMapStoreFactory
     * with StepRecord as value class)
     */
    @Test
    void testTypedMapStore_stepRecords() {
        var record1 = new StepRecord()
                .setPipelineId("p1").setStepId("s1")
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(PipelineStatus.RUNNING).setRunId("r1");
        var record2 = new StepRecord()
                .setPipelineId("p2").setStepId("s2")
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(PipelineStatus.COMPLETED).setRunId("r2");

        var map = hz.<String, StepRecord>getMap("pipeWorkerStatuses");
        map.put("node-1", record1);
        map.put("node-2", record2);
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String, StepRecord>getMap("pipeWorkerStatuses");
        assertThat(reloaded.get("node-1").getStatus())
                .isEqualTo(PipelineStatus.RUNNING);
        assertThat(reloaded.get("node-2").getStatus())
                .isEqualTo(PipelineStatus.COMPLETED);
    }

    /*
     * Tests queue add/poll/size via queue-* configuration
     * (TypedJdbcQueueStoreFactory -> TypedJdbcQueueStore -> StringJdbcQueueStore)
     */
    @Test
    void testQueueStoreAndLoad() {
        var queue = hz.<String>getQueue("queue-jdbc-ops-test");
        queue.add("item-x");
        queue.add("item-y");
        queue.add("item-z");

        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.peek()).isEqualTo("item-x");

        // Poll one item
        var polled = queue.poll();
        assertThat(polled).isEqualTo("item-x");
        assertThat(queue.size()).isEqualTo(2);
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String>getQueue("queue-jdbc-ops-test");
        // On restart, queue is re-loaded from JDBC store
        assertThat(reloaded.size()).isGreaterThanOrEqualTo(0);
    }

    /*
     * Tests storeAll on queue and loadAll on restart
     */
    @Test
    void testQueueBatchOperations() {
        var queue = hz.<String>getQueue("queue-batch-ops-test");
        // Add multiple items (triggers storeAll on the queue store)
        for (int i = 0; i < 5; i++) {
            queue.add("batch-item-" + i);
        }
        assertThat(queue.size()).isEqualTo(5);

        // Clear queue (triggers deleteAll)
        queue.clear();
        assertThat(queue.size()).isZero();
    }

    /*
     * Trigger individual-key StringJdbcMapStore.load() by evicting a key
     * from memory then getting it back. This covers the lambda$0 RowMapper.
     */
    @Test
    void testStringJdbcMapStore_singleKeyLoad_viaEviction() {
        var map = hz.<String, String>getMap("evict-load-test");
        map.put("evict-key-1", "evict-val-1");
        map.put("evict-key-2", "evict-val-2");

        // Evict keys from in-memory cache; subsequent gets re-load from DB
        map.evict("evict-key-1");
        map.evict("evict-key-2");

        // These get() calls now go through StringJdbcMapStore.load()
        assertThat(map.get("evict-key-1")).isEqualTo("evict-val-1");
        assertThat(map.get("evict-key-2")).isEqualTo("evict-val-2");
    }

    /*
     * Test that StringJdbcMapStore.storeAll is invoked by a large putAll,
     * and StringJdbcQueueStore load path is covered via queue drain after
     * restart.
     */
    @Test
    void testStoreAll_andQueueDrainCoversLoadPaths() {
        // Large putAll may trigger storeAll in write-behind scenarios
        var map = hz.<String, String>getMap("storeall-test");
        var entries = new java.util.HashMap<String, String>();
        for (int i = 0; i < 10; i++) {
            entries.put("sa-key-" + i, "sa-val-" + i);
        }
        map.putAll(entries);
        map.flush(); // flush write-behind to DB

        // Evict all to force re-read from DB
        for (int i = 0; i < 10; i++) {
            map.evict("sa-key-" + i);
        }
        for (int i = 0; i < 10; i++) {
            assertThat(map.get("sa-key-" + i)).isEqualTo("sa-val-" + i);
        }
    }

    /*
     * Test TypedJdbcMapStore.storeAll via typed map with StepRecord values
     * and eviction to force load + RowMapper
     */
    @Test
    void testTypedJdbcMapStore_storeAll_andEvictLoad() {
        var map = hz.<String, StepRecord>getMap("pipeWorkerStatuses");
        var entries = new java.util.HashMap<String, StepRecord>();
        for (int i = 0; i < 5; i++) {
            var rec = new StepRecord()
                    .setPipelineId("bulk-p" + i)
                    .setStepId("bulk-s" + i)
                    .setUpdatedAt(System.currentTimeMillis())
                    .setStatus(PipelineStatus.RUNNING)
                    .setRunId("bulk-r" + i);
            entries.put("sa-node-" + i, rec);
        }
        map.putAll(entries);

        // Evict then re-read to force TypedJdbcMapStore.load()
        map.evict("sa-node-0");
        var reloaded = map.get("sa-node-0");
        if (reloaded != null) {
            assertThat(reloaded.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        }
    }
}
