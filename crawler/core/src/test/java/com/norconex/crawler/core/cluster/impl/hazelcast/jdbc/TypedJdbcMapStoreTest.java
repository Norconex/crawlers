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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * Unit tests for {@link TypedJdbcMapStore}. The underlying
 * {@link StringJdbcMapStore} is mocked so no real JDBC connection is needed.
 */
class TypedJdbcMapStoreTest {

    private StringJdbcMapStore ss;
    private HazelcastInstance hz;

    @BeforeEach
    void setUp() {
        ss = mock(StringJdbcMapStore.class);
        hz = mock(HazelcastInstance.class);
    }

    // -----------------------------------------------------------------------
    // store()
    // -----------------------------------------------------------------------

    @Test
    void store_nullValue_delegatesToDelete() {
        var store = storeOf(String.class);

        store.store("myKey", null);

        verify(ss).delete("myKey");
        verify(ss, never()).store(any(), any());
    }

    @Test
    void store_stringValueWithStringClass_storesDirectly() {
        var store = storeOf(String.class);

        store.store("k", "hello");

        verify(ss).store("k", "hello");
    }

    @Test
    void store_stringInstanceWithNonStringClass_storesAsString() {
        // valueClass is Integer, but actual value is a String instance
        var store = storeOf(Integer.class);

        store.store("k", "plain-string");

        verify(ss).store("k", "plain-string");
    }

    @Test
    void store_nonStringValue_storesAsJson() {
        var store = storeOf(StepRecord.class);
        var rec = new StepRecord()
                .setStepId("s1")
                .setStatus(PipelineStatus.PENDING);

        store.store("k", rec);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(ss).store(eq("k"), captor.capture());
        assertThat(captor.getValue()).contains("s1");
    }

    // -----------------------------------------------------------------------
    // storeAll()
    // -----------------------------------------------------------------------

    @Test
    void storeAll_nullMap_doesNothing() {
        var store = storeOf(String.class);
        clearInvocations(ss); // ignore init() in constructor

        store.storeAll(null);

        verify(ss, never()).storeAll(any());
        verify(ss, never()).delete(any());
    }

    @Test
    void storeAll_emptyMap_doesNothing() {
        var store = storeOf(String.class);
        clearInvocations(ss);

        store.storeAll(Map.of());

        verify(ss, never()).storeAll(any());
        verify(ss, never()).delete(any());
    }

    @Test
    void storeAll_stringValues_storesAll() {
        var store = storeOf(String.class);
        clearInvocations(ss);

        store.storeAll(Map.of("a", "1", "b", "2"));

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(ss).storeAll(captor.capture());
        assertThat(captor.getValue()).containsKeys("a", "b");
    }

    @Test
    void storeAll_nullValueEntry_deletesKeyAndStoresRest() {
        var store = storeOf(String.class);
        clearInvocations(ss);

        // Use a mutable map so we can include a null value
        var map = new java.util.HashMap<String, Object>();
        map.put("keep", "val");
        map.put("nuke", null);
        store.storeAll(map);

        @SuppressWarnings("unchecked")
        var storeCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ss).storeAll(storeCaptor.capture());
        assertThat(storeCaptor.getValue()).containsKey("keep")
                .doesNotContainKey("nuke");
        verify(ss).delete("nuke");
    }

    @Test
    void storeAll_allNullValues_onlyDeletes() {
        var store = storeOf(String.class);
        clearInvocations(ss);

        var map = new java.util.HashMap<String, Object>();
        map.put("a", null);
        map.put("b", null);
        store.storeAll(map);

        verify(ss, never()).storeAll(any());
        verify(ss).delete("a");
        verify(ss).delete("b");
    }

    @Test
    void storeAll_nonStringValues_storesAsJson() {
        var store = storeOf(StepRecord.class);
        clearInvocations(ss);

        var rec = new StepRecord().setStepId("step-x");
        store.storeAll(Map.of("k", rec));

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(ss).storeAll(captor.capture());
        assertThat(captor.getValue().get("k").toString()).contains("step-x");
    }

    // -----------------------------------------------------------------------
    // delete() / deleteAll()
    // -----------------------------------------------------------------------

    @Test
    void delete_delegatesToStringStore() {
        var store = storeOf(String.class);

        store.delete("key1");

        verify(ss).delete("key1");
    }

    @Test
    void deleteAll_delegatesToStringStore() {
        var store = storeOf(String.class);

        store.deleteAll(List.of("a", "b", "c"));

        verify(ss).deleteAll(List.of("a", "b", "c"));
    }

    // -----------------------------------------------------------------------
    // load()
    // -----------------------------------------------------------------------

    @Test
    void load_nullJson_returnsNull() {
        when(ss.load("missing")).thenReturn(null);
        var store = storeOf(StepRecord.class);

        assertThat(store.load("missing")).isNull();
    }

    @Test
    void load_stringClass_returnsJsonStringDirectly() {
        when(ss.load("k")).thenReturn("rawValue");
        var store = storeOf(String.class);

        assertThat(store.load("k")).isEqualTo("rawValue");
    }

    @Test
    void load_nonStringClass_deserializesFromJson() {
        var rec = new StepRecord().setStepId("s99")
                .setStatus(PipelineStatus.RUNNING);
        var json = SerialUtil.toJsonString(rec);
        when(ss.load("k")).thenReturn(json);
        var store = storeOf(StepRecord.class);

        var loaded = store.load("k");

        assertThat(loaded).isInstanceOf(StepRecord.class);
        assertThat(((StepRecord) loaded).getStepId()).isEqualTo("s99");
    }

    // -----------------------------------------------------------------------
    // loadAll()
    // -----------------------------------------------------------------------

    @Test
    void loadAll_stringClass_returnsStringsDirectly() {
        when(ss.loadAll(List.of("a", "b")))
                .thenReturn(Map.of("a", "v1", "b", "v2"));
        var store = storeOf(String.class);

        var result = store.loadAll(List.of("a", "b"));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of("a", "v1", "b", "v2"));
    }

    @Test
    void loadAll_nonStringClass_deserializesValues() {
        var rec = new StepRecord().setStepId("step-load");
        var json = SerialUtil.toJsonString(rec);
        when(ss.loadAll(List.of("k"))).thenReturn(Map.of("k", json));
        var store = storeOf(StepRecord.class);

        var result = store.loadAll(List.of("k"));

        assertThat(result.get("k")).isInstanceOf(StepRecord.class);
        assertThat(((StepRecord) result.get("k")).getStepId())
                .isEqualTo("step-load");
    }

    // -----------------------------------------------------------------------
    // loadAllKeys()
    // -----------------------------------------------------------------------

    @Test
    void loadAllKeys_delegatesToStringStore() {
        when(ss.loadAllKeys()).thenReturn(List.of("k1", "k2"));
        var store = storeOf(String.class);

        var keys = store.loadAllKeys();

        assertThat(keys).containsExactlyInAnyOrder("k1", "k2");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private TypedJdbcMapStore storeOf(Class<?> valueClass) {
        return new TypedJdbcMapStore(ss, valueClass, hz, new Properties(),
                "test-store");
    }
}
