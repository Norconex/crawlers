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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.testcontainers.containers.Container.ExecResult;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.cluster.SharedCluster;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExecUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches a crawler with supplied arguments to all nodes in a cluster.
 */
@Slf4j
public final class ClusteredCrawler {

    private ClusteredCrawler() {

    }

    public static List<CompletableFuture<ExecResult>> launchAsync(
            int numOfNodes, @NonNull CrawlConfig crawlConfig, String... args) {
        //TODO have various methods that prepares the config and ultimately call doLaunch
        return doLaunch(numOfNodes, args);
    }

    public static List<ExecResult> launchSync(
            int numOfNodes, @NonNull CrawlConfig crawlConfig, String... args) {
        try {
            return ConcurrentUtil.allOf(doLaunch(numOfNodes, args)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }

    private static List<CompletableFuture<ExecResult>> doLaunch(
            int numOfNodes, String... args) {
        var cp = SharedCluster.buildNodeClasspath();

        var cmdArgs = new ArrayList<String>();
        cmdArgs.add("java");
        var debug = ExecUtil.isDebugMode();
        if (debug) {
            cmdArgs.add(
                    "-agentlib:jdwp=transport=dt_socket,"
                            + "server=y,suspend=n,address=*:5005");
        }
        var log4jCfg = findNodeLog4j2Config();
        if (log4jCfg != null) {
            cmdArgs.add("-Dlog4j2.configurationFile=" + log4jCfg);
        }
        cmdArgs.add("-Dfile.encoding=UTF8");
        cmdArgs.add("-Djava.net.preferIPv4Stack=true");
        cmdArgs.add("-cp");
        cmdArgs.add(cp);
        cmdArgs.add(MockCrawler.class.getName());
        cmdArgs.addAll(List.of(args));

        return SharedCluster.withNodesAndGet(numOfNodes, client -> {
            var responses =
                    client.execOnCluster(cmdArgs.toArray(new String[] {}));
            if (debug) {
                client.getNodes().forEach(n -> LOG.warn(
                        "Attach your IDE to port {} for debugging node {}.",
                        n.getMappedPort(5005),
                        n.getNetworkAliases().get(0)));
            }
            return responses;
        });
    }

    /**
     * Attempts to find a staged log4j2.xml and returns the container path to it
     * if present; otherwise returns null.
     */
    private static String findNodeLog4j2Config() {
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
}