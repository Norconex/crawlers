/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.collections4.Bag;

public final class CrawlerExecutionAssertions {

    private CrawlerExecutionAssertions() {
    }

    public static void assertEventSequence(
            List<String> actualEvents,
            String... expectedEvents) {
        assertThat(actualEvents)
                .as("Event sequence should match expected order")
                .containsExactly(expectedEvents);
    }

    public static void assertEventsContainAll(
            List<String> actualEvents,
            String... expectedEvents) {
        assertThat(actualEvents)
                .as("Events should contain all expected events")
                .contains(expectedEvents);
    }

    public static void assertEventCount(
            Bag<String> eventCounts,
            String eventName,
            int expectedCount) {
        assertThat(eventCounts.getCount(eventName))
                .as("Event '%s' should occur %d times",
                        eventName, expectedCount)
                .isEqualTo(expectedCount);
    }

    public static void assertEventCountAtLeast(
            Bag<String> eventCounts,
            String eventName,
            int minCount) {
        assertThat(eventCounts.getCount(eventName))
                .as("Event '%s' should occur at least %d times",
                        eventName, minCount)
                .isGreaterThanOrEqualTo(minCount);
    }

    public static void assertNoErrors(String output) {
        var filteredOutput = filterHarmlessErrors(output);
        assertThat(filteredOutput)
                .as("Output should not contain ERROR markers")
                .doesNotContain(" ERROR ");
    }

    public static String filterHarmlessErrors(String output) {
        if (output == null) {
            return "";
        }
        var lines = output.lines().toList();
        var filteredLines = new StringBuilder();
        for (var line : lines) {
            if (line.contains("JGRP000027: failed passing message up")
                    || line.contains(
                            "Cannot invoke \"org.jgroups.protocols"
                                    + ".TpHeader.clusterName()\"")
                    || line.contains(
                            " on net6: java.lang.NullPointerException")
                    || line.contains(
                            "ISPN000517: Ignoring cache topology")
                    || line.contains("ExceptionSwallower")
                    || line.contains(
                            "org.jgroups.util.SubmitToThreadPool"
                                    + "$SingleMessageHandler")) {
                continue;
            }
            filteredLines.append(line).append('\n');
        }
        return filteredLines.toString();
    }

    public static boolean hasErrors(String output) {
        return filterHarmlessErrors(output).contains(" ERROR ");
    }
}
