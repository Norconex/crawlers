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

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.norconex.commons.lang.time.DurationFormatter;
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
public class CrawlTestWaitFor {

    @NonNull
    private static final Duration INTERVAL = Duration.ofMillis(100);

    @NonNull
    private final Duration timeout;
    @NonNull
    private final CrawlTestHarness harness;

    @SneakyThrows
    public void allNodesToHaveFired(String eventName) {
        if (!harness.getInstrumentTemplate().isRecordEvents()) {
            throw new IllegalStateException("Events must recorded to check "
                    + "for event-based conditions.");
        }

        if (!ConcurrentUtil.waitUntil(() -> {
            for (var entry : harness.getResults().getNodeOutputs().entrySet()) {
                if (!entry.getValue().getEventNames().contains(eventName)) {
                    return false;
                }
            }
            return true;
        }, timeout, INTERVAL)) {
            throw new TimeoutException("Not all nodes fired \"%s\" within %s."
                    .formatted(eventName, fmt(timeout)));
        }
    }

    //
    //    private static String fmt(long d) {
    //        return DurationFormatter.FULL.format(d);
    //    }

    private static String fmt(Duration d) {
        return DurationFormatter.FULL.format(d);
    }
}
