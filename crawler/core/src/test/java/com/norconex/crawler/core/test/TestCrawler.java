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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.HzUtil;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.util.CoreTestUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

// Instrumented to return a NodeTestResult
@Slf4j
public class TestCrawler implements Closeable {

    private static final String TEST_RESULT_FILE_NAME = "testResults.json";

    private final CrawlTestInstrument instrument;
    private final CrawlConfig crawlConfig;
    private final EventNameRecorder eventNameRecorder = new EventNameRecorder();
    private final TestLogAppender logAppender = new TestLogAppender();
    private final CrawlDriver crawlDriver;
    private final boolean client;

    public TestCrawler(@NonNull CrawlTestInstrument crawlTestInstrument) {
        this(crawlTestInstrument, false);
    }

    private TestCrawler(
            @NonNull CrawlTestInstrument instrument, boolean remoteInstance) {
        Objects.requireNonNull(instrument.getWorkDir(), "workDir is required");
        crawlConfig = ofNullable(instrument.getCrawlConfig())
                .orElseGet(CrawlConfig::new);
        crawlConfig.setWorkDir(instrument.getWorkDir());
        instrument.setCrawlConfig(crawlConfig);
        this.instrument = instrument;
        client = instrument.isNewJvm() && !remoteInstance;
        crawlDriver = ClassUtil.newInstance(
                instrument.getDriverSupplierClass()).get();

        //NOTE: the config modifier will only exist in the local JVM instance.
        // so we apply it right away here and config changes will be serialized
        // to new JVM, so we don't have to call again.
        if (instrument.getConfigModifier() != null) {
            instrument.getConfigModifier().accept(crawlConfig);
        }
    }

    public CrawlTestNodeOutput crawl() {
        if (instrument.isNewJvm()) {
            return launchNewJvm();
        }
        return doCrawl();
    }

    public CompletableFuture<CrawlTestNodeOutput> crawlAsync() {
        return CompletableFuture.supplyAsync(this::crawl);
    }

    public CrawlTestNodeOutput getResult() {
        if (client) {
            return deserializeNodeTestResults();
        }
        var eventNames = crawlConfig.getEventListeners().stream()
                .filter(EventNameRecorder.class::isInstance)
                .map(EventNameRecorder.class::cast)
                .findFirst()
                .map(EventNameRecorder::getNames)
                .orElse(List.of());

        return CrawlTestNodeOutput.builder()
                .eventNames(eventNames)
                .logLines(logAppender.getMessages())
                .caches(((CachesRecorder) crawlDriver
                        .callbacks()
                        .getAfterCommand())
                                .getCaches())
                .build();
    }

    @Override
    public void close() throws IOException {
        if (instrument.isRecordEvents()) {
            crawlConfig.removeEventListener(eventNameRecorder);
        }
        if (instrument.isRecordLogs()) {
            logAppender.stopCapture();
        }

    }

