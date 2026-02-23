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
package com.norconex.crawler.core.junit.cluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Various cluster waiting operations to facilitate state management with tests.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ClusterWaitFor {

    @NonNull
    private static final Duration INTERVAL = Duration.ofMillis(100);

    @NonNull
    private final Duration timeout;
    @NonNull
    private final ClusterClient cluster;

    /**
     * Waits for all cluster node processes to terminate and returns their
     * exit codes.
     * @return exit codes for all nodes
     */
    @SneakyThrows
    public List<Integer> termination() {
        LOG.info("Waiting up to {} for termination of {} nodes...",
                fmt(timeout),
                cluster.getNodes().size());
        long then = System.currentTimeMillis();
        List<Integer> exitCodes = new ArrayList<>();
        for (Process p : cluster.getNodes()) {
            try {
                if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    printStreams();
                    throw new TimeoutException(
                            "Nodes did not terminate within " + fmt(timeout));
                }
                var exitCode = p.exitValue();
                exitCodes.add(exitCode);
                if (exitCode != 0) {
                    LOG.warn("Node exit code: {}", exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Node was interrupted.");
                exitCodes.add(-1); // Indicate interruption
            }
        }
        LOG.info("Nodes terminated in {}",
                fmt(System.currentTimeMillis() - then));
        return exitCodes;
    }

    @SneakyThrows
    public void allNodesToHaveFired(String eventName) {
        StateDbClient.get()
                .waitFor()
                .numNodes(cluster.getNodes().size())
                .toHaveFired(eventName);

        //        LOG.info("Waiting up to {} for {} nodes to have fired \"{}\" at "
        //                + "least once...",
        //                fmt(timeout),
        //                cluster.getNodes().size(),
        //                eventName);
        //        long then = System.currentTimeMillis();
        //        if (!ConcurrentUtil.waitUntil(() -> {
        //            // we don't care about actual counts, just that all nodes
        //            // are present
        //            return cluster.getNodes().size() == cluster.getStateDb()
        //                    .getCountsByNodesForTopicAndKey(
        //                            StateDbClient.TOPIC_EVENT, eventName)
        //                    .size();
        //        }, timeout, INTERVAL)) {
        //            printStreams();
        //            throw new TimeoutException("Not all nodes fired \"%s\" within %s."
        //                    .formatted(eventName, fmt(timeout)));
        //        }
        //        LOG.info("{} nodes have fired \"{}\" in {}",
        //                cluster.getNodes().size(),
        //                eventName,
        //                fmt(System.currentTimeMillis() - then));
    }

    private void printStreams() {
        // Prefer file-based logs to avoid loading potentially huge
        // cluster_state tables into memory (H2 OOM). This streams
        // each node's stdout/stderr files line-by-line instead.
        cluster.printNodeLogsOrderedByNode();
    }

    private static String fmt(long d) {
        return DurationFormatter.FULL.format(d);
    }

    private static String fmt(Duration d) {
        return DurationFormatter.FULL.format(d);
    }
}
