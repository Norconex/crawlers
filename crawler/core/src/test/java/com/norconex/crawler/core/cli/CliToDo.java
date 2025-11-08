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
package com.norconex.crawler.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifierImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.mocks.cli.MockCliEventWriter;
import com.norconex.crawler.core.mocks.cli.MockCliLauncher_DELETE;
import com.norconex.crawler.core.util.About;
import com.norconex.crawler.core.util.LogUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithLogLevel(value = "OFF", classes = CacheManagerNotifierImpl.class)
@WithLogLevel(
    value = "INFO",
    classes = {
            CliToDo.class,
            MockCliLauncher_DELETE.class,
            MockCliEventWriter.class,
            About.class,
            CliRunner.class,
            LogUtil.class,
            CliConfigCheck.class
    }
)
@WithTestWatcherLogging
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@Deprecated
class CliToDo {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "with  {index} node(s)")
    @ValueSource(ints = { 1, 2 })
    @Deprecated
    public @interface SingleAndMultiNodesTest {
    }

    // MAYBE: a class for each crawler action/test?
    // MAYBE: write unit tests for <app> help command
    // MAYBE: test with .variables and .properties and system env/props

    //    @Test
    //    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    //    void testStoreExportImport() {
    //        var exportDir = SharedCluster.NODE_BASE_WORKDIR + "/exports";
    //        SharedCluster.withNodes(1, client -> {
    //            // Export
    //            var exportOuput = ClusteredCrawler.builder()
    //                    .build()
    //                    .launchOnCluster(client, new CrawlConfig(), "storeexport",
    //                            "-dir", exportDir);
    //            assertThat(exportOuput.getNode1().getExitCode()).isZero();
    //
    //            var exportedFile = exportOuput.getNode1().getStdout().replaceFirst(
    //                    "(?ms).*?Storage exported to file: (.*?\\.zip).*$", "$1");
    //
    //            assertThat(exportedFile).endsWith(".zip");
    //
    //            // Give the file system time to release locks
    //            Thread.sleep(500); //NOSONAR
    //
    //            // Import
    //            var importResults = ClusteredCrawler.builder()
    //                    .postLaunch(cl -> cl.execOnCluster("rm", exportedFile))
    //                    .build()
    //                    .launchOnCluster(client, new CrawlConfig(), "storeimport",
    //                            "-file", exportedFile);
    //            assertThat(importResults.getNode1().getExitCode()).isZero();
    //        });
    //    }

    @SingleAndMultiNodesTest
    @Disabled
    void testStop(int numOfNodes) {
        // we are just testing that the CLI is launching, not that it actually
        // stopped, which is tested separately.
        // when testing with two nodes, it should be smart enough to not stop
        // it twice
        //        var exit = launch(numOfNodes, "stop", "-config=");
        //        assertThat(exit.ok()).isTrue();
    }

    //    private MockCliExit launchVerbatim(String... cmdArgs) {
    //        return MockCliLauncher_DELETE.launchVerbatim(cmdArgs);
    //    }
    //
    //    //NOTE: when testing with multiple nodes, the return MockCliExit
    //    // will have merged text merged in unpredictable ways as well as
    //    // merged events. If one exit is not OK, then the one returned is not OK.
    //    @Deprecated
    //    private MockCliExit launch(
    //            int numOfNodes,
    //            String... cmdArgs) {
    //        // For commands that operate on shared persistent state and are not
    //        // designed to run concurrently from multiple nodes in this mock
    //        // test environment, execute only once to avoid Infinispan entering
    //        // degraded mode (nodes leaving while state transfer pending) which
    //        // was causing InterruptedException/timeouts.
    //        if (numOfNodes > 1 && cmdArgs != null && cmdArgs.length > 0) {
    //            var cmd = cmdArgs[0];
    //            if (isSingleExecutionCommand(cmd)) {
    //                LOG.info("Executing single-execution command '{}' once for {} "
    //                        + "mock nodes to prevent cluster degradation.",
    //                        cmd, numOfNodes);
    //                return launchWithConnector(new MockMultiNodesConnector(),
    //                        cmdArgs);
    //            }
    //        }
    //        if (numOfNodes <= 1) {
    //            return launchWithConnector(new MockSingleNodeConnector(), cmdArgs);
    //        }
    //
    //        var executor = Executors.newFixedThreadPool(numOfNodes);
    //        var latch = new CountDownLatch(numOfNodes);
    //        final List<MockCliExit> exits = new ArrayList<>();
    //        var thrownException = new AtomicReference<Throwable>();
    //
    //        for (var i = 0; i < numOfNodes; i++) {
    //            final var nodeIndex = i;
    //            executor.submit(() -> {
    //                try {
    //                    var nodeDir = tempDir.resolve("node-" + nodeIndex);
    //                    Files.createDirectories(nodeDir);
    //                    var exit = launchWithConnectorAndWorkDir(
    //                            new MockMultiNodesConnector(), nodeDir, cmdArgs);
    //                    exits.add(exit);
    //                } catch (Throwable t) {
    //                    thrownException.compareAndSet(null, t);
    //                } finally {
    //                    latch.countDown();
    //                }
    //            });
    //        }
    //
    //        // Wait for all threads to finish
    //        try {
    //            latch.await();
    //        } catch (InterruptedException e) {
    //            ConcurrentUtil.wrapAsCompletionException(e);
    //        }
    //        executor.shutdown();
    //        try {
    //            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
    //                LOG.warn(
    //                        "Executor did not terminate in time, forcing shutdown.");
    //                executor.shutdownNow();
    //            }
    //        } catch (InterruptedException e) {
    //            executor.shutdownNow();
    //            Thread.currentThread().interrupt();
    //        }
    //
    //        // Propagate exception if any
    //        if (thrownException.get() != null) {
    //            if (thrownException.get() instanceof RuntimeException re) {
    //                throw re;
    //            }
    //            if (thrownException.get() instanceof Error err) {
    //                throw err;
    //            }
    //            throw new RuntimeException(thrownException.get());
    //        }
    //
    //        // Fallback logic
    //        if (exits.isEmpty()) {
    //            LOG.info("No CLI launch exit objects returned.");
    //            return null;
    //        }
    //
    //        // Reduce exits in a single one
    //        var mergedExit = new MockCliExit();
    //        for (MockCliExit ex : exits) {
    //            if (mergedExit.getCode() == -1 || mergedExit.ok()) {
    //                mergedExit.setCode(ex.getCode());
    //            }
    //            mergedExit.setStdErr(
    //                    mergeNonBlank(mergedExit.getStdErr(), ex.getStdErr()));
    //            mergedExit.setStdOut(
    //                    mergeNonBlank(mergedExit.getStdOut(), ex.getStdOut()));
    //            mergedExit.getEvents().addAll(ex.getEvents());
    //        }
    //        return mergedExit;
    //    }
    //
    //    String mergeNonBlank(String str1, String str2) {
    //        var b = new StringBuilder();
    //        if (StringUtils.isNotBlank(str1)) {
    //            b.append(str1);
    //        }
    //        if (StringUtils.isNotBlank(str2)) {
    //            if (!b.isEmpty()) {
    //                b.append('\n');
    //            }
    //            b.append(str2);
    //        }
    //        return b.toString();
    //    }
    //
    //    private MockCliExit launchWithConnector(
    //            ClusterConnector conn,
    //            String... cmdArgs) {
    //        return launchWithConnectorAndWorkDir(conn, tempDir, cmdArgs);
    //    }
    //
    //    private MockCliExit launchWithConnectorAndWorkDir(
    //            ClusterConnector conn,
    //            Path workDir,
    //            String... cmdArgs) {
    //        return MockCliLauncher_DELETE
    //                .builder()
    //                .args(List.of(cmdArgs))
    //                .workDir(workDir)
    //                .logErrors(true)
    //                .configModifier(cfg -> cfg.setClusterConnector(conn))
    //                .build()
    //                .launch();
    //    }
    //
    //    private boolean isSingleExecutionCommand(String cmd) {
    //        if (cmd == null) {
    //            return false;
    //        }
    //        return switch (cmd.toLowerCase()) {
    //            case "storeexport", "storeimport", "configrender", "clean" -> true;
    //            default -> false;
    //        };
    //    }
    //
    //    private void assertMultiNodeEventsNotEmpty(MockCliExit exit, int num) {
    //        if (num > 1) {
    //            assertThat(exit.getEvents())
    //                    .as("Expected some events for multi-node execution")
    //                    .isNotEmpty();
    //        }
    //    }
    //
    //    // Remove events we know were added just for testing but are normally
    //    // not there
    //    private List<String> removeArtificalEvents(List<String> events) {
    //        return ListUtils.removeAll(events, List.of(
    //                CrawlerEvent.CRAWLER_STORE_EXPORT_BEGIN,
    //                CrawlerEvent.CRAWLER_STORE_EXPORT_END));
    //    }
}