    public static void main(String[] args) {
        var instPath = Path.of(args[0]);
        var instrument =
                CoreTestUtil.readFromFile(instPath, CrawlTestInstrument.class);
        var resultPath = instrument.getCrawlConfig().getWorkDir()
                .resolve(TEST_RESULT_FILE_NAME);
        System.err.println("XXX INSTRUMENT in MAIN: " + instrument);
        try (var crawler = new TestCrawler(instrument, true)) {
            CrawlTestNodeOutput finalResult;
            if (instrument.getRecordInterval() != null) {
                final var resultHolder =
                        new AtomicReference<CrawlTestNodeOutput>();
                var crawlThread = new Thread(() -> {
                    resultHolder.set(crawler.doCrawl());
                });
                crawlThread.start();

                var scheduler = Executors.newScheduledThreadPool(1);
                scheduler.scheduleAtFixedRate(() -> {
                    CoreTestUtil.writeToFile(crawler.getResult(), resultPath);
                }, instrument.getRecordInterval().toMillis(),
                        instrument.getRecordInterval().toMillis(),
                        TimeUnit.MILLISECONDS);
                try {
                    crawlThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                finalResult = resultHolder.get();
            } else {
                finalResult = crawler.doCrawl();
            }
            CoreTestUtil.writeToFile(finalResult, resultPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CrawlTestNodeOutput doCrawl() {

        instrumentCrawl();

        LOG.info("TestCrawl database properties: {}",
                HzUtil.connectorConfig(crawlConfig).getProperties());

        CrawlTestNodeOutput result = null;
        try {
            new Crawler(crawlDriver, crawlConfig).crawl();
        } finally {
            result = getResult();
        }
        return result;
    }

    private void instrumentCrawl() {
        // record caches
        if (instrument.isRecordCaches()) {
            var consumer = crawlDriver.callbacks().getAfterCommand();
            if (consumer instanceof CachesRecorder rec) {
                rec.setEnabled(true);
            } else {
                LOG.warn("Cache recording requested but crawl driver "
                        + "'afterCommand' callback is not 'CachesRecorder'.");
            }
        }

        // record events
        if (instrument.isRecordEvents()) {
            crawlConfig.addEventListener(eventNameRecorder);
        }

        // record logs
        if (instrument.isRecordLogs()) {
            logAppender.startCapture();
        }

        // Crawl Config modifications
        if (StringUtils.isBlank(crawlConfig.getId())) {
            if (instrument.isClustered()) {
                throw new IllegalArgumentException(
                        "Crawler 'id' must not be blank.");
            }
            crawlConfig.setId("crawl-" + TimeIdGenerator.next());
        }
        crawlConfig.setFetchers(
                List.of(Configurable.configure(new MockFetcher())));

        // Clustering
        crawlConfig.getClusterConfig().setClustered(instrument.isClustered());
        if (instrument.isClustered()) {
            var hzConnector = (HazelcastClusterConnector) crawlConfig
                    .getClusterConfig().getConnector();
            hzConnector.getConfiguration()
                    .setConfigFile("classpath:cache/hazelcast-clustered.yaml");
        }
    }

    private CrawlTestNodeOutput launchNewJvm() {
        Objects.requireNonNull(
                instrument.getNodeName(), "nodeName is required");

        var workDir = instrument.getWorkDir().resolve(instrument.getNodeName());
        crawlConfig.setWorkDir(workDir);

        var instrumentPath = workDir.resolve("instrument.yaml");
        CoreTestUtil.writeToFile(instrument, instrumentPath);

        var process = JvmLauncher.builder()
                .mainClass(TestCrawler.class)
                .workDir(workDir)
                .appArg(instrumentPath.toAbsolutePath().toString())
                .build()
                .start();

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Diagnose if results file is missing. Read child STDOUT/ERR to
        // help identify why nothing was written.
        var resultPath = instrument.getCrawlConfig().getWorkDir()
                .resolve(TEST_RESULT_FILE_NAME);

        if (Files.exists(resultPath)) {
            return deserializeNodeTestResults();
        }

        // If child exited with non-zero code, surface it and logs.
        try {
            var exitValue = process.exitValue();
            LOG.warn("Child JVM exited with code {} and did not produce {}",
                    exitValue, TEST_RESULT_FILE_NAME);
        } catch (IllegalThreadStateException itse) {
            LOG.warn("Child process still alive after waitFor().");
        }

        try {
            var stdout = workDir.resolve(JvmLauncher.STDOUT_FILE_NAME);
            var stderr = workDir.resolve(JvmLauncher.STDERR_FILE_NAME);
            var outText = Files.exists(stdout) ? Files.readString(stdout) : "";
            var errText = Files.exists(stderr) ? Files.readString(stderr) : "";

            var extracted =
                    CoreTestUtil.extractErrorLines(errText + "\n" + outText);
            if (!extracted.isBlank()) {
                LOG.error("Child JVM output (errors):\n{}", extracted);
            }

            LOG.debug("Child JVM full STDOUT:\n{}", outText);
            LOG.debug("Child JVM full STDERR:\n{}", errText);
        } catch (IOException e) {
            LOG.warn("Failed to read child JVM stdout/stderr", e);
        }

        return deserializeNodeTestResults();
    }

    private CrawlTestNodeOutput deserializeNodeTestResults() {
        var resultPath = instrument.getCrawlConfig().getWorkDir()
                .resolve(TEST_RESULT_FILE_NAME);
        if (Files.exists(resultPath)) {
            return CoreTestUtil.readFromFile(resultPath,
                    CrawlTestNodeOutput.class);
        }
        LOG.debug("No node test resutls at : {}", resultPath);
        return new CrawlTestNodeOutput(List.of(), List.of(), Map.of());
    }
}
