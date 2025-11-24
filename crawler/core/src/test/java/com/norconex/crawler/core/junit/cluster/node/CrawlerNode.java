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
package com.norconex.crawler.core.junit.cluster.node;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.util.ExecUtil;
import com.norconex.crawler.core.util.ThreadTracker;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Generated;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class CrawlerNode {

    static final String PROP_DRIVER_SUPPL = "node.driverSupplier";
    static final String PROP_CAPTURES = "node.captures";
    static final String PROP_NODE_WORKDIR = "node.workdir";

    // to make triple-certain that the debug port is not reused on nodes
    private static final AtomicInteger launchCount = new AtomicInteger();

    @Singular
    private final List<String> appArgs;
    @Singular
    private final List<String> jvmArgs;
    @Singular
    private final List<WithLogLevel> logLevels;
    private Class<? extends Supplier<CrawlDriver>> driverSupplierClass;

    @NonNull
    @Default
    private final CaptureFlags captures = new CaptureFlags();

    public Process launch(String nodeName, Path nodeWorkDir, Path configFile) {
        var captures = this.captures;
        var jvm = JvmProcess.builder()
                .mainClass(CrawlerNode.class)
                .workDir(nodeWorkDir)
                .appArgs(appArgs);
        applyDebugMode(jvm);
        applyLogLevels(jvm);
        applyJvmArgs(jvm, nodeName, nodeWorkDir, configFile);
        if (captures.isEvents() || captures.isCaches()) {
            jvm.jvmArg(captures.asJvmSysProp());
        }
        // Let JvmProcess redirect stdout/stderr to log files. Do not attach
        // StreamCapturer to stdout/stderr anymore to avoid storing logs in H2
        // and to ensure logs end up in per-node files only.
        return jvm.build().start();
    }

    private void applyJvmArgs(
            JvmProcess.JvmProcessBuilder jvm,
            String nodeName,
            Path nodeWorkDir,
            Path configFile) {

        // Extract cluster root from nodeWorkDir (parent directory)
        var clusterRootDir = nodeWorkDir.getParent();

        jvm.jvmArgs(jvmArgs);
        // Force IPv4 and disable IPv6 to prevent JGroups IPv6 binding warnings
        jvm.jvmArg("-Djava.net.preferIPv4Stack=true");
        jvm.jvmArg("-Djava.net.preferIPv6Addresses=false");
        // Disable IPv6 completely to suppress JGroups interface enumeration
        // warnings
        jvm.jvmArg("-Djava.net.disableIPv6=true");

        // only set config path if one is set, with at least one argument
        if (!appArgs.isEmpty() && configFile != null) {
            jvm.appArg("-config")
                    .appArg(configFile.toAbsolutePath().toString());
        }
        jvm.jvmArg(dArg(PROP_DRIVER_SUPPL,
                Optional.<Class<? extends Supplier<CrawlDriver>>>ofNullable(
                        driverSupplierClass)
                        .orElse(TestCrawlDriverFactory.class).getName()));
        jvm.jvmArg(dArg(PROP_NODE_WORKDIR, nodeWorkDir));
        // Capture flags (events/caches) are passed from launch() when
        // requested; avoid duplicating here.

        if (StateDbClient.isInitialized()) {
            jvm.jvmArg(dArg(StateDbClient.PROP_NODE_NAME, nodeName));
            jvm.jvmArg(dArg(StateDbClient.PROP_JDBC_URL,
                    StateDbClient.get().getJdbcUrl()));
        } else {
            LOG.info("StateDbClient is not initialized.");
        }

        // Set JGroups FILE_PING location to cluster root for node discovery
        if (clusterRootDir != null) {
            var pingLocation =
                    clusterRootDir.resolve("jgroups-ping").toAbsolutePath();
            jvm.jvmArg(dArg("jgroups.ping.location", pingLocation));
        }
    }

    private void applyDebugMode(JvmProcess.JvmProcessBuilder cmd) {
        if (ExecUtil.isDebugMode()) {
            cmd.jvmArg("-agentlib:jdwp=transport=dt_socket,"
                    + "server=y,suspend=n,address=*:"
                    + findAvailableDebugPort(
                            5005 + launchCount.getAndIncrement()));
        }
    }

    private int findAvailableDebugPort(int startPort) {
        for (var port = startPort; port < startPort + 100; port++) {
            try (var socket = new java.net.ServerSocket(port)) {
                return port;
            } catch (java.io.IOException e) {
                // Port is taken, try next
            }
        }
        throw new RuntimeException("No available debug port found");
    }

    // Add log level system properties as JVM arguments
    private void applyLogLevels(JvmProcess.JvmProcessBuilder cmd) {
        for (WithLogLevel logLevel : logLevels) {
            var level = logLevel.value();
            for (Class<?> clazz : logLevel.classes()) {
                cmd.jvmArg("-Dlog4j.logger." + clazz.getName() + "=" + level);
            }
        }
    }

    private String dArg(String key, Object value) {
        return "-D" + key + "=" + value.toString();
    }

    //--- New JVM --------------------------------------------------------------

    @Generated // excluded from coverage
    public static void main(String[] args) {
        var driver = createDriver();

        try {
            LOG.info("Received launch arguments: " + String.join(" ", args));
            var exitCode = CliCrawlerLauncher.launch(driver, args);

            if (LOG.isDebugEnabled()) {
                LOG.debug("CLI Crawler Launcher returned with exit code: {}",
                        exitCode);
                // Log active threads to diagnose what's preventing JVM exit
                logActiveThreads();
            }

            // Use Runtime.halt() to force immediate JVM termination.
            // This is necessary because Infinispan creates non-daemon
            // threads (JGroups transport, cache management) that prevent
            // the JVM from exiting naturally, even after all resources
            // are properly closed. System.exit() waits for non-daemon
            // threads, but Runtime.halt() terminates immediately.
            //     Runtime.getRuntime().halt(exitCode);
        } catch (Exception e) {
            LOG.error("Fatal error during crawler node startup", e);
            // Exit with error code on failure
            //      Runtime.getRuntime().halt(1);
        }
    }

    private static void logActiveThreads() {
        var liveCount = ThreadTracker.getLiveThreadCount();
        var daemonCount = ThreadTracker.getDaemonThreadCount();
        var nonDaemonCount = liveCount - daemonCount;

        LOG.debug("Active threads: total={}, daemon={}, non-daemon={}",
                liveCount, daemonCount, nonDaemonCount);

        // Also log all threads for complete picture
        var allThreads =
                ThreadTracker.allThreadInfos();
        LOG.debug("All threads ({}): ", allThreads.size());
        for (var thread : allThreads) {
            LOG.debug("  - Thread[{}]: name='{}', state={}, isDaemon={}",
                    thread.getThreadId(),
                    thread.getThreadName(),
                    thread.getThreadState(),
                    thread.isDaemon());
        }
    }

    @SuppressWarnings("unchecked")
    private static CrawlDriver createDriver() {
        var driverSuppl = System.getProperty(PROP_DRIVER_SUPPL);
        try {
            Class<?> driverSupplClass = TestCrawlDriverFactory.class;
            if (StringUtils.isNotBlank(driverSuppl)) {
                driverSupplClass = Class.forName(driverSuppl);
            }
            return DriverInstrumentor.instrument(
                    ((Supplier<CrawlDriver>) driverSupplClass
                            .getDeclaredConstructor()
                            .newInstance()).get());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid crawl driver supplier class: " + driverSuppl, e);
        }
    }

}
