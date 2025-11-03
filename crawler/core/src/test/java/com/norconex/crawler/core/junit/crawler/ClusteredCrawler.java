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
package com.norconex.crawler.core.junit.crawler;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.Container.ExecResult;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.junit.cluster.SharedCluster;
import com.norconex.crawler.core.junit.cluster.SharedClusterClient;
import com.norconex.crawler.core.mocks.cli.MockCliEventWriter;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExecUtil;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches a crawler with supplied arguments to all nodes in a cluster.
 */
@Slf4j
@Builder
public final class ClusteredCrawler {

    @Default
    private final Class<? extends Supplier<CrawlDriver>> driverSupplierClass =
            MockCrawlDriverFactory.class;

    @Default
    private final List<WithLogLevel> logLevels = new ArrayList<>();

    private final Consumer<SharedClusterClient> preLaunch;
    private final Consumer<SharedClusterClient> postLaunch;

    @SuppressWarnings("unchecked")
    @Generated // excluded from coverage
    public static void main(String[] args) {
        // We are on a cluster node
        CrawlDriver driver = null;
        try {
            driver = ((Supplier<CrawlDriver>) Class.forName(args[0])
                    .getDeclaredConstructor()
                    .newInstance()).get();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid crawl driver class: " + args[0]);
        }

        var cleanArgs = ArrayUtils.remove(args, 0);
        try {
            // Explicitly exit to terminate the JVM
            System.exit(CliCrawlerLauncher.launch(
                    CachesExporterCrawlDriverWrapper.wrap(driver), cleanArgs));
        } catch (Exception e) {
            e.printStackTrace();
            // Exit with error code on failure
            System.exit(1);
        }
    }

    public ClusteredCrawlOuput launchOne(
            CrawlConfig config, String... cliArgs) {
        return launch(1, config, cliArgs);
    }

    public ClusteredCrawlOuput launchTwo(
            CrawlConfig config, String... cliArgs) {
        return launch(2, config, cliArgs);
    }

    public ClusteredCrawlOuput launchThree(
            CrawlConfig config, String... cliArgs) {
        return launch(3, config, cliArgs);
    }

    public ClusteredCrawlOuput launch(
            int numOfNodes, CrawlConfig config, String... cliArgs) {
        // We are on host
        return SharedCluster.withNodesAndGet(numOfNodes, client -> {
            return launchOnCluster(client, config, cliArgs);
        });
    }

    /**
     * Launches a crawler within an exsiting cluster, from which the client
     * was obtained.  Unless you need to launch the crawler multiple times
     * on the same cluster or need to perform advanced manipulation, it
     * is usually preferable to use {@link #launch(int, CrawlConfig, String...)}
     * @param client shared cluster client
     * @param cfg crawler configuration
     * @param cliArgs command-line arguments
     * @return execution results for all
     */
    public ClusteredCrawlOuput launchOnCluster(
            SharedClusterClient client, CrawlConfig cfg, String... cliArgs) {
        try {
            if (preLaunch != null) {
                preLaunch.accept(client);
            }
            var execResults = doLaunchCrawler(client, cfg, cliArgs);
            if (postLaunch != null) {
                postLaunch.accept(client);
            }

            // Handle timeout case - return early without processing results
            if (execResults.isEmpty()) {
                LOG.warn("No execution results available (likely timeout), "
                        + "returning empty output");
                return new ClusteredCrawlOuput(new ArrayList<>());
            }

            return ClusteredCrawlOutputAggregator.aggregate(
                    execResults, client, cfg);
        } finally {
            // Kill any running Java processes in containers after test completes
            cleanupJavaProcesses(client);
        }
    }

