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
package com.norconex.crawler.core.junit;

import java.util.List;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

/**
 * Common interface for crawler execution results, supporting both
 * single-node CLI tests and multi-node cluster tests.
 */
public interface CrawlerExecutionResult {

    default boolean isOK() {
        return getExitCode() == 0 && !hasErrors();
    }

    default boolean hasErrors() {
        var stdout = getStdOut();
        var stderr = getStdErr();
        var combinedOutput = stdout + stderr;
        var lines = combinedOutput.lines().toList();
        var filteredLines = new StringBuilder();
        for (var line : lines) {
            if (line.contains("JGRP000027: failed passing message up")
                    || line.contains(
                            "Cannot invoke \"org.jgroups.protocols"
                                    + ".TpHeader.clusterName()\"")
                    || line.contains(
                            " on net6: java.lang.NullPointerException")
                    || line.contains("ISPN000517: Ignoring cache topology")
                    || line.contains("ExceptionSwallower")
                    || line.contains(
                            "org.jgroups.util.SubmitToThreadPool"
                                    + "$SingleMessageHandler.getClusterName")
                    || line.contains(
                            "org.jgroups.util.SubmitToThreadPool"
                                    + "$SingleMessageHandler.run")
                    || line.contains("ISPN000208")
                    || line.contains("ISPN000452")
                    || line.contains("No live owners found for segments")
                    || line.contains(
                            "Failed to update topology for cache")) {
                continue;
            }
            filteredLines.append(line).append('\n');
        }
        return filteredLines.toString().contains(" ERROR ");
    }

    List<String> getEventNames();

    default Bag<String> getEventNameBag() {
        var bag = new HashBag<String>();
        getEventNames().stream().forEach(bag::add);
        return bag;
    }

    String getStdOut();

    String getStdErr();

    String getFailureSummary();

    int getExitCode();
}
