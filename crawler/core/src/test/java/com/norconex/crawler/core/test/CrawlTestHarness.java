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
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hazelcast.config.MapStoreConfig.InitialLoadMode;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
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
                    .withPassword("test");

    @NonNull
    @Getter
    private final CrawlTestInstrument instrumentTemplate;
    @Getter
    private final String id = "" + TimeIdGenerator.next();

    // Used to isolate concurrent/repeated clustered test runs.
    private final String hazelcastClusterName = "crawler-test-" + id;
    private volatile Integer hazelcastPortBase;
    private volatile Integer hazelcastPortCount;
    private volatile String sharedCrawlerId;
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
            // Create the schema only on the first launchAsync call for this
            // harness. Crash/resume tests call launchAsync twice on the same
            // harness instance and need to share the same schema across both
            // runs so that the second run can see the first run's ledger data.
            if (schemaName == null) {
                schemaName = "crawltest_" + id;
                createSchema(schemaName);
            }

            // Pick an isolated port range for this harness instance so repeated
            // invocations (e.g., parameterized tests) don't collide on 5701+.
            hazelcastPortCount = Math.max(2, nodeNames.length);
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
        var crawlerId = instrument.getCrawlConfig().getId();
        if (sharedCrawlerId == null) {
            sharedCrawlerId = crawlerId;
        } else if (!StringUtils.equals(sharedCrawlerId, crawlerId)) {
            throw new IllegalStateException(
                    "All nodes in a crawl session must share the same "
                            + "crawler ID. Expected '" + sharedCrawlerId
                            + "' but got '" + crawlerId + "' for node '"
                            + nodeName + "'.");
        }
        if (instrument.isClustered()) {
            var connConfig = ((HazelcastClusterConnector) instrument
                    .getCrawlConfig()
                    .getClusterConfig()
                    .getConnector())
                            .getConfiguration();

            // Reduce nodeExpiryTimeout so the coordinator marks orphaned
            // workers (e.g., from a crash) as EXPIRED within 10 seconds
            // instead of the 30-second default.
            connConfig.setNodeExpiryTimeout(java.time.Duration.ofSeconds(10));

            // Ensure this harness run forms its own cluster.
            connConfig.setClusterName(hazelcastClusterName);

            // Configure the JDBC configurer directly — no YAML file needed.
            var configurer =
                    (JdbcHazelcastConfigurer) connConfig.getConfigurer();
            var baseJdbcUrl = POSTGRES.getJdbcUrl();
            var sep = baseJdbcUrl.contains("?") ? "&" : "?";
            // Include currentSchema for table isolation and sslmode=disable
            // because the Testcontainers Postgres image has no SSL configured.
            configurer
                    .setJdbcUrl(baseJdbcUrl + sep
                            + "currentSchema=" + schemaName
                            + "&sslmode=disable")
                    .setJdbcUsername(POSTGRES.getUsername())
                    .setJdbcPassword(POSTGRES.getPassword())
                    .setJdbcDriver(POSTGRES.getDriverClassName())
                    // Jet is not used by crawler core tests and can add
                    // partition-safe-state waits in small transient clusters.
                    .setJetEnabled(false)
                    // Keep clustered tests deterministic on Windows by relying
                    // on JDBC persistence rather than asynchronous partition
                    // backup migration.
                    .setBackupCount(0)
                    // EAGER loading avoids first-operation stalls in
                    // clustered tests where both nodes begin processing at
                    // nearly the same time.
                    .setInitialLoadMode(InitialLoadMode.EAGER);

            // Apply test-specific Hazelcast tuning for faster failure
            // detection in crash tests.
            configurer.getHazelcastProperties()
                    .put("hazelcast.partition.count", "17");
            configurer.getHazelcastProperties()
                    .put("hazelcast.heartbeat.interval.seconds", "1");
            configurer.getHazelcastProperties()
                    .put("hazelcast.max.no.heartbeat.seconds", "60");
            configurer.getHazelcastProperties()
                    .put("hazelcast.operation.call.timeout.millis", "120000");

            // Set TCP members for cluster discovery using the per-harness port
            // range chosen in launchAsync().
            var base = hazelcastPortBase;
            var count = hazelcastPortCount;
            if (base == null || count == null) {
                // Fallback (should not normally happen)
                base = 5701;
                count = Math.max(2, totalNodes);
            }

            var tcpMembers = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    tcpMembers.append(",");
                }
                tcpMembers.append("127.0.0.1:").append(base + i);
            }
            configurer.setTcpMembers(tcpMembers.toString());

            LOG.info("CrawlTestHarness configured JDBC URL: {}",
                    configurer.getJdbcUrl());
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
        execPsql("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        LOG.info("Created isolated PostgreSQL schema: {}", schema);
    }

    private void dropSchema(String schema) {
        try {
            execPsql("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            LOG.info("Dropped PostgreSQL schema: {}", schema);
        } catch (RuntimeException e) {
            LOG.warn("Failed to drop schema: {}", schema, e);
        }
    }

    /**
     * Runs a single SQL statement inside the Postgres Docker container via
     * {@code psql}. This avoids any TCP connection to the mapped port so
     * schema lifecycle (create/drop) is immune to connection-limit exhaustion
     * and SSL negotiation issues caused by crash-test dangling connections.
     */
    private static void execPsql(String sql) {
        try {
            var result = POSTGRES.execInContainer(
                    "psql",
                    "-U", POSTGRES.getUsername(),
                    "-d", POSTGRES.getDatabaseName(),
                    "-c", sql);
            if (result.getExitCode() != 0) {
                throw new RuntimeException(
                        "psql exited " + result.getExitCode()
                                + ": " + result.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("execInContainer(psql) failed", e);
        }
    }

}
