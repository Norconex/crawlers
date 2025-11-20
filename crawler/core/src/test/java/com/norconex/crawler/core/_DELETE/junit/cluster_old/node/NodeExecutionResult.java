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
package com.norconex.crawler.core._DELETE.junit.cluster_old.node;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.junit.CrawlerExecutionResult;
import com.norconex.crawler.core.junit.JvmProcess;
import com.norconex.crawler.core.util.CoreTestUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.AccessLevel;
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
@Deprecated
public class NodeExecutionResult implements CrawlerExecutionResult {

    private enum StdType {
        STDOUT, STDERR
    }

    @Getter
    private final Process process;
    @Getter
    private final Path workDir;

    @Override
    public List<String> getEventNames() {
        return NodeEventNamesExporter.parseEventNames(workDir);
    }

    public String getNodeName() {
        return workDir.getFileName().toString();
    }

    @Override
    public int getExitCode() {
        if (!process.isAlive()) {
            return process.exitValue();
        }
        return -1;
    }

    public Properties loadStateProps() {
        return NodeState.load(workDir);
    }

    /**
     * Returns the content of stdout.log as a string.
     * @return the stdout content, or empty string if file doesn't exist
     */
    @Override
    public String getStdOut() {
        return readLogFile(JvmProcess.STDOUT_FILE_NAME);
    }

    /**
     * Returns the content of stderr.log as a string.
     * @return the stderr content, or empty string if file doesn't exist
     */
    @Override
    public String getStdErr() {
        return readLogFile(JvmProcess.STDERR_FILE_NAME);
    }

    /**
     * Gets a summary of errors found in the stdout and stderr logs.
     * @return error summary, or empty string if no errors found
     */
    @Override
    public String getFailureSummary() {
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
        var stdx = stdType == StdType.STDOUT ? getStdOut() : getStdErr();
        var errors = CoreTestUtil.extractErrorLines(stdx);
        if (!errors.isEmpty()) {
            summary.append(stdType + " errors:\n")
                    .append(errors)
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

}
