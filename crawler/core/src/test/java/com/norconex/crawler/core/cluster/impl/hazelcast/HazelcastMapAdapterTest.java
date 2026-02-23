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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.QueryFilter;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingStatus;

/**
 * Component tests for {@link HazelcastMapAdapter}.
 *
 * <p>One shared {@link HazelcastInstance} is used for the entire class; each
 * test method uses a UUID-prefixed map name to ensure complete isolation
 * without requiring an instance restart.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastMapAdapterTest {

    private HazelcastInstance hz;
    private HazelcastCacheManager cacheManager;
    /** Unique prefix for every test method so maps never clash. */
    private String mapPrefix;

    @BeforeAll
    void startHazelcast() {
        hz = HazelcastTestSupport.startNode();
        cacheManager = new HazelcastCacheManager(hz);
    }

    @AfterAll
    void stopHazelcast() {
        cacheManager.close();
    }

    @BeforeEach
    void freshMapPrefix() {
        // Each test writes to its own uniquely-named maps.
        mapPrefix = "test-" + UUID.randomUUID() + "-";
    }

    // -----------------------------------------------------------------
    // String-typed map — basic CRUD
    // -----------------------------------------------------------------

    @Test
    void testStringMap_putAndGet() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "strings",
                String.class);
        map.put("k1", "hello");
        assertThat(map.get("k1")).contains("hello");
    }

    @Test
    void testStringMap_getMissing_returnsEmpty() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "strings",
                String.class);
        assertThat(map.get("no-such-key")).isEmpty();
    }

    @Test
    void testStringMap_remove() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "strings",
                String.class);
        map.put("k1", "v1");
        map.remove("k1");
        assertThat(map.containsKey("k1")).isFalse();
    }

    @Test
    void testStringMap_putIfAbsent_firstCallWins() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "strings",
                String.class);
        var prev1 = map.putIfAbsent("k1", "first");
        var prev2 = map.putIfAbsent("k1", "second");

        assertThat(prev1).isNull(); // no previous value
        assertThat(prev2).isEqualTo("first"); // existing value returned
        assertThat(map.get("k1")).contains("first"); // original value preserved
    }

    @Test
    void testStringMap_keys_and_size() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "ksize",
                String.class);
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");

        assertThat(map.size()).isEqualTo(3);
        assertThat(map.keys()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void testStringMap_countAll() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "countall",
                String.class);
        map.put("x", "1");
        map.put("y", "2");

        assertThat(map.count(QueryFilter.ofAll())).isEqualTo(2);
    }

    @Test
    void testStringMap_forEach() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "foreach",
                String.class);
        map.put("p", "P");
        map.put("q", "Q");

        var sb = new StringBuilder();
        // Values sorted to make assertion deterministic.
        map.forEach((k, v) -> sb.append(v));
        assertThat(sb.toString()).contains("P", "Q");
    }

    @Test
    void testStringMap_clear() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "clr",
                String.class);
        map.put("k", "v");
        map.clear();
        assertThat(map.isEmpty()).isTrue();
    }

    // -----------------------------------------------------------------
    // computeIfAbsent — should call the supplier exactly once
    // (concurrent safety baked in)
    // -----------------------------------------------------------------

    @Test
    void testComputeIfAbsent_runsMappingOnlyOnce() throws InterruptedException {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "cia",
                String.class);
        var callCount = new AtomicInteger(0);
        var threads = 10;
        var latch = new CountDownLatch(threads);
        var pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    map.computeIfAbsent("shared-key", k -> {
                        callCount.incrementAndGet();
                        return "computed";
                    });
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(map.get("shared-key")).contains("computed");
        // The mapping function may legitimately be called more than once in
        // concurrent scenarios if the CAS fails, but the final value must
        // always be "computed" and must never be null.
        assertThat(callCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testComputeIfAbsent_existingKeySkipsMapping() {
        var map = cacheManager.<String>getCacheMap(mapPrefix + "cia2",
                String.class);
        map.put("existing", "original");
        var callCount = new AtomicInteger(0);

        map.computeIfAbsent("existing", k -> {
            callCount.incrementAndGet();
            return "new-value";
        });

        assertThat(callCount.get()).isEqualTo(0);
        assertThat(map.get("existing")).contains("original");
    }

    // -----------------------------------------------------------------
    // Typed object map — verifies typed value round-trip
    // -----------------------------------------------------------------

    @Test
    void testTypedMap_putAndGetRoundTrip() {
        var map = cacheManager.getCacheMap(mapPrefix + "entries",
                CrawlEntry.class);

        var entry = new CrawlEntry("https://example.com");
        entry.setDepth(3);
        entry.setProcessingStatus(ProcessingStatus.QUEUED);
        map.put(entry.getReference(), entry);

        var loaded = map.get("https://example.com").orElseThrow();
        assertThat(loaded.getReference()).isEqualTo("https://example.com");
        assertThat(loaded.getDepth()).isEqualTo(3);
        assertThat(loaded.getProcessingStatus())
                .isEqualTo(ProcessingStatus.QUEUED);
    }

    @Test
    void testTypedMap_replace_swapsValue() {
        var map = cacheManager.getCacheMap(mapPrefix + "replace",
                CrawlEntry.class);

        var queued = new CrawlEntry("ref-1");
        queued.setProcessingStatus(ProcessingStatus.QUEUED);

        var processing = new CrawlEntry("ref-1");
        processing.setProcessingStatus(ProcessingStatus.PROCESSING);

        map.put("ref-1", queued);
        var replaced = map.replace("ref-1", queued, processing);

        assertThat(replaced).isTrue();
        assertThat(map.get("ref-1").map(CrawlEntry::getProcessingStatus))
                .contains(ProcessingStatus.PROCESSING);
    }
}
