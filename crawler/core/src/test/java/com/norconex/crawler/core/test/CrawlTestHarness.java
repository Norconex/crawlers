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

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CrawlTestHarness implements Closeable {

    // Shared across all harness instances: started once per JVM, never
    // stopped mid-run (Testcontainers Ryuk / JVM-shutdown hook cleans it
    // up). Each harness run creates its own PostgreSQL schema for isolation.
    @SuppressWarnings("rawtypes")
    private static final PostgreSQLContainer POSTGRES =
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

    @NonNull
    @Getter
    private final CrawlTestInstrument instrumentTemplate;
    @Getter
    private final String id = "" + TimeIdGenerator.next();

    // Used to isolate concurrent/repeated clustered test runs.
    private final String hazelcastClusterName = "crawler-test-" + id;
    private volatile Integer hazelcastPortBase;
    private volatile Integer hazelcastPortCount;
    // Unique PostgreSQL schema for this harness run — keeps DB tables isolated
    // across concurrent or sequential test invocations.
    private volatile String schemaName;

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
            // Start the shared container on first use; near-zero cost if
            // already running.
            ensurePostgresStarted();
            // Each harness run gets its own schema so parallel or sequential
            // tests never share tables.
            schemaName = "crawltest_" + id;
            createSchema(schemaName);

            // Pick an isolated port range for this harness instance so repeated
            // invocations (e.g., parameterized tests) don't collide on 5701+.
            hazelcastPortCount = Math.max(3, nodeNames.length + 1);
            hazelcastPortBase = findFreeLocalPortRangeBase(hazelcastPortCount);

            LOG.info("Postgres ready: id='{}', schema='{}', jdbcUrl='{}'",
                    POSTGRES.getContainerId(), schemaName,
                    POSTGRES.getJdbcUrl());
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
            LOG.info("=== All node futures completed, collecting results ===");
            Map<String, CrawlTestNodeOutput> results = new HashMap<>();
            futures.forEach((name, future) -> {
                LOG.info("=== Joining result from node: {} ===", name);
                results.put(name, future.join());
                LOG.info("=== Successfully joined result from node: {} ===",
                        name);
            });
            // Note: postgres.stop() is NOT called here anymore to support
            // adding new nodes later or resuming crawls. It will be stopped
            // in close().
            LOG.info("=== Returning CrawlTestHarnessResult with {} nodes ===",
                    results.size());
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

        LOG.info("=== launchSync() called for {} nodes ===", nodeNames.length);
        try {
            LOG.info(
                    "=== Waiting for CompletableFuture.allOf() to complete ===");
            var result = launchAsync(nodeNames).get();
            LOG.info(
                    "=== CompletableFuture.allOf() completed successfully ===");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("=== launchSync() INTERRUPTED ===", e);
            throw new RuntimeException("Interrupted while waiting for crawlers",
                    e);
        } catch (ExecutionException e) {
            LOG.error("=== launchSync() EXECUTION EXCEPTION ===", e);
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

    /**
     * Forcibly crashes a named node by killing its child JVM process.
     * Simulates a hard node failure without any graceful Hazelcast shutdown.
     * No-op if the node is not found or is not running in a child JVM.
     *
     * @param nodeName the node to crash
     */
    public void crashNode(String nodeName) {
        var crawler = nodeCrawlers.get(nodeName);
        if (crawler != null) {
            LOG.info("Crashing node: {}", nodeName);
            crawler.crash();
        } else {
            LOG.warn("crashNode({}): node not found in active crawlers.",
                    nodeName);
        }
    }

    /**
     * Returns {@code true} if the named node's child JVM process is still
     * alive.
     *
     * @param nodeName the node name to check
     * @return {@code true} if the process is alive
     */
    public boolean isNodeAlive(String nodeName) {
        return ofNullable(nodeCrawlers.get(nodeName))
                .map(TestCrawler::isProcessAlive)
                .orElse(false);
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
        // Drop this run's schema; the shared container keeps running for
        // other tests in the same JVM (Ryuk handles final cleanup).
        if (schemaName != null) {
            dropSchema(schemaName);
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

            // Use a test-tuned Hazelcast config to keep clustered tests
            // (especially stop/resume) deterministic and fast on Windows.
            // This avoids long startup delays from eager map-store loading
            // and default partition migrations.
            connConfig.setConfigFile(
                    "classpath:cache/hazelcast-clustered-test.yaml");

            // Reduce nodeExpiryTimeout so the coordinator marks orphaned
            // workers (e.g., from a crash) as EXPIRED within 10 seconds
            // instead of the 30-second default. This is safe for tests
            // because the heartbeat YAML is also set to 1-second intervals.
            connConfig.setNodeExpiryTimeout(java.time.Duration.ofSeconds(10));

            // Ensure this harness run forms its own cluster.
            connConfig.setClusterName(hazelcastClusterName);

            var props = connConfig.getProperties();
            // Append currentSchema so Hibernate/JDBC uses the harness-specific
            // schema and test runs never share or pollute each other's tables.
            var baseJdbcUrl = POSTGRES.getJdbcUrl();
            var sep = baseJdbcUrl.contains("?") ? "&" : "?";
            // matches variables in HZ yaml configuration
            props.setProperty("JDBC_URL",
                    baseJdbcUrl + sep + "currentSchema=" + schemaName);
            props.setProperty("JDBC_USERNAME", POSTGRES.getUsername());
            props.setProperty("JDBC_PASSWORD", POSTGRES.getPassword());
            props.setProperty("JDBC_DRIVER", POSTGRES.getDriverClassName());

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

    private static synchronized void ensurePostgresStarted() {
        if (!POSTGRES.isRunning()) {
            LOG.info("Starting shared PostgreSQL container...");
            POSTGRES.start();
            LOG.info("Shared PostgreSQL container started: jdbcUrl='{}'",
                    POSTGRES.getJdbcUrl());
        }
    }

    private void createSchema(String schema) {
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
            LOG.info("Created isolated PostgreSQL schema: {}", schema);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(
                    "Failed to create schema: " + schema, e);
        }
    }

    private void dropSchema(String schema) {
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            LOG.info("Dropped PostgreSQL schema: {}", schema);
        } catch (java.sql.SQLException e) {
            LOG.warn("Failed to drop schema: {}", schema, e);
        }
    }

}
