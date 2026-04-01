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

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.test.CrawlTestDriver;

import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates running two crawler nodes <em>in the same JVM</em> using
 * {@link MockNetworkClusterConnector}.  The Hazelcast mock-network layer
 * eliminates TCP port allocation and drops per-node startup time from
 * seconds to milliseconds.
 *
 * <p>This test proves the embedded multi-instance pattern works for full
 * crawler lifecycle (init → crawl → close) without spawning child JVMs.
 * It uses an in-memory H2 database shared by both nodes.</p>
 */
@Slf4j
@Timeout(60)
@SlowTest
class EmbeddedClusterCrawlTest {

    @TempDir
    java.nio.file.Path tempDir;

    @AfterEach
    void tearDown() {
        HazelcastTestSupport.shutdownAll();
    }

    @Test
    @Timeout(60)
    void twoEmbeddedNodesCrawlAllReferences() throws Exception {
        var numRefs = 8;
        var crawlerId = "embedded-cluster-test";
        var clusterName = "embed-" + UUID.randomUUID().toString()
                .substring(0, 8);

        // Shared H2 in-memory database so both nodes see the same tables.
        var sharedJdbcUrl = "jdbc:h2:mem:" + clusterName
                + ";DB_CLOSE_DELAY=-1";
        var h2SqlMerge = "MERGE INTO \"{tableName}\" (k, v)\n"
                + "KEY(k)\nVALUES (?, ?)";

        var startRefs = java.util.stream.IntStream.range(0, numRefs)
                .mapToObj(i -> "ref-" + i)
                .toList();

        // Collect events from both nodes.
        var allEvents = new CopyOnWriteArrayList<String>();

        // Build per-node configs sharing the same cluster + DB.
        var cfg1 = buildConfig(crawlerId, clusterName,
                sharedJdbcUrl, h2SqlMerge, startRefs,
                tempDir.resolve("node-1"));
        var cfg2 = buildConfig(crawlerId, clusterName,
                sharedJdbcUrl, h2SqlMerge, startRefs,
                tempDir.resolve("node-2"));

        // Attach lightweight event recorders.
        cfg1.addEventListener(event -> {
            if (event instanceof CrawlerEvent ce) {
                allEvents.add(ce.getName());
            }
        });
        cfg2.addEventListener(event -> {
            if (event instanceof CrawlerEvent ce) {
                allEvents.add(ce.getName());
            }
        });

        // Launch both nodes concurrently in the same JVM.
        var f1 = CompletableFuture.runAsync(
                () -> new Crawler(CrawlTestDriver.create(), cfg1).crawl());
        var f2 = CompletableFuture.runAsync(
                () -> new Crawler(CrawlTestDriver.create(), cfg2).crawl());

        CompletableFuture.allOf(f1, f2).get(55, TimeUnit.SECONDS);

        // Both nodes should have completed their crawl lifecycle.
        var importCount = allEvents.stream()
                .filter(CrawlerEvent.DOCUMENT_IMPORTED::equals)
                .count();
        LOG.info("Embedded cluster test: {} imports across 2 nodes",
                importCount);
        assertThat(importCount).isEqualTo(numRefs);
    }

    private static CrawlConfig buildConfig(
            String crawlerId,
            String clusterName,
            String jdbcUrl,
            String sqlMerge,
            List<String> startRefs,
            java.nio.file.Path workDir) {

        var cfg = new CrawlConfig();
        cfg.setId(crawlerId);
        cfg.setWorkDir(workDir);
        cfg.setStartReferences(startRefs);
        cfg.setNumThreads(2);
        cfg.setIdleTimeout(Duration.ofSeconds(2));
        cfg.setFetchers(List.of(Configurable.configure(
                new MockFetcher(),
                fcfg -> fcfg.setDelay(Duration.ofMillis(10)))));

        // Cluster: embedded mock-network connector
        cfg.getClusterConfig().setClustered(true);
        var connector = new MockNetworkClusterConnector();
        cfg.getClusterConfig().setConnector(connector);

        // JDBC + Hazelcast tuning
        var hzConfig = connector.getConfiguration();
        hzConfig.setClusterName(clusterName);

        var configurer =
                (JdbcHazelcastConfigurer) hzConfig.getConfigurer();
        configurer
                .setJdbcUrl(jdbcUrl)
                .setSqlMerge(sqlMerge)
                .setBackupCount(0)
                .setJetEnabled(false);

        return cfg;
    }
}
