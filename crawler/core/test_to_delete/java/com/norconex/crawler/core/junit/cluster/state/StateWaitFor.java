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
package com.norconex.crawler.core.junit.cluster.state;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

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
public class StateWaitFor {
    private static final Duration INTERVAL = Duration.ofMillis(100);

    @NonNull
    private final Duration timeout;
    @NonNull
    private final StateDbClient stateDb;

    private int numNodes;

    public StateWaitFor numNodes(int numNodes) {
        this.numNodes = numNodes;
        return this;
    }

    public void toHaveFired(String eventName) {
        if (numNodes < 0) {
            throw new IllegalArgumentException("numNodes must be positive");
        }
        LOG.info("Waiting up to {} for {} nodes to have fired \"{}\" at "
                + "least once...",
                fmt(timeout),
                numNodes,
                eventName);
        var then = System.currentTimeMillis();
        if (!ConcurrentUtil.waitUntil(
                () -> (numNodes == stateDb.getCountsByNodesForTopicAndKey(
                        StateDbClient.TOPIC_EVENT, eventName).size()),
                timeout, INTERVAL)) {
            throwError(eventName);
        }
        LOG.info("{} nodes have fired \"{}\" in {}",
                numNodes,
                eventName,
                fmt(System.currentTimeMillis() - then));
    }

    @SneakyThrows
    private void throwError(String eventName) {
        ExceptionSwallower.swallow(stateDb::printStreamsOrderedByNode);
        throw new TimeoutException(("Less than %s nodes fired \"%s\" "
                + "within %s.").formatted(
                        numNodes,
                        eventName,
                        fmt(timeout)));
    }

    private static String fmt(long d) {
        return DurationFormatter.FULL.format(d);
    }

    private static String fmt(Duration d) {
        return DurationFormatter.FULL.format(d);
    }

}