    private List<ExecResult> doLaunchCrawler(
            SharedClusterClient client, CrawlConfig cfg,
            String... cliArgs) {
        // We are on host
        var totalWatch = org.apache.commons.lang3.time.StopWatch
                .createStarted();

        // Set defaults
        var driverSupplCls = driverSupplierClass == null
                ? MockCrawlDriverFactory.class
                : driverSupplierClass;

        var cp = SharedCluster.buildNodeClasspath();
        var cmdArgs = new ArrayList<String>();
        cmdArgs.add("java");
        var debug = ExecUtil.isDebugMode();
        if (debug) {
            cmdArgs.add("-agentlib:jdwp=transport=dt_socket,"
                    + "server=y,suspend=n,address=*:5005");
        }
        var log4jCfg = findNodeLog4j2Config();
        if (log4jCfg != null) {
            cmdArgs.add("-Dlog4j2.configurationFile=" + log4jCfg);
        }

        // Add log level system properties as JVM arguments
        for (WithLogLevel logLevel : logLevels) {
            String level = logLevel.value();
            for (Class<?> clazz : logLevel.classes()) {
                cmdArgs.add("-Dlog4j.logger." + clazz.getName() + "=" + level);
            }
        }

        cmdArgs.add("-Dfile.encoding=UTF8");
        cmdArgs.add("-Djava.net.preferIPv4Stack=true");
        cmdArgs.add("-Djava.net.preferIPv6Addresses=false");
        cmdArgs.add("-Djava.net.disableIPv6=true");
        cmdArgs.add("-cp");
        cmdArgs.add(cp);

        // Prepare config and resolved workdir
        if (cfg != null) {
            if (StringUtils.isBlank(cfg.getId())) {
                cfg.setId("clustered-" + TimeIdGenerator.next());
            }
            cfg.setWorkDir(client.getNodeWorkdir());
            addEventWriter(cfg);
        }

        // Build command
        cmdArgs.add(ClusteredCrawler.class.getName());
        cmdArgs.add(driverSupplCls.getName());
        cmdArgs.addAll(List.of(cliArgs));
        // only add config argument if a config object was passed and at
        // lease one argument (since just passing config is pointless)
        if (cfg != null && cliArgs.length > 0) {
            cmdArgs.add("-config");
            var cfgPath = writeConfigOnCluster(client, cfg)
                    .toString()
                    .replace('\\', '/');
            cmdArgs.add(cfgPath);
        }

        LOG.info("🚀 Launching crawler in containers (setup took: {})",
                totalWatch.formatTime());
        var execWatch = org.apache.commons.lang3.time.StopWatch
                .createStarted();
        var responses = client.execOnCluster(cmdArgs.toArray(new String[] {}));

        try {
            // Wait up to 90 seconds for crawlers to complete
            // If they take longer, the finally block will kill them
            var results = ConcurrentUtil.allOf(responses)
                    .get(90, java.util.concurrent.TimeUnit.SECONDS);
            LOG.info("⏱️  Crawler execution completed in: {}",
                    execWatch.formatTime());
            LOG.info("🏁 Total doLaunchCrawler time: {}",
                    totalWatch.formatTime());
            return results;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("❌ Crawler execution failed after: {}",
                    execWatch.formatTime());
            throw ConcurrentUtil.wrapAsCompletionException(e);
        } catch (java.util.concurrent.TimeoutException e) {
            LOG.warn("⏰ Crawler execution timed out after 90 seconds "
                    + "(total time: {}), will kill processes and gather "
                    + "partial results", totalWatch.formatTime());
            // Return empty list, cleanup will happen in finally block
            return new ArrayList<>();
        }
    }

    //             exit.getEvents().addAll(MockCliEventWriter.parseEvents(eventFile));
    // return exit;

    private Path writeConfigOnCluster(
            SharedClusterClient client, CrawlConfig config) {
        var w = new StringWriter();
        BeanMapper.DEFAULT.write(config, w, Format.YAML);
        var yaml = w.toString();
        System.err.println("XXX writeConfigOnCluster: " + yaml);

        // Force POSIX workDir in YAML (avoid Windows backslashes)
        var nodeWorkDir = client.getNodeWorkdir().toString()
                .replace('\\', '/');
        yaml = yaml.replaceAll(
                "(?m)^workDir:.*$",
                "workDir: \"" + nodeWorkDir + "\"");
        return client.copyStringToClusterFile(yaml, "config.yaml");
    }

    /**
     * Attempts to find a staged log4j2.xml and returns the container path to it
     * if present; otherwise returns null.
     */
    private String findNodeLog4j2Config() {
        try {
            var dirs = Files.list(SharedCluster.HOST_LIB_DIR)
                    .filter(Files::isDirectory)
                    .toList();
            for (String fileName : List.of("log4j2-test.xml", "log4j2.xml")) {
                for (var d : dirs) {
                    var logCfg = d.resolve(fileName);
                    if (Files.exists(logCfg)) {
                        return "file:" + SharedCluster.NODE_LIB_DIR + "/"
                                + d.getFileName() + "/" + fileName;
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed locating log4j2.xml in staged libs.", e);
        }
    }

    private void cleanupJavaProcesses(SharedClusterClient client) {
        try {
            // Kill all java processes that might still be running
            client.getNodes().forEach(node -> {
                try {
                    node.execInContainer("pkill", "-9", "java");
                } catch (Exception e) {
                    LOG.debug(
                            "Could not kill java processes in container {}: {}",
                            node.getNetworkAliases().get(0), e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("Failed to cleanup java processes", e);
        }
    }

    private void addEventWriter(CrawlConfig cfg) {
        var eventFile = cfg.getWorkDir().resolve(
                TimeIdGenerator.next() + "-events.txt");
        System.err.println(
                "XXX Adding event writer with event file: " + eventFile);
        if (cfg.getEventListeners().stream()
                .noneMatch(MockCliEventWriter.class::isInstance)) {
            var eventWriter = new MockCliEventWriter();
            eventWriter.setEventFile(eventFile);
            cfg.addEventListener(eventWriter);
        }
    }
}
