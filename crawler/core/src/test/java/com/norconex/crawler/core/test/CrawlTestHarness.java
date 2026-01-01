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
package com.norconex.crawler.core.test;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.collections4.OrderedMap;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CrawlTestHarness implements Closeable {

    // Configure container with explicit waiting strategy and credentials so
    // tests are less likely to race on startup and we can log mapped port.
    PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test")
                    // Prefer waiting for the DB to log that it is ready to accept
                    // connections. Waiting only for a listening port can return
                    // before Postgres is fully ready to accept connections.
                    .waitingFor(Wait.forLogMessage(
                            ".*database system is ready to accept connections.*\\n",
                            1)
                            .withStartupTimeout(Duration.ofSeconds(60)));
    //                    .waitingFor(Wait.forLogMessage(
    //                            ".*database system is ready to accept connections.*\\n",
    //                            1)
    //                            .withStartupTimeout(Duration.ofSeconds(60)));

    @NonNull
    @Getter(value = AccessLevel.PACKAGE)
    private final CrawlTestInstrument instrumentTemplate;
    @Getter
    private final String id = "" + TimeIdGenerator.next();

    // Used to isolate concurrent/repeated clustered test runs.
    private final String hazelcastClusterName = "crawler-test-" + id;
    private volatile Integer hazelcastPortBase;
    private volatile Integer hazelcastPortCount;

    private final OrderedMap<String, TestCrawler> nodeCrawlers =
            new ListOrderedMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<CrawlTestHarnessResult> launchAsync(
            @NonNull String... nodeNames) {
        if (Arrays.stream(nodeNames).anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Node name must not be null.");
        }
        if (Arrays.stream(nodeNames).distinct().count() != nodeNames.length) {
            throw new IllegalArgumentException("Node names must be unique.");
        }

        var isClustered = instrumentTemplate.isClustered();
        if (isClustered) {
            // TestCrawler will know to grab the right HZ cfg when clustered
            postgres.start();

            // Pick an isolated port range for this harness instance so repeated
            // invocations (e.g., parameterized tests) don't collide on 5701+.
            hazelcastPortCount = Math.max(3, nodeNames.length + 1);
            hazelcastPortBase = findFreeLocalPortRangeBase(hazelcastPortCount);

            // Log actual connection info so we can diagnose refused connections
            try {
                LOG.info("Postgres container started: id='{}', jdbcUrl='{}'",
                        //                        , "
                        //                        + "host='{}', mappedPort={}",
                        postgres.getContainerId(), postgres.getJdbcUrl());
                //                        postgres.getHost(),
                //                        postgres.getMappedPort(5432));
            } catch (Exception e) {
                LOG.warn("Could not determine mapped Postgres port: {}",
                        e.getMessage());
            }
        }

        Map<String, CompletableFuture<CrawlTestNodeOutput>> futures =
                new ConcurrentHashMap<>();
        for (String nodeName : nodeNames) {

            var instrument = instrumentNode(nodeName, nodeNames.length);

            var testCrawler = new TestCrawler(instrument);
            nodeCrawlers.put(nodeName, testCrawler);

            var future = CompletableFuture.supplyAsync(
                    testCrawler::crawl, executor);

            futures.put(nodeName, future);
            LOG.debug("Launched \"{}\".", nodeName);
        }

        var allDone = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> {
            Map<String, CrawlTestNodeOutput> results = new HashMap<>();
            futures.forEach((name, future) -> results.put(name, future.join()));
            // Note: postgres.stop() is NOT called here anymore to support
            // adding new nodes later or resuming crawls. It will be stopped
            // in close().
            return new CrawlTestHarnessResult(results);
        });
    }

    public CrawlTestInstrument getCrawlInstrument(String nodeName) {
        return ofNullable(nodeCrawlers.get(nodeName))
                .map(TestCrawler::getInstrument)
                .orElse(null);
    }

    public CrawlConfig getFirstNodeConfig() {
        if (nodeCrawlers.isEmpty()) {
            return null;
        }
        return nodeCrawlers.get(nodeCrawlers.firstKey())
                .getInstrument()
                .getCrawlConfig();
    }

    public CrawlTestHarnessResult launchSync(@NonNull String... nodeNames) {

        try {
            return launchAsync(nodeNames).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for crawlers",
                    e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to launch crawlers",
                    e.getCause());
        }
    }

    public CrawlTestHarnessResult getResults() {
        Map<String, CrawlTestNodeOutput> results = new HashMap<>();
        nodeCrawlers.forEach((name, crawler) -> {
            results.put(name, crawler.getResult());
        });
        return new CrawlTestHarnessResult(results);
    }

    public Optional<CrawlTestNodeOutput> getNodeOutput(String nodeName) {
        var crawler = nodeCrawlers.get(nodeName);
        if (crawler != null) {
            return Optional.ofNullable(crawler.getResult());
        }
        return Optional.empty();
    }

    public CrawlTestWaitFor waitFor() {
        return new CrawlTestWaitFor(Duration.ofSeconds(30), this);
    }

    public CrawlTestWaitFor waitFor(@NonNull Duration timeout) {
        return new CrawlTestWaitFor(timeout, this);
    }

    @Override
    public void close() throws IOException {
        LOG.info("CrawlTestHarness.close() called for cleanup.");
        nodeCrawlers.forEach((name, crawler) -> {
            LOG.info("Closing TestCrawler node: {}", name);
            // Crawlers may already be closed from whenComplete() callback
            ExceptionSwallower.close(crawler);
        });
        executor.shutdownNow();
        LOG.info("ExecutorService shutdown.");
        if (postgres.isRunning()) {
            postgres.stop();
            LOG.info("PostgreSQL container stopped.");
        } else {
            LOG.info("PostgreSQL container already stopped.");
        }
        LOG.info("CrawlTestHarness.close() cleanup complete.");
    }

    private CrawlTestInstrument instrumentNode(String nodeName,
            int totalNodes) {
        var instrument = newInstrumentFromTemplate();
        instrument.setNodeName(nodeName);
        if (instrument.getCrawlConfig().getId() == null) {
            instrument.getCrawlConfig().setId(id);
        }
        if (instrument.isClustered()) {
            var connConfig = ((HazelcastClusterConnector) instrument
                    .getCrawlConfig()
                    .getClusterConfig()
                    .getConnector())
                            .getConfiguration();

            // Ensure this harness run forms its own cluster.
            connConfig.setClusterName(hazelcastClusterName);

            var props = connConfig.getProperties();
            // matches variables in HZ yaml configuration
            props.setProperty("JDBC_URL", postgres.getJdbcUrl());
            props.setProperty("JDBC_USERNAME", postgres.getUsername());
            props.setProperty("JDBC_PASSWORD", postgres.getPassword());
            props.setProperty("JDBC_DRIVER", postgres.getDriverClassName());

            // Set TCP members for cluster discovery using the per-harness port
            // range chosen in launchAsync(). HazelcastCluster will apply this
            // list to the loaded Config before instance creation.
            var base = hazelcastPortBase;
            var count = hazelcastPortCount;
            if (base == null || count == null) {
                // Fallback (should not normally happen)
                base = 5701;
                count = Math.max(3, totalNodes + 1);
            }

            var tcpMembers = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    tcpMembers.append(",");
                }
                tcpMembers.append("127.0.0.1:").append(base + i);
            }
            connConfig.setTcpMembers(tcpMembers.toString());

            LOG.info("CrawlTestHarness instrumented database properties: {}",
                    props);
        }
        return instrument;
    }

    private static int findFreeLocalPortRangeBase(int rangeSize) {
        if (rangeSize <= 0) {
            throw new IllegalArgumentException("rangeSize must be > 0");
        }

        // Keep the range in a generally safe ephemeral-ish window and avoid
        // running past the max port.
        var rnd = ThreadLocalRandom.current();
        var host = InetAddress.getLoopbackAddress();
        int minBase = 20_000;
        int maxBase = 60_000 - rangeSize;

        for (int attempt = 0; attempt < 200; attempt++) {
            int base = rnd.nextInt(minBase, Math.max(minBase + 1, maxBase));
            if (isLocalPortRangeFree(host, base, rangeSize)) {
                LOG.info("Selected Hazelcast port range base={} (size={})",
                        base, rangeSize);
                return base;
            }
        }

        throw new IllegalStateException(
                "Could not find a free localhost port range of size "
                        + rangeSize);
    }

    private static boolean isLocalPortRangeFree(
            InetAddress host, int base, int rangeSize) {
        for (int i = 0; i < rangeSize; i++) {
            int port = base + i;
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(host, port));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private CrawlTestInstrument newInstrumentFromTemplate() {
        var instrument = BeanUtil.clone(instrumentTemplate);
        if (instrument.getCrawlConfig() == null) {
            var driver = ClassUtil
                    .newInstance(instrument.getDriverSupplierClass()).get();
            var cfg = ClassUtil.newInstance(driver.crawlerConfigClass());
            instrument.setCrawlConfig(cfg);
        }
        return instrument;
    }

}
