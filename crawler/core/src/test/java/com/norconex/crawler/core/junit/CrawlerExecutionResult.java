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

    /**
     * Returns true if the execution completed successfully. That is,
     * the exit code is 0 and there were no reported errors.
     *
     * @return {@code true} if execution succeeded, {@code false} otherwise
     */
    default boolean isOK() {
        return getExitCode() == 0 && !hasErrors();
    }

    /**
     * Checks if the node reported any errors during execution (regardless
     * of exit value).
     * @return true if errors were detected
     * @see #isOK()
     */
    default boolean hasErrors() {

        // Check both stdout and stderr since errors may be logged to either
        var stdout = getStdOut();
        var stderr = getStdErr();
        var combinedOutput = stdout + stderr;

        // Filter out entire log lines containing known harmless errors
        // Split by newlines, filter out harmless patterns, rejoin
        var lines = combinedOutput.lines().toList();
        var filteredLines = new StringBuilder();

        for (var line : lines) {
            // Skip lines containing harmless JGroups errors
            if (line.contains("JGRP000027: failed passing message up")
                    || line.contains("Cannot invoke \"org.jgroups.protocols"
                            + ".TpHeader.clusterName()\"")
                    || line.contains(" on net6: java.lang.NullPointerException")
                    || line.contains("ISPN000517: Ignoring cache topology")) {
                continue; // Skip Infinispan merge warnings
            }
            if (line.contains("ExceptionSwallower")
                    || line.contains("org.jgroups.util.SubmitToThreadPool"
                            + "$SingleMessageHandler.getClusterName")
                    || line.contains("org.jgroups.util.SubmitToThreadPool"
                            + "$SingleMessageHandler.run")) {
                continue; // Skip JGroups stack trace lines
            }
            // Skip state transfer / topology transient Infinispan errors that
            // occur during graceful shutdown or cluster merges but do not
            // indicate test failure.
            if (line.contains("ISPN000208")
                    || line.contains("ISPN000452")
                    || line.contains("No live owners found for segments")
                    || line.contains("Failed to update topology for cache")) {
                continue;
            }
            filteredLines.append(line).append('\n');
        }

        // Look for actual errors, not just any exception logging
        // Be careful not to match INFO-level status messages
        return filteredLines.toString().contains(" ERROR ");
        //                || filteredOutput.contains("Exception:")
        //                || (filteredOutput.contains("Pipeline step")
        //                        && filteredOutput.contains("failed"))
        //                || filteredOutput.contains("PersistenceException")
        //                || filteredOutput
        //                        .contains("Crawler terminated with state: FAILED");
    }

    /**
     * Returns all event names from the execution.
     * For single-node: events from that node
     * For cluster: aggregated events from all nodes
     *
     * @return list of event names
     */
    List<String> getEventNames();

    /**
     * Returns event counts (histogram) from the execution.
     * For single-node: event counts from that node
     * For cluster: aggregated event counts from all nodes
     *
     * @return bag of event counts
     */
    default Bag<String> getEventCounts() {
        var bag = new HashBag<String>();
        getEventNames().stream().forEach(bag::add);
        return bag;
    }

    /**
     * Returns standard output from the execution.
     * For single-node: captured stdout
     * For cluster: combined stdout from all nodes
     *
     * @return standard output string
     */
    String getStdOut();

    /**
     * Returns standard error from the execution.
     * For single-node: captured stderr
     * For cluster: combined stderr from all nodes
     *
     * @return standard error string
     */
    String getStdErr();

    /**
     * Gets a summary explaining why this execution result is not OK.
     * @return failure summary, or empty string if successful
     */
    String getFailureSummary();

    /**
     * Returns the exit code. Zero (0) is interpreted as success.
     * @return exit code
     * @see #isOK()
     */
    int getExitCode();
}
