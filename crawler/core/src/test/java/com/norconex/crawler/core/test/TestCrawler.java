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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

// Instrumented to return a NodeTestResult
@Slf4j
public class TestCrawler implements Closeable {

    private static final String TEST_RESULT_FILE_NAME = "testResults.json";
    private static final List<Process> ALL_CHILD_PROCESSES =
            new CopyOnWriteArrayList<>();

    static {
        // Add shutdown hook to ensure all child processes are killed when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown hook triggered. Destroying {} child processes.",
                    ALL_CHILD_PROCESSES.size());
            ALL_CHILD_PROCESSES.forEach(process -> {
                if (process.isAlive()) {
                    LOG.info("Destroying child process: {}", process);
                    process.destroyForcibly();
                }
            });
        }, "TestCrawler-ShutdownHook"));
    }

    @Getter
    private final CrawlTestInstrument instrument;
    private final CrawlConfig crawlConfig;
    private final EventNameRecorder eventNameRecorder = new EventNameRecorder();
    private final TestLogAppender logAppender = new TestLogAppender();
    private final CrawlDriver crawlDriver;
    private final boolean client;
    private final AtomicReference<Process> childProcess =
            new AtomicReference<>();

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

    // For debug harness: expose workDir of child process
    Path getWorkDir() {
        return crawlConfig.getWorkDir();
    }

    @Override
    public void close() throws IOException {
        LOG.info("TestCrawler.close() called for cleanup.");
        if (instrument.isRecordEvents()) {
            crawlConfig.removeEventListener(eventNameRecorder);
            LOG.info("EventNameRecorder removed.");
        }
        if (instrument.isRecordLogs()) {
            logAppender.stopCapture();
            LOG.info("LogAppender stopped.");
        }

        // Destroy child process if still running
        var process = childProcess.get();
        if (process != null && process.isAlive()) {
            LOG.info("Destroying child JVM process forcibly.");
            process.destroyForcibly();
            ALL_CHILD_PROCESSES.remove(process);
            try {
                // Wait a bit for the process to terminate
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                    LOG.info("Child JVM process terminated gracefully.");
                } else {
                    LOG.warn(
                            "Child JVM process did not terminate within timeout.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn(
                        "Interrupted while waiting for child process to terminate.");
            }
        }

        // No explicit Hazelcast or CacheManager shutdown here; handled by underlying Crawler.
        LOG.info("TestCrawler.close() cleanup complete.");
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            var instPath = Path.of(args[0]);
            var instrument = CoreTestUtil.readFromFile(instPath,
                    CrawlTestInstrument.class);
            var resultPath = instrument.getCrawlConfig().getWorkDir()
                    .resolve(TEST_RESULT_FILE_NAME);
            System.err.println("XXX INSTRUMENT in MAIN: " + instrument);
            LOG.info("TestCrawler main() starting. PID: {}",
                    ProcessHandle.current().pid());

            try (var crawler = new TestCrawler(instrument, true)) {
                CrawlTestNodeOutput finalResult;
                if (instrument.getRecordInterval() != null) {
                    final var resultHolder =
                            new AtomicReference<CrawlTestNodeOutput>();
                    var crawlThread = new Thread(() -> {
                        LOG.info("crawlThread started. Thread: {}",
                                Thread.currentThread().getName());
                        resultHolder.set(crawler.doCrawl());
                        LOG.info("crawlThread finished. Thread: {}",
                                Thread.currentThread().getName());
                    });
                    crawlThread.start();

                    var scheduler = Executors.newScheduledThreadPool(1);
                    scheduler.scheduleAtFixedRate(() -> {
                        CoreTestUtil.writeToFile(crawler.getResult(),
                                resultPath);
                    }, instrument.getRecordInterval().toMillis(),
                            instrument.getRecordInterval().toMillis(),
                            TimeUnit.MILLISECONDS);
                    try {
                        crawlThread.join();
                        LOG.info("crawlThread joined. Thread: {}",
                                crawlThread.getName());
                    } catch (InterruptedException e) {
                        LOG.warn("Interrupted while joining crawlThread.");
                        Thread.currentThread().interrupt();
                    }
                    scheduler.shutdown();
                    LOG.info("Scheduler shutdown initiated.");
                    try {
                        if (!scheduler.awaitTermination(5,
                                TimeUnit.SECONDS)) {
                            LOG.warn(
                                    "Scheduler did not terminate in time. Calling shutdownNow().");
                            scheduler.shutdownNow();
                        } else {
                            LOG.info("Scheduler terminated cleanly.");
                        }
                    } catch (InterruptedException e) {
                        LOG.warn(
                                "Interrupted while waiting for scheduler termination.");
                        scheduler.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    LOG.info(
                            "After scheduler shutdown. Remaining non-daemon threads:");
                    Thread.getAllStackTraces().keySet().stream()
                            .filter(t -> t.isAlive() && !t.isDaemon())
                            .forEach(t -> LOG.info("Thread: {} (id={})",
                                    t.getName(), t.getId()));
                    Thread.currentThread().interrupt();
                    finalResult = resultHolder.get();
                } else {
                    finalResult = crawler.doCrawl();
                }
                CoreTestUtil.writeToFile(finalResult, resultPath);
                LOG.info("TestCrawler main() finishing. PID: {}",
                        ProcessHandle.current().pid());
                LOG.info("Active non-daemon threads at exit:");
                Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.isAlive() && !t.isDaemon())
                        .forEach(t -> LOG.info("Thread: {} (id={})",
                                t.getName(), t.getId()));
            }
        } catch (IOException e) {
            exitCode = 1;
            LOG.error("TestCrawler main() failed", e);
        } catch (RuntimeException e) {
            exitCode = 1;
            LOG.error("TestCrawler main() failed", e);
        } catch (Error e) {
            exitCode = 1;
            LOG.error("TestCrawler main() failed", e);
        } finally {
            try {
                System.out.flush();
            } catch (Exception ignored) {
                // ignore
            }
            try {
                System.err.flush();
            } catch (Exception ignored) {
                // ignore
            }
            System.exit(exitCode);
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
        var nodeName = instrument.getNodeName();
        Objects.requireNonNull(nodeName, "nodeName is required");

        var workDir = instrument.getWorkDir().resolve(nodeName);
        crawlConfig.setWorkDir(workDir);

        var instrumentPath = workDir.resolve("instrument.yaml");
        CoreTestUtil.writeToFile(instrument, instrumentPath);

        var process = JvmLauncher.builder()
                .mainClass(TestCrawler.class)
                .workDir(workDir)
                .appArg(instrumentPath.toAbsolutePath().toString())
                .stdoutListener(
                        line -> LOG.info("[{}-stdout] {}", nodeName, line))
                .stderrListener(
                        line -> LOG.error("[{}-stderr] {}", nodeName, line))
                .build()
                .start();

        childProcess.set(process);
        ALL_CHILD_PROCESSES.add(process);

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
