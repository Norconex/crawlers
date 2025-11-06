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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.AccessLevel;
import lombok.Generated;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A crawler node on a crawler cluster, for testing. Each node runs
 * on its own JVM but this class is to be used on the test JVM as a client
 * to the corresponding running node.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CrawlerNode {

    static final String PROP_DRIVER_SUPPL = "node.driverSupplier";
    static final String PROP_EXPORT_EVENTS = "node.exportEvents";
    static final String PROP_EXPORT_CACHES = "node.exportCaches";
    static final String PROP_NODE_WORKDIR = "node.workdir";

    private enum StdType {
        STDOUT, STDERR
    }

    @Getter
    private final Process process;
    @Getter
    private final Path workDir;

    public List<String> getEvents() {
        return NodeEventsExporter.parseEvents(workDir);
    }

    public Properties loadStateProps() {
        return NodeState.load(workDir);
    }

    /**
     * Returns the content of stdout.log as a string.
     * @return the stdout content, or empty string if file doesn't exist
     */
    public String getStdout() {
        return readLogFile(JvmProcess.STDOUT_FILE_NAME);
    }

    /**
     * Returns the content of stderr.log as a string.
     * @return the stderr content, or empty string if file doesn't exist
     */
    public String getStderr() {
        return readLogFile(JvmProcess.STDERR_FILE_NAME);
    }

    /**
     * Checks if the node encountered any errors during execution by examining
     * both stdout and stderr logs for ERROR or Exception patterns. Also checks
     * if the process exited with a non-zero exit code.
     * @return true if errors were detected
     */
    public boolean hasErrors() {
        // Check if process died with error
        if (!process.isAlive()) {
            var exitCode = process.exitValue();
            if (exitCode != 0) {
                return true;
            }
        }

        // Check both stdout and stderr since errors may be logged to either
        var stdout = getStdout();
        var stderr = getStderr();
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
     * Gets a summary of errors found in the stdout and stderr logs.
     * @return error summary, or empty string if no errors found
     */
    public String getErrorSummary() {
        if (!hasErrors()) {
            return "";
        }

        var summary = new StringBuilder();

        // Check for process exit
        if (!process.isAlive()) {
            var exitCode = process.exitValue();
            if (exitCode != 0) {
                summary.append("Process exited with code: ")
                        .append(exitCode).append("\n\n");
            }
        }

        appendStdErrorSummary(summary, StdType.STDOUT);
        appendStdErrorSummary(summary, StdType.STDERR);
        return summary.toString();
    }

    /**
     * Gets the top level files and directories generated during execution for
     * this node.
     * @return files and directories
     */
    public List<Path> listFiles() {
        try {
            return Files.list(getWorkDir()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Stops the crawler node process and closes all streams.
     * This must be called to release file handles before the
     * work directory can be deleted.
     */
    public void close() {
        if (process != null && process.isAlive()) {
            LOG.debug("Stopping crawler node at: {}", workDir);
            process.destroy();
            try {
                // Wait up to 10 seconds for graceful shutdown
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    LOG.warn("Node did not stop gracefully, "
                            + "forcing termination");
                    process.destroyForcibly();
                    // Wait for forcible termination to complete
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for node to stop", e);
                process.destroyForcibly();
            }
        }

        // Close all streams to release file handles
        // (important on Windows for file deletion)
        if (process != null) {
            ExceptionSwallower.closeQuietly(
                    process.getOutputStream(),
                    process.getInputStream(),
                    process.getErrorStream());
        }
    }

    private void appendStdErrorSummary(
            StringBuilder summary, StdType stdType) {
        var stdx = stdType == StdType.STDOUT ? getStdout() : getStderr();
        // Extract error lines
        var stdxErrors = stdx.lines()
                .filter(line -> line.contains(" ERROR ")
                        || line.contains("Exception")
                        || line.contains("Failed to")
                        || line.contains("FAILED")
                        || line.contains("Caused by")
                        || line.contains("at org.")
                        || line.contains("at com.")
                        || line.contains("at java."))
                .limit(100) // Capture full stack traces
                .toList();

        if (!stdxErrors.isEmpty()) {
            summary.append(stdType + " errors:\n")
                    .append(String.join("\n", stdxErrors))
                    .append("\n\n");
        }
    }

    private String readLogFile(String filename) {
        var logFile = workDir.resolve(filename);
        try {
            if (Files.exists(logFile)) {
                return Files.readString(logFile);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read log file: {}", logFile, e);
        }
        return "";
    }

    //--- New JVM --------------------------------------------------------------

    @Generated // excluded from coverage
    public static void main(String[] args) {
        var driver = createDriver();

        try {
            LOG.info("Received launch arguments: " + String.join(" ", args));
            // Explicitly exit to terminate the JVM
            System.exit(CliCrawlerLauncher.launch(driver, args));
        } catch (Exception e) {
            LOG.error("Fatal error during crawler node startup", e);
            // Exit with error code on failure
            System.exit(1);
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
            return CrawlDriverInstrumentor.instrument(
                    ((Supplier<CrawlDriver>) driverSupplClass
                            .getDeclaredConstructor()
                            .newInstance()).get());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid crawl driver supplier class: " + driverSuppl, e);
        }
    }
}
