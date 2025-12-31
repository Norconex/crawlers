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

import java.io.Closeable;
import java.io.IOException;
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

import org.apache.commons.lang3.StringUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanUtil;
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
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test")
    // Prefer waiting for the DB to log that it is ready to accept
    // connections. Waiting only for a listening port can return
    // before Postgres is fully ready to accept connections.
    ;
    //                    .waitingFor(Wait.forLogMessage(
    //                            ".*database system is ready to accept connections.*\\n",
    //                            1)
    //                            .withStartupTimeout(Duration.ofSeconds(60)));

    @NonNull
    @Getter(value = AccessLevel.PACKAGE)
    private final CrawlTestInstrument instrumentTemplate;
    @Getter
    private final String id = "" + TimeIdGenerator.next();

    private final Map<String, TestCrawler> nodeCrawlers = new HashMap<>();
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

            var instrument = instrumentNode(nodeName);

            var testCrawler = new TestCrawler(instrument);
            nodeCrawlers.put(nodeName, testCrawler);
            futures.put(nodeName, CompletableFuture.supplyAsync(
                    testCrawler::crawl, executor));
            LOG.debug("Launched \"{}\".", nodeName);
        }

        var allDone = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> {
            Map<String, CrawlTestNodeOutput> results = new HashMap<>();
            futures.forEach((name, future) -> results.put(name, future.join()));
            if (isClustered) {
                postgres.stop();
            }
            return new CrawlTestHarnessResult(results);
        });
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

    CrawlTestWaitFor waitFor() {
        return new CrawlTestWaitFor(Duration.ofSeconds(30), this);
    }

    CrawlTestWaitFor waitFor(@NonNull Duration timeout) {
        return new CrawlTestWaitFor(timeout, this);
    }

    @Override
    public void close() throws IOException {
        nodeCrawlers
                .forEach((name, crawler) -> ExceptionSwallower.close(crawler));
        executor.shutdownNow();
        postgres.close();
    }

    private CrawlTestInstrument instrumentNode(String nodeName) {
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
            var props = connConfig.getProperties();
            // matches variables in HZ yaml configuration
            props.setProperty("JDBC_URL", postgres.getJdbcUrl());
            props.setProperty("JDBC_USERNAME", postgres.getUsername());
            props.setProperty("JDBC_PASSWORD", postgres.getPassword());
            props.setProperty("JDBC_DRIVER", postgres.getDriverClassName());

            LOG.info("CrawlTestHarness instrumented database properties: {}",
                    props);
        }
        return instrument;
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
