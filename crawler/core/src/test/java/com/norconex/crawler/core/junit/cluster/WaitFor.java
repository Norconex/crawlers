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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;
import com.norconex.crawler.core.util.ConcurrentUtil;

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
public class WaitFor {

    @NonNull
    private static final Duration INTERVAL = Duration.ofMillis(100);

    @NonNull
    private final Duration timeout;
    @NonNull
    private final ClusterClient cluster;

    @SneakyThrows
    public void termination() {
        for (Process p : cluster.getNodes()) {
            try {
                if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException(
                            "Process did not terminate within "
                                    + DurationFormatter.FULL.format(timeout));
                }
                var exitCode = p.exitValue();
                if (exitCode != 0) {
                    LOG.warn("Node exit code: {}", exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Node was interrupted.");
            }
        }
    }

    @SneakyThrows
    public void allNodesToHaveFired(String eventName) {
        ConcurrentUtil.waitUntilOrThrow(() -> {
            // we don't care about actual counts, just that all nodes
            // are present
            return cluster.getNodes().size() == cluster.getStateDb()
                    .getCountsByNodesForTopicAndKey(
                            StateDbClient.TOPIC_EVENT, eventName)
                    .size();
        }, timeout, INTERVAL);
    }

}
