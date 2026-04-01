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
import com.norconex.crawler.core.junit.annotations.SlowTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.norconex.crawler.core.junit.annotations.SlowTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import com.norconex.crawler.core.junit.annotations.SlowTest;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastConfigurerContext;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.junit.annotations.SlowTest;

/**
 * Direct unit tests for {@link TypedJdbcQueueStore} covering
 * {@code storeAll()}, {@code store()}, {@code load()}, and {@code loadAll()}
 * with JSON serialization/deserialization of typed objects.
 */
@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SlowTest
class TypedJdbcQueueStoreDirectTest {

    private Path tempDir;
    private HazelcastInstance hz;

    @BeforeAll
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hz-typed-queue-test-");
        hz = newInstance();
    }

    @AfterAll
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    // ======================================================================
    // store() + load() round-trip (typed object)
    // ======================================================================

    /**
     * A typed object is JSON-serialized on {@code store()} and
     * deserialized back to the original type on {@code load()}.
     */
    @Test
    void store_typedObject_andLoad_roundTripPreservesFields() {
        var store = newStepRecordStore("tq_store_rt_" + uniqueSuffix());
        var record = makeRecord("p1", "s1", PipelineStatus.RUNNING, "r1");

        store.store(1L, record);
        var loaded = store.load(1L);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getPipelineId()).isEqualTo("p1");
        assertThat(loaded.getStepId()).isEqualTo("s1");
        assertThat(loaded.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(loaded.getRunId()).isEqualTo("r1");
    }

    /** {@code load()} returns {@code null} when the key does not exist. */
    @Test
    void load_missingKey_returnsNull() {
        var store = newStepRecordStore("tq_load_miss_" + uniqueSuffix());
        assertThat(store.load(9999L)).isNull();
    }

    // ======================================================================
    // storeAll() + loadAll()
    // ======================================================================

    /**
     * {@code storeAll()} serializes every entry to JSON and persists the
     * batch atomically.  {@code loadAll()} deserializes them back.
     */
    @Test
    void storeAll_typedBatch_andLoadAll_roundTrip() {
        var store = newStepRecordStore("tq_storeall_rt_" + uniqueSuffix());

        var batch = new LinkedHashMap<Long, StepRecord>();
        batch.put(10L, makeRecord("pA", "sA", PipelineStatus.RUNNING, "rA"));
        batch.put(20L, makeRecord("pB", "sB", PipelineStatus.COMPLETED, "rB"));
        batch.put(30L, makeRecord("pC", "sC", PipelineStatus.FAILED, "rC"));
        store.storeAll(batch);

        var loaded = store.loadAll(List.of(10L, 20L, 30L));

        assertThat(loaded).hasSize(3);
        assertThat(loaded.get(10L).getPipelineId()).isEqualTo("pA");
        assertThat(loaded.get(20L).getStatus())
                .isEqualTo(PipelineStatus.COMPLETED);
        assertThat(loaded.get(30L).getRunId()).isEqualTo("rC");
    }

    /** {@code storeAll()} with an empty map is a no-op (no exception thrown). */
    @Test
    void storeAll_emptyMap_isNoOp() {
        var store = newStepRecordStore("tq_storeall_empty_" + uniqueSuffix());
        store.storeAll(Map.of());
        assertThat(store.loadAllKeys()).isEmpty();
    }

    // ======================================================================
    // String-typed store (no JSON serialization)
    // ======================================================================

    /**
     * When the value type is {@link String}, no JSON serialization occurs;
     * values are stored and retrieved verbatim.
     */
    @Test
    void store_stringValue_storedVerbatimNoJson() {
        var store = newStringStore("tq_str_" + uniqueSuffix());
        store.store(1L, "plain text");
        assertThat(store.load(1L)).isEqualTo("plain text");
    }

    @Test
    void storeAll_stringBatch_storesAllVerbatim() {
        var store = newStringStore("tq_str_all_" + uniqueSuffix());
        var batch = new LinkedHashMap<Long, String>();
        batch.put(1L, "alpha");
        batch.put(2L, "beta");
        batch.put(3L, "gamma");
        store.storeAll(batch);

        assertThat(store.load(1L)).isEqualTo("alpha");
        assertThat(store.load(2L)).isEqualTo("beta");
        assertThat(store.load(3L)).isEqualTo("gamma");
    }

    // ======================================================================
    // Null-handling
    // ======================================================================

    /**
     * Storing {@code null} delegates to the underlying queue store; loading
     * back produces {@code null} (no NPE or serialization error).
     */
    @Test
    void store_nullValue_andLoadBack_returnsNull() {
        var store = newStepRecordStore("tq_null_" + uniqueSuffix());
        store.store(1L, null);
        assertThat(store.load(1L)).isNull();
    }

    // ======================================================================
    // delete() / deleteAll()
    // ======================================================================

    @Test
    void delete_removesEntry() {
        var store = newStepRecordStore("tq_del_" + uniqueSuffix());
        var rec = makeRecord("p", "s", PipelineStatus.RUNNING, "r");
        store.store(42L, rec);
        assertThat(store.load(42L)).isNotNull();

        store.delete(42L);
        assertThat(store.load(42L)).isNull();
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private TypedJdbcQueueStore<StepRecord> newStepRecordStore(
            String storeName) {
        return new TypedJdbcQueueStore<>(
                hz, storeName, storeProps(), StepRecord.class);
    }

    private TypedJdbcQueueStore<String> newStringStore(String storeName) {
        return new TypedJdbcQueueStore<>(
                hz, storeName, storeProps(), String.class);
    }

    private Properties storeProps() {
        var props = new Properties();
        props.setProperty(JdbcClient.PROP_DATA_CONN_REF, "jdbc-datasource");
        props.setProperty("column-value-type", "TEXT");
        return props;
    }

    private HazelcastInstance newInstance() {
        var ctx =
                new HazelcastConfigurerContext(tempDir, false, "test-cluster");
        return Hazelcast.newHazelcastInstance(
                new JdbcHazelcastConfigurer().buildConfig(ctx));
    }

    private static StepRecord makeRecord(String pipelineId, String stepId,
            PipelineStatus status, String runId) {
        return new StepRecord()
                .setPipelineId(pipelineId)
                .setStepId(stepId)
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(status)
                .setRunId(runId);
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.nanoTime());
    }
}
