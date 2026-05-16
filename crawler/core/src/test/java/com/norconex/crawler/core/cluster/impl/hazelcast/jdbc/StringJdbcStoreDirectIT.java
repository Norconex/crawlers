/* Copyright 2025-2026 Norconex Inc.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastConfigurerContext;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.core.junit.annotations.SlowTest;

/**
 * Direct (Hazelcast-bypassing) unit tests for
 * {@link StringJdbcMapStore} and {@link StringJdbcQueueStore}.
 *
 * <p>These tests call {@code storeAll()}, {@code load()}, etc. directly on the
 * store instances rather than going through the Hazelcast map/queue API,
 * which ensures 100% coverage of paths that Hazelcast does not exercise
 * (e.g. {@code storeAll} is never invoked by Hazelcast on
 * {@code write-delay-seconds: 0} maps).</p>
 */
@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SlowTest
class StringJdbcStoreDirectTest {

    private Path tempDir;
    private HazelcastInstance hz;

    /** H2-compatible MERGE statement used by the standalone config. */
    private static final String H2_MERGE_SQL =
            "MERGE INTO \"{tableName}\" (k, v)\nKEY(k)\nVALUES (?, ?)";

    @BeforeAll
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hz-store-direct-test-");
        hz = newInstance();
    }

    @AfterAll
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    // ======================================================================
    // StringJdbcMapStore
    // ======================================================================

    /**
     * {@code storeAll()} inserts all entries in a single transaction.
     * Entries are retrievable via individual {@code load()} calls afterward.
     */
    @Test
    void stringMapStore_storeAll_insertsAllEntries() {
        var store = newMapStore("map_storeall_" + uniqueSuffix());

        var entries = Map.of("k1", "v1", "k2", "v2", "k3", "v3");
        store.storeAll(entries);

        assertThat(store.load("k1")).isEqualTo("v1");
        assertThat(store.load("k2")).isEqualTo("v2");
        assertThat(store.load("k3")).isEqualTo("v3");
    }

    /**
     * {@code storeAll()} with an empty map must be a no-op (no exception).
     */
    @Test
    void stringMapStore_storeAll_emptyMap_isNoOp() {
        var store = newMapStore("map_storeall_empty_" + uniqueSuffix());
        // Must not throw
        store.storeAll(Map.of());
        assertThat(store.loadAllKeys()).isEmpty();
    }

    /** {@code load()} returns the value for an existing key. */
    @Test
    void stringMapStore_load_existingKey_returnsValue() {
        var store = newMapStore("map_load_" + uniqueSuffix());
        store.store("hello", "world");
        assertThat(store.load("hello")).isEqualTo("world");
    }

    /** {@code load()} returns {@code null} for a missing key. */
    @Test
    void stringMapStore_load_missingKey_returnsNull() {
        var store = newMapStore("map_load_miss_" + uniqueSuffix());
        assertThat(store.load("nonexistent")).isNull();
    }

    /** {@code loadAll()} retrieves all requested keys at once. */
    @Test
    void stringMapStore_loadAll_multiplePairs() {
        var store = newMapStore("map_loadall_" + uniqueSuffix());
        store.store("a", "alpha");
        store.store("b", "beta");
        store.store("c", "gamma");

        var result = store.loadAll(Set.of("a", "b", "c"));

        assertThat(result).containsEntry("a", "alpha")
                .containsEntry("b", "beta")
                .containsEntry("c", "gamma");
    }

    /** {@code loadAllKeys()} returns every key currently in the table. */
    @Test
    void stringMapStore_loadAllKeys_returnsAllStoredKeys() {
        var store = newMapStore("map_allkeys_" + uniqueSuffix());
        store.store("x", "1");
        store.store("y", "2");
        store.store("z", "3");

        var keys = store.loadAllKeys();
        assertThat(keys).containsExactlyInAnyOrder("x", "y", "z");
    }

    /** {@code delete()} removes the entry; subsequent {@code load()} returns null. */
    @Test
    void stringMapStore_delete_removesEntry() {
        var store = newMapStore("map_delete_" + uniqueSuffix());
        store.store("toDelete", "value");
        assertThat(store.load("toDelete")).isEqualTo("value");

        store.delete("toDelete");
        assertThat(store.load("toDelete")).isNull();
    }

    /** {@code storeAll()} followed by {@code loadAll()} is a consistent round-trip. */
    @Test
    void stringMapStore_storeAll_thenLoadAll_roundTrip() {
        var store = newMapStore("map_rt_" + uniqueSuffix());
        var batch = Map.of(
                "rt-1", "val-1", "rt-2", "val-2",
                "rt-3", "val-3", "rt-4", "val-4");
        store.storeAll(batch);

        var loaded = store.loadAll(batch.keySet());
        assertThat(loaded).containsAllEntriesOf(batch);
    }

    // ======================================================================
    // StringJdbcQueueStore
    // ======================================================================

    /** {@code store()} + {@code load()} round-trip using long keys. */
    @Test
    void stringQueueStore_store_andLoad_roundTrip() {
        var store = newQueueStore("q_rt_" + uniqueSuffix());
        store.store(1L, "item-a");
        assertThat(store.load(1L)).isEqualTo("item-a");
    }

    /** {@code load()} returns {@code null} when the key does not exist. */
    @Test
    void stringQueueStore_load_missingKey_returnsNull() {
        var store = newQueueStore("q_load_miss_" + uniqueSuffix());
        assertThat(store.load(9999L)).isNull();
    }

    /** {@code storeAll()} persists a batch of entries. */
    @Test
    void stringQueueStore_storeAll_persistsBatch() {
        var store = newQueueStore("q_storeall_" + uniqueSuffix());
        var batch = new LinkedHashMap<Long, String>();
        batch.put(10L, "alpha");
        batch.put(20L, "beta");
        batch.put(30L, "gamma");
        store.storeAll(batch);

        assertThat(store.load(10L)).isEqualTo("alpha");
        assertThat(store.load(20L)).isEqualTo("beta");
        assertThat(store.load(30L)).isEqualTo("gamma");
    }

    /** {@code storeAll()} with an empty map is a no-op. */
    @Test
    void stringQueueStore_storeAll_emptyMap_isNoOp() {
        var store = newQueueStore("q_storeall_empty_" + uniqueSuffix());
        store.storeAll(Map.of());
        assertThat(store.loadAllKeys()).isEmpty();
    }

    /** {@code loadAll()} retrieves only the requested keys in key order. */
    @Test
    void stringQueueStore_loadAll_bySelectedKeys() {
        var store = newQueueStore("q_loadall_" + uniqueSuffix());
        store.store(1L, "one");
        store.store(2L, "two");
        store.store(3L, "three");

        var result = store.loadAll(List.of(1L, 3L));
        assertThat(result).containsEntry(1L, "one")
                .containsEntry(3L, "three")
                .doesNotContainKey(2L);
    }

    /** {@code loadAllKeys()} returns a sorted set of all stored keys. */
    @Test
    void stringQueueStore_loadAllKeys_returnsSortedKeys() {
        var store = newQueueStore("q_allkeys_" + uniqueSuffix());
        store.store(5L, "e");
        store.store(2L, "b");
        store.store(8L, "h");

        var keys = store.loadAllKeys();
        assertThat(keys).containsExactly(2L, 5L, 8L); // TreeSet order
    }

    /** {@code delete()} removes the entry from the queue store. */
    @Test
    void stringQueueStore_delete_removesEntry() {
        var store = newQueueStore("q_delete_" + uniqueSuffix());
        store.store(42L, "toRemove");
        assertThat(store.load(42L)).isEqualTo("toRemove");

        store.delete(42L);
        assertThat(store.load(42L)).isNull();
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private StringJdbcMapStore newMapStore(String storeName) {
        var props = new Properties();
        props.setProperty(JdbcClient.PROP_DATA_CONN_REF, "jdbc-datasource");
        props.setProperty("column-key-type", "VARCHAR(4096)");
        props.setProperty("column-value-type", "TEXT");
        props.setProperty("sql-merge", H2_MERGE_SQL);
        var store = new StringJdbcMapStore();
        store.init(hz, props, storeName);
        return store;
    }

    private StringJdbcQueueStore newQueueStore(String storeName) {
        var props = new Properties();
        props.setProperty(JdbcClient.PROP_DATA_CONN_REF, "jdbc-datasource");
        props.setProperty("column-value-type", "TEXT");
        return new StringJdbcQueueStore(hz, storeName, props);
    }

    private HazelcastInstance newInstance() {
        var ctx =
                new HazelcastConfigurerContext(tempDir, false, "test-cluster");
        return Hazelcast.newHazelcastInstance(
                new JdbcHazelcastConfigurer().buildConfig(ctx));
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.nanoTime());
    }
}
