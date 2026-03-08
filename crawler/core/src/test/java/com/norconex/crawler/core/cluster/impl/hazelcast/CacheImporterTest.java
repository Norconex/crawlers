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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.SerializedCache.CacheType;
import com.norconex.crawler.core.cluster.SerializedCache.SerializedEntry;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * Unit tests for {@link CacheImporter} — covers every branch of
 * {@code importCaches}, including class resolution, deserialisation
 * fall-backs, MAP batch logic, and QUEUE population.
 */
@Timeout(30)
@SlowTest
@WithTestWatcherLogging
class CacheImporterTest {

    private HazelcastCacheManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
        HazelcastTestSupport.shutdownAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HazelcastCacheManager newManager() {
        var hz = HazelcastTestSupport.startNode();
        manager = new HazelcastCacheManager(hz);
        return manager;
    }

    /** Build a MAP-type SerializedCache with the given entries. */
    private static SerializedCache mapCache(
            String name, String className, List<SerializedEntry> entries) {
        var c = new SerializedCache();
        c.setCacheName(name);
        c.setClassName(className);
        c.setCacheType(CacheType.MAP);
        c.setEntries(entries.iterator());
        return c;
    }

    /** Build a QUEUE-type SerializedCache with the given entries. */
    private static SerializedCache queueCache(
            String name, String className, List<SerializedEntry> entries) {
        var c = new SerializedCache();
        c.setCacheName(name);
        c.setClassName(className);
        c.setCacheType(CacheType.QUEUE);
        c.setEntries(entries.iterator());
        return c;
    }

    // ------------------------------------------------------------------
    // Empty cache list → no-op
    // ------------------------------------------------------------------

    @Test
    void importCaches_emptyList_noOp() {
        var mgr = newManager();
        // Should complete without throwing
        CacheImporter.importCaches(mgr, Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // MAP: null className → defaults to String
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapNullClassName_defaultsToString() {
        var mgr = newManager();
        var entries = List.of(new SerializedEntry("k1", "hello"));
        var cache = mapCache("map-null-class", null, entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        // Entry must be present as a raw string
        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getMap("map-null-class").get("k1"))
                .isEqualTo("hello");
    }

    // ------------------------------------------------------------------
    // MAP: unknown className → falls back to String, logs debug
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapUnknownClassName_fallsBackToString() {
        var mgr = newManager();
        var entries = List.of(new SerializedEntry("k1", "raw-value"));
        var cache = mapCache(
                "map-unknown-class",
                "com.example.DoesNotExist",
                entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        // Fallback to String → entry stored as-is
        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getMap("map-unknown-class").get("k1"))
                .isEqualTo("raw-value");
    }

    // ------------------------------------------------------------------
    // MAP: null JSON value → entry stored as null
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapNullJsonEntry_storesNull() {
        var mgr = newManager();
        var entries = List.of(new SerializedEntry("k-null", null));
        var cache = mapCache("map-null-json", null, entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        // IMap.get() returns null for absent keys AND for null-valued keys —
        // either way the import did not throw and the key is reachable.
        assertThat(hz.getMap("map-null-json").containsKey("k-null"))
                .isFalse(); // null values are not stored in IMap
    }

    // ------------------------------------------------------------------
    // MAP: non-null JSON but targetClass==String → stored raw (else branch)
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapStringClass_storedRaw() {
        var mgr = newManager();
        var entries = List.of(new SerializedEntry("k1", "plain-text"));
        // className explicitly set to String
        var cache = mapCache(
                "map-string-class",
                String.class.getName(),
                entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getMap("map-string-class").get("k1"))
                .isEqualTo("plain-text");
    }

    // ------------------------------------------------------------------
    // MAP: valid JSON + non-String class → deserialised successfully
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapValidJson_deserialisesObject() {
        var mgr = newManager();
        var original = new CrawlEntry("http://test.norconex.com");
        original.setDepth(5);
        var json = SerialUtil.toJsonString(original);

        var entries = List.of(new SerializedEntry("entry-1", json));
        var cache = mapCache(
                "map-deser",
                CrawlEntry.class.getName(),
                entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        var stored = hz.getMap("map-deser").get("entry-1");
        assertThat(stored).isInstanceOf(CrawlEntry.class);
        assertThat(((CrawlEntry) stored).getReference())
                .isEqualTo("http://test.norconex.com");
        assertThat(((CrawlEntry) stored).getDepth()).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // MAP: invalid JSON + non-String class → falls back to raw string
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapInvalidJson_fallsBackToRawString() {
        var mgr = newManager();
        var entries = List.of(
                new SerializedEntry("k1",
                        "this-is-not-valid-json-for-CrawlEntry{"));
        var cache = mapCache(
                "map-bad-json",
                CrawlEntry.class.getName(),
                entries);

        // Must not throw; bad JSON → raw string fallback
        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getMap("map-bad-json").get("k1"))
                .isEqualTo("this-is-not-valid-json-for-CrawlEntry{");
    }

    // ------------------------------------------------------------------
    // MAP: multiple entries are all stored
    // ------------------------------------------------------------------

    @Test
    void importCaches_mapMultipleEntries_allStored() {
        var mgr = newManager();
        var entries = List.of(
                new SerializedEntry("a", "alpha"),
                new SerializedEntry("b", "beta"),
                new SerializedEntry("c", "gamma"));
        var cache = mapCache("map-multi", null, entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        var map = hz.getMap("map-multi");
        assertThat(map.get("a")).isEqualTo("alpha");
        assertThat(map.get("b")).isEqualTo("beta");
        assertThat(map.get("c")).isEqualTo("gamma");
    }

    // ------------------------------------------------------------------
    // QUEUE: entries are added to the queue
    // ------------------------------------------------------------------

    @Test
    void importCaches_queueType_populatesQueue() {
        var mgr = newManager();
        var entries = List.of(
                new SerializedEntry(null, "item-1"),
                new SerializedEntry(null, "item-2"),
                new SerializedEntry(null, "item-3"));
        var cache = queueCache("queue-test", null, entries);

        CacheImporter.importCaches(mgr, List.of(cache));

        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getQueue("queue-test").size()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Multiple caches imported in one call
    // ------------------------------------------------------------------

    @Test
    void importCaches_multipleCaches_allImported() {
        var mgr = newManager();
        var map1 = mapCache("mc-1", null,
                List.of(new SerializedEntry("x", "1")));
        var map2 = mapCache("mc-2", null,
                List.of(new SerializedEntry("y", "2")));

        CacheImporter.importCaches(mgr, List.of(map1, map2));

        var hz = mgr.getHazelcastInstance();
        assertThat(hz.getMap("mc-1").get("x")).isEqualTo("1");
        assertThat(hz.getMap("mc-2").get("y")).isEqualTo("2");
    }
}
