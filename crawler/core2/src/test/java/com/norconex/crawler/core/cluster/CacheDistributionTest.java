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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Duration;

import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanCacheManager;

class CacheDistributionTest {

    private DefaultCacheManager cm1;
    private DefaultCacheManager cm2;
    private InfinispanCacheManager icm1;
    private InfinispanCacheManager icm2;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure we bind to localhost for tests
        System.setProperty("jgroups.bind_addr", "127.0.0.1");

        cm1 = new DefaultCacheManager(
                loadXml("cache/infinispan-cluster-test.xml"));
        cm2 = new DefaultCacheManager(
                loadXml("cache/infinispan-cluster-test.xml"));

        icm1 = new InfinispanCacheManager(cm1);
        icm2 = new InfinispanCacheManager(cm2);

        // Start the caches we will use to trigger transport init
        cm1.getCache(CacheNames.CRAWL_RUN);
        cm2.getCache(CacheNames.CRAWL_RUN);

        waitForCluster(cm1, cm2, CacheNames.CRAWL_RUN, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        if (icm1 != null) {
            icm1.close();
        }
        if (icm2 != null) {
            icm2.close();
        }
    }

    @Test
    void crawlRunId_isConsistentAcrossNodes() {
        var runCache1 = icm1.getCrawlRunCache();
        var runCache2 = icm2.getCrawlRunCache();

        var id1 = runCache1.computeIfAbsent("crawlRunId", k -> "cr-1");
        var id2 = runCache2.computeIfAbsent("crawlRunId", k -> "cr-2");

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void crawlSessionValue_propagatesAcrossNodes() throws Exception {
        var session1 = icm1.getCrawlSessionCache();
        var session2 = icm2.getCrawlSessionCache();

        session1.put("crawlRunInfo", "json-abc");

        var v = waitUntil(() -> session2.get("crawlRunInfo").orElse(null),
                Duration.ofSeconds(10));
        assertThat(v).isEqualTo("json-abc");
    }

    @Test
    void dynamicAdminCache_isDistributed() throws Exception {
        var admin1 = icm1.getAdminCache();
        var admin2 = icm2.getAdminCache();

        admin1.put("k", "v");

        var v = waitUntil(() -> admin2.get("k").orElse(null),
                Duration.ofSeconds(10));
        assertThat(v).isEqualTo("v");
    }

    // --- helpers ------------------------------------------------------------

    private static InputStream loadXml(String cp) {
        var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(cp);
        if (is == null) {
            throw new IllegalStateException("Missing resource: " + cp);
        }
        return is;
    }

    private static void waitForCluster(DefaultCacheManager a,
            DefaultCacheManager b,
            String cacheName, Duration timeout) throws Exception {
        var ca = a.getCache(cacheName);
        var cb = b.getCache(cacheName);

        var k1 = "probe-" + System.nanoTime();
        var v1 = "v1";
        ca.put(k1, v1);
        var got1 = waitUntil(() -> (String) cb.get(k1), timeout);
        if (!v1.equals(got1)) {
            throw new AssertionError(
                    "Cluster did not propagate from A to B for key " + k1);
        }

        var k2 = "probe-" + System.nanoTime();
        var v2 = "v2";
        cb.put(k2, v2);
        var got2 = waitUntil(() -> (String) ca.get(k2), timeout);
        if (!v2.equals(got2)) {
            throw new AssertionError(
                    "Cluster did not propagate from B to A for key " + k2);
        }
    }

    private static <T> T waitUntil(java.util.concurrent.Callable<T> c,
            Duration timeout) throws Exception {
        var end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            var v = c.call();
            if (v != null) {
                return v;
            }
            Sleeper.sleepMillis(100);
        }
        return null;
    }
}