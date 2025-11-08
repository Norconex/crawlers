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
package com.norconex.crawler.core.cluster.impl.infinispan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Controls crawler stop requests in standalone mode via a file-based
 * mechanism. Used when the crawler is running in non-clustered mode
 * and cannot use the Infinispan cache for stop signaling.
 */
@Slf4j
public class FileStopController implements CrawlerStopController {

    private static final String STOP_FILE_NAME = ".stop";
    /**
     * Maximum age for a stop file to be considered valid (5 minutes).
     * Stop files older than this are considered stale and will be
     * ignored and deleted.
     */
    private static final long MAX_STOP_FILE_AGE_MS =
            TimeUnit.MINUTES.toMillis(5);

    private final Path stopFilePath;
    private final Consumer<Void> stopAction;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final long startTime;

    public FileStopController(
            Path workDir, Consumer<Void> stopAction) {
        stopFilePath = workDir.resolve(STOP_FILE_NAME);
        this.stopAction = stopAction;
        startTime = System.currentTimeMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "file-stop-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /** Call during startup to start monitoring for stop file */
    @Override
    public void start() {
        // 1. Immediate startup check
        if (Files.exists(stopFilePath)) {
            if (isStopFileValid()) {
                triggerStop("Startup check");
                return;
            }
            LOG.info("Ignoring stale stop file: {}", stopFilePath);
            cleanupStaleStopFile();
        }

        // 2. Poll for stop file every 3 seconds
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (Files.exists(stopFilePath) && isStopFileValid()) {
                    triggerStop("File polling check");
                }
            } catch (Exception e) {
                LOG.error("Stop file poller failed.", e);
            }
        }, 1, 3, TimeUnit.SECONDS);
    }

    /** Call to clean up */
    @Override
    public void stop() {
        scheduler.shutdownNow();
        // Clean up stop file if it exists
        try {
            Files.deleteIfExists(stopFilePath);
        } catch (IOException e) {
            LOG.warn("Failed to delete stop file: {}",
                    stopFilePath, e);
        }
    }

    private void triggerStop(String source) {
        if (stopping.compareAndSet(false, true)) {
            LOG.info("STOP triggered via {} (file: {})",
                    source, stopFilePath);
            stopAction.accept(null);
        }
    }

    /**
     * Checks if the stop file is valid based on its timestamp.
     * A stop file is valid if:
     * 1. It contains a valid timestamp
     * 2. The timestamp is after the controller start time
     * 3. The file age is less than MAX_STOP_FILE_AGE_MS
     *
     * @return true if the stop file is valid and should trigger a stop
     */
    private boolean isStopFileValid() {
        try {
            var content = Files.readString(stopFilePath).trim();
            var fileTimestamp = Long.parseLong(content);

            // Check if file was created before this controller started
            if (fileTimestamp < startTime) {
                LOG.debug(
                        "Stop file timestamp ({}) is before controller "
                                + "start time ({})",
                        fileTimestamp, startTime);
                return false;
            }

            // Check if file is too old
            var age = System.currentTimeMillis() - fileTimestamp;
            if (age > MAX_STOP_FILE_AGE_MS) {
                LOG.warn(
                        "Stop file is too old (age: {} ms, max: {} ms)",
                        age, MAX_STOP_FILE_AGE_MS);
                return false;
            }

            return true;
        } catch (IOException e) {
            LOG.error("Failed to read stop file: {}", stopFilePath, e);
            return false;
        } catch (NumberFormatException e) {
            LOG.error("Stop file contains invalid timestamp: {}",
                    stopFilePath);
            return false;
        }
    }

    /**
     * Removes a stale stop file.
     */
    private void cleanupStaleStopFile() {
        try {
            Files.deleteIfExists(stopFilePath);
            LOG.info("Cleaned up stale stop file: {}", stopFilePath);
        } catch (IOException e) {
            LOG.warn("Failed to cleanup stale stop file: {}",
                    stopFilePath, e);
        }
    }

    /**
     * Utility: writes stop file to signal crawler shutdown.
     * @param workDir the crawler work directory
     */
    public static void writeStopFile(Path workDir) {
        var stopFile = workDir.resolve(STOP_FILE_NAME);
        try {
            Files.createDirectories(workDir);
            Files.writeString(
                    stopFile,
                    String.valueOf(System.currentTimeMillis()),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info("Stop file written: {}", stopFile);
        } catch (IOException e) {
            LOG.error("Failed to write stop file: {}", stopFile, e);
            throw new RuntimeException(
                    "Failed to write stop file: " + stopFile, e);
        }
    }
}
