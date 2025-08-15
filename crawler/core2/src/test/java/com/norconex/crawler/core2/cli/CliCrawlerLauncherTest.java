/* Copyright 2022-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifierImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core2.cluster.ClusterConnector;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.junit.WithLogLevel;
import com.norconex.crawler.core2.junit.WithTestWatcherLogging;
import com.norconex.crawler.core2.mocks.cli.MockCliEventWriter;
import com.norconex.crawler.core2.mocks.cli.MockCliExit;
import com.norconex.crawler.core2.mocks.cli.MockCliLauncher;
import com.norconex.crawler.core2.mocks.cluster.MockMultiNodesConnector;
import com.norconex.crawler.core2.mocks.cluster.MockSingleNodeConnector;
import com.norconex.crawler.core2.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core2.stubs.CrawlerConfigStubber;
import com.norconex.crawler.core2.util.About;
import com.norconex.crawler.core2.util.ConcurrentUtil;
import com.norconex.crawler.core2.util.LogUtil;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithLogLevel(value = "OFF", classes = CacheManagerNotifierImpl.class)
@WithTestWatcherLogging
class CliCrawlerLauncherTest {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "with  {index} node(s)")
    @ValueSource(ints = { 1, 2 })
    public @interface SingleAndMultiNodesTest {
    }

    // MAYBE: a class for each crawler action/test?
    // MAYBE: write unit tests for <app> help command
    // MAYBE: test with .variables and .properties and system env/props

    @TempDir
    private Path tempDir;

    // Ensures ignite has no hold on the temp files before a cleanup attempt
    // of the tempDir is made by Junit. If we have to do this work around
    // too often, modify the ParameterizedClusterConnectorTest to handle this.
    @AfterEach
    void tearDown() {
        //        GridTestUtil.waitForGridShutdown();
    }

    static Stream<Class<?>> classProvider() {
        return Stream.of(
                MockSingleNodeConnector.class,
                MockMultiNodesConnector.class);
    }

    @SingleAndMultiNodesTest
    @WithLogLevel(
        value = "INFO",
        classes = {
                CliCrawlerLauncherTest.class,
                MockCliLauncher.class,
                MockCliEventWriter.class
        }
    )
    void testNoArgs(int numOfNodes) {
        var exit = launch(numOfNodes);
        assertThat(exit.ok()).isFalse();
        assertThat(exit.getStdErr()).contains("No arguments provided.");
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help configcheck");
    }

    @Test
    void testErrors() throws Exception {
        // Bad args
        var exit1 = launchVerbatim("potato", "--soup");
        assertThat(exit1.ok()).isFalse();
        assertThat(exit1.getStdErr()).contains("Unmatched arguments");

        // Non existant config file
        var exit2 = launchVerbatim(
                "configcheck",
                "-config=" + TimeIdGenerator.next()
                        + "IDontExist");
        assertThat(exit2.ok()).isFalse();
        assertThat(exit2.getStdErr()).contains(
                "Configuration file does not exist");

        // Simulate Picocli Exception
        var exit3 = launchVerbatim("clean", "-config=", "-config=");
        assertThat(exit3.ok()).isFalse();
        assertThat(exit3.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        var exit4 = launchVerbatim("clean", "-config=", "-config=");
        assertThat(exit4.ok()).isFalse();
        assertThat(exit4.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        // Bad config syntax
        var file5 = tempDir.resolve("badConfig.xml");
        Files.writeString(file5, """
                <crawler badAttr="badAttr"></crawler>
                """);
        var exit5 = launchVerbatim("configcheck", "-config=" + file5);
        assertThat(exit5.ok()).isFalse();
        assertThat(exit5.getStdErr()).contains(
                "Unrecognized field \"badAttr\"");

        // Constraint violation
        var file6 = tempDir.resolve("badConfig6.xml");
        Files.writeString(file6, """
                <crawler numThreads="0"></crawler>
                """);
        var exit6 = launchVerbatim("configcheck", "-config=" + file6);
        assertThat(exit6.ok()).isFalse();
        assertThat(exit6.getStdErr()).contains(
                "Invalid value");

    }

    @SingleAndMultiNodesTest
    void testHelp(int numOfNodes) {
        var exit = launch(numOfNodes, "-h");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help",
                "start",
                "stop",
                "configcheck",
                "configrender",
                "clean",
                "storeexport",
                "storeimport");
    }

    @SingleAndMultiNodesTest
    @WithLogLevel(
        value = "INFO",
        classes = {
                CliCrawlerLauncherTest.class,
                MockCliLauncher.class,
                MockCliEventWriter.class,
                About.class,
                CliRunner.class,
                LogUtil.class
        }
    )
    void testVersion(int numOfNodes) {
        var exit = launch(numOfNodes, "-v");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
                "C R A W L E R",
                "Runtime:",
                "Name:",
                "Version:",
                "Vendor:")
                .doesNotContain("null");
    }

    @SingleAndMultiNodesTest
    @WithLogLevel(
        value = "INFO",
        classes = {
                CliCrawlerLauncherTest.class,
                MockCliLauncher.class,
                MockCliEventWriter.class,
                CliConfigCheck.class
        }
    )
    void testConfigCheck(int numOfNodes) {
        var exit = launch(numOfNodes, "configcheck", "-config=");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).containsIgnoringWhitespaces(
                "No configuration errors detected.");
    }

    @SingleAndMultiNodesTest
    @WithLogLevel(
        value = "INFO",
        classes = {
                CliCrawlerLauncherTest.class,
                MockCliLauncher.class,
                MockCliEventWriter.class
        }
    )
    void testBadConfig(int numOfNodes)
            throws IOException {
        var cfgFile = tempDir.resolve("badconfig.xml");
        Files.writeString(cfgFile, """
                <crawler>
                  <numThreads>0</numThreads>
                </crawler>
                """);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> { //NOSONAR
                    launch(numOfNodes,
                            "configcheck",
                            "-config=" + cfgFile
                                    .toAbsolutePath());
                })
                .toString()
                .contains("\"numThreads\" must be greater than or equal to 1.");
    }

    @SingleAndMultiNodesTest
    void testStoreExportImport(int numOfNodes) {
        var exportDir = tempDir.resolve("exportdir");
        var exportFile =
                exportDir.resolve(MockCrawlerBuilder.CRAWLER_ID
                        + ".zip");
        var configFile = CrawlerConfigStubber.writeConfigToDir(
                tempDir, cfg -> {});

        // Export
        var exit1 = launch(
                numOfNodes,
                "storeexport",
                "-config=" + configFile,
                "-dir=" + exportDir);

        assertThat(exit1.ok()).isTrue();
        assertThat(exportFile).isNotEmptyFile();

        // Wait for locks to be released after export
        waitForFileUnlock(exportFile, 30_000, 500);

        // Import
        var exit2 = launch(
                numOfNodes,
                "storeimport",
                "-config=" + configFile,
                "-file=" + exportFile);
        assertThat(exit2.ok()).isTrue();
    }

    /**
     * Waits until the given file is unlocked (not held by any process).
     * @param file the file to check
     * @param timeoutMillis max time to wait
     * @param pollMillis polling interval
     */
    private void waitForFileUnlock(Path file, long timeoutMillis,
            long pollMillis) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try (var channel = Files.newByteChannel(file)) {
                // If we can open the file, it's not locked
                return;
            } catch (IOException e) {
                // File is locked, wait and retry
                try {
                    Thread.sleep(pollMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Interrupted while waiting for file unlock", ie);
                }
            }
        }
        throw new RuntimeException("Timeout waiting for file unlock: " + file);
    }

    @SingleAndMultiNodesTest
    void testStart(int numOfNodes) {
        var exit1 = launch(numOfNodes, "start", "-config=");
        if (!exit1.ok()) {
            LOG.error("Could not start crawler properly. Output:\n{}",
                    exit1);
        }
        assertThat(exit1.ok()).isTrue();

        String[] expected = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        if (numOfNodes <= 1) {
            // Single nodes will receive only one set and we can check for order
            assertThat(exit1.getEvents()).containsExactly(expected);
        } else {
            // Multi nodes we'll make sure they are contained but there will
            // be duplicates so we can't guarantee order.
            assertThat(exit1.getEvents()).contains(expected);
        }
    }

    @SingleAndMultiNodesTest
    void testStartAfterClean(int numOfNodes) {

        var exit2 = launch(
                numOfNodes, "start", "-clean", "-config=");
        assertThat(exit2.ok()).withFailMessage(exit2.getStdErr())
                .isTrue();

        String[] expected = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,

                // Perform cleaning
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,

                // Reset crawl context
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,

                // Regular crawl flow
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        if (numOfNodes <= 1) {
            // Single nodes will receive only one set and we can check for order
            assertThat(exit2.getEvents()).containsExactly(expected);
        } else {
            // Multi nodes we'll make sure they are contained but there will
            // be duplicates so we can't guarantee order.
            assertThat(exit2.getEvents()).contains(expected);
        }

        //TODO verify that crawlstore from previous run was deleted
        // and recreated
    }

    @SingleAndMultiNodesTest
    void testClean(int numOfNodes) {
        var exit = launch(numOfNodes, "clean", "-config=");
        assertThat(exit.ok()).isTrue();
        String[] expected = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };
        if (numOfNodes <= 1) {
            // Single nodes will receive only one set and we can check for order
            assertThat(exit.getEvents()).containsExactly(expected);
        } else {
            // Multi nodes we'll make sure they are contained but there will
            // be duplicates so we can't guarantee order.
            assertThat(exit.getEvents()).contains(expected);
        }

    }

    //TODO move this to its own test with more elaborate tests involving
    // actually stopping a crawl session and being on a cluster?
    @SingleAndMultiNodesTest
    void testStop(int numOfNodes) {
        // we are just testing that the CLI is launching, not that it actually
        // stopped, which is tested separately.
        var exit = launch(numOfNodes, "stop", "-config=");
        assertThat(exit.ok()).isTrue();
    }

    @SingleAndMultiNodesTest
    void testConfigRender(int numOfNodes) throws IOException {
        var cfgFile = CrawlerConfigStubber.writeConfigToDir(tempDir, null);

        var exit1 = launch(numOfNodes, "configrender",
                "-config=" + cfgFile);
        assertThat(exit1.ok()).isTrue();
        // check that some entries not explicitly configured are NOT present
        // (with V4, "default" values are not exported):
        assertThat(exit1.getStdOut()).doesNotContain("<importer");

        var renderedFile = tempDir.resolve("configrender.xml");
        var exit2 = launch(
                numOfNodes,
                "configrender",
                "-config=" + cfgFile,
                "-output=" + renderedFile);

        assertThat(exit2.ok()).isTrue();

        var storedLines = Files.readAllLines(renderedFile);
        var outputLines = exit1.getStdOut().trim().lines().toList();
        if (numOfNodes <= 1) {
            assertThat(storedLines).containsExactlyElementsOf(outputLines);
        } else {
            assertThat(outputLines).containsAll(storedLines);
        }
    }

    @SingleAndMultiNodesTest
    @WithLogLevel(
        value = "INFO",
        classes = {
                CliCrawlerLauncherTest.class,
                MockCliLauncher.class,
                MockCliEventWriter.class
        }
    )
    void testFailingConfigRender(int numOfNodes) {
        var exit = launch(
                numOfNodes,
                "configrender",
                "-config=",
                // passing a directory to get FileNotFoundException
                "-output=" + tempDir.toAbsolutePath());
        assertThat(exit.getStdErr()).contains("FileNotFoundException");
    }

    private MockCliExit launchVerbatim(String... cmdArgs) {
        return MockCliLauncher.launchVerbatim(cmdArgs);
    }

    //NOTE: when testing with multiple nodes, the return MockCliExit
    // will have merged text merged in unpredictable ways as well as
    // merged events. If one exit is not OK, then the one returned is not OK.
    private MockCliExit launch(
            int numOfNodes,
            String... cmdArgs) {
        if (numOfNodes <= 1) {
            return launchWithConnector(new MockSingleNodeConnector(), cmdArgs);
        }

        var executor = Executors.newFixedThreadPool(numOfNodes);
        var latch = new CountDownLatch(numOfNodes);
        final List<MockCliExit> exits = new ArrayList<>();
        //        var failedExit = new AtomicReference<MockCliExit>();
        var thrownException = new AtomicReference<Throwable>();

        for (var i = 0; i < numOfNodes; i++) {
            executor.submit(() -> {
                try {
                    var exit = launchWithConnector(
                            new MockMultiNodesConnector(), cmdArgs);
                    exits.add(exit);
                    //                    if (!exit.ok()) {
                    //                        failedExit.compareAndSet(null, exit);
                    //                    }
                } catch (Throwable t) {
                    thrownException.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to finish
        try {
            latch.await();
        } catch (InterruptedException e) {
            ConcurrentUtil.wrapAsCompletionException(e);
        }
        executor.shutdown();

        // Propagate exception if any
        if (thrownException.get() != null) {
            if (thrownException.get() instanceof RuntimeException re) {
                throw re;
            }
            if (thrownException.get() instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(thrownException.get());
        }

        // Fallback logic
        if (exits.isEmpty()) {
            LOG.info("No CLI launch exit objects returned.");
            return null;
        }

        // Reduce exits in a single one
        var mergedExit = new MockCliExit();
        for (MockCliExit ex : exits) {
            if (mergedExit.getCode() == -1 || mergedExit.ok()) {
                mergedExit.setCode(ex.getCode());
            }
            mergedExit.setStdErr(
                    mergeNonBlank(mergedExit.getStdErr(), ex.getStdErr()));
            mergedExit.setStdOut(
                    mergeNonBlank(mergedExit.getStdOut(), ex.getStdOut()));
            mergedExit.getEvents().addAll(ex.getEvents());
        }
        return mergedExit;
    }

    String mergeNonBlank(String str1, String str2) {
        var b = new StringBuilder();
        if (StringUtils.isNotBlank(str1)) {
            b.append(str1);
        }
        if (StringUtils.isNotBlank(str2)) {
            if (!b.isEmpty()) {
                b.append('\n');
            }
            b.append(str2);
        }
        return b.toString();
    }

    private MockCliExit launchWithConnector(
            ClusterConnector conn,
            String... cmdArgs) {
        return MockCliLauncher
                .builder()
                .args(List.of(cmdArgs))
                .workDir(tempDir)
                .logErrors(true)
                .configModifier(cfg -> cfg.setClusterConnector(conn))
                .build()
                .launch();
    }

    //    //TODO DELETE ME:
    //    private MockCliExit launch(
    //            Class<? extends ClusterConnector> clusterConnClass,
    //            String... cmdArgs) {
    //        return MockCliLauncher
    //                .builder()
    //                .args(List.of(cmdArgs))
    //                .workDir(tempDir)
    //                .logErrors(true)
    //                .configModifier(cfg -> cfg.setClusterConnector(
    //                        ClassUtil.newInstance(clusterConnClass)))
    //                .build()
    //                .launch();
    //    }

}
