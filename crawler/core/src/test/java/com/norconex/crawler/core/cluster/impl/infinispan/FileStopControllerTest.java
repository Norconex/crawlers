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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;

class FileStopControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void testStopFileDetectedOnStartup() {
        // Given: Controller is created first
        var stopTriggered = new AtomicBoolean(false);
        var controller = new FileStopController(tempDir, ignored -> {
            stopTriggered.set(true);
        });

        // When: A stop file is written (simulating external stop command)
        Sleeper.sleepMillis(10); // Ensure timestamp is after controller start
        FileStopController.writeStopFile(tempDir);
        var stopFile = tempDir.resolve(".stop");
        assertThat(stopFile).exists();

        // And: Controller starts
        controller.start();

        // Then: Stop should be triggered immediately
        waitUntil(stopTriggered, 2000);
        assertThat(stopTriggered.get()).isTrue();

        controller.stop();
    }

    @Test
    void testStopFileDetectedDuringPolling() {
        // Given: Controller is started without stop file
        var stopTriggered = new AtomicBoolean(false);
        var controller = new FileStopController(tempDir, ignored -> {
            stopTriggered.set(true);
        });
        controller.start();

        // When: Stop file is created after startup
        Sleeper.sleepMillis(500);
        FileStopController.writeStopFile(tempDir);
        assertThat(tempDir.resolve(".stop")).exists();

        // Then: Stop should be triggered by polling
        waitUntil(stopTriggered, 5000);
        assertThat(stopTriggered.get()).isTrue();

        controller.stop();
    }

    @Test
    void testStopFileCleanedUpOnStop() {
        // Given: Stop file exists
        FileStopController.writeStopFile(tempDir);
        var stopFile = tempDir.resolve(".stop");
        assertThat(stopFile).exists();

        // When: Controller stops
        var controller = new FileStopController(tempDir, ignored -> {});
        controller.start();
        controller.stop();

        // Then: Stop file should be cleaned up
        assertThat(stopFile).doesNotExist();
    }

    @Test
    void testWriteStopFileCreatesDirectories() throws Exception {
        // Given: Non-existent work directory
        var newWorkDir = tempDir.resolve("new/work/dir");
        assertThat(newWorkDir).doesNotExist();

        // When: Writing stop file
        FileStopController.writeStopFile(newWorkDir);

        // Then: Directories and stop file should be created
        assertThat(newWorkDir).exists();
        assertThat(newWorkDir.resolve(".stop")).exists();

        // Cleanup
        Files.deleteIfExists(newWorkDir.resolve(".stop"));
    }

    @Test
    void testStopOnlyTriggeredOnce() {
        // Given: Controller is created first
        var stopCount = new AtomicInteger(0);
        var controller = new FileStopController(tempDir, ignored -> {
            stopCount.incrementAndGet();
        });

        // When: Stop file is written after controller creation
        Sleeper.sleepMillis(10); // Ensure timestamp is after controller start
        FileStopController.writeStopFile(tempDir);
        controller.start();

        // Then: Stop action should only be triggered once
        waitUntil(() -> stopCount.get() >= 1, 2000);
        assertThat(stopCount.get()).isEqualTo(1);

        // Wait a bit more to ensure it's not called again
        Sleeper.sleepMillis(1000);
        assertThat(stopCount.get()).isEqualTo(1);

        controller.stop();
    }

    @Test
    void testStaleStopFileIsIgnored() throws Exception {
        // Given: A stop file with old timestamp (10 minutes ago)
        var stopFile = tempDir.resolve(".stop");
        var oldTimestamp = System.currentTimeMillis()
                - java.util.concurrent.TimeUnit.MINUTES.toMillis(10);
        Files.createDirectories(tempDir);
        Files.writeString(
                stopFile,
                String.valueOf(oldTimestamp),
                java.nio.file.StandardOpenOption.CREATE);
        assertThat(stopFile).exists();

        // When: Controller starts with stale stop file
        var stopTriggered = new AtomicBoolean(false);
        var controller = new FileStopController(tempDir, ignored -> {
            stopTriggered.set(true);
        });
        controller.start();

        // Then: Stop should NOT be triggered
        assertThat(stopTriggered.get()).isFalse();

        // And: Stale stop file should be cleaned up
        assertThat(stopFile).doesNotExist();

        controller.stop();
    }

    @Test
    void testStopFileCreatedBeforeControllerStartIsIgnored()
            throws Exception {
        // Given: A stop file created before controller initialization
        var stopFile = tempDir.resolve(".stop");
        Files.createDirectories(tempDir);
        var oldTimestamp = System.currentTimeMillis()
                - java.util.concurrent.TimeUnit.SECONDS.toMillis(30);
        Files.writeString(
                stopFile,
                String.valueOf(oldTimestamp),
                java.nio.file.StandardOpenOption.CREATE);
        assertThat(stopFile).exists();

        // When: Controller starts
        var stopTriggered = new AtomicBoolean(false);
        var controller = new FileStopController(tempDir, ignored -> {
            stopTriggered.set(true);
        });
        controller.start();

        // Then: Stop should NOT be triggered
        assertThat(stopTriggered.get()).isFalse();

        controller.stop();
    }

    @Test
    void testInvalidStopFileContentIsIgnored() throws Exception {
        // Given: A stop file with invalid content
        var stopFile = tempDir.resolve(".stop");
        Files.createDirectories(tempDir);
        Files.writeString(
                stopFile,
                "not-a-timestamp",
                java.nio.file.StandardOpenOption.CREATE);
        assertThat(stopFile).exists();

        // When: Controller starts
        var stopTriggered = new AtomicBoolean(false);
        var controller = new FileStopController(tempDir, ignored -> {
            stopTriggered.set(true);
        });
        controller.start();

        // Then: Stop should NOT be triggered
        assertThat(stopTriggered.get()).isFalse();

        controller.stop();
    }

    private void waitUntil(AtomicBoolean condition, long timeoutMs) {
        var startTime = System.currentTimeMillis();
        while (!condition.get()
                && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Sleeper.sleepMillis(100);
        }
    }

    private void waitUntil(
            java.util.function.BooleanSupplier condition,
            long timeoutMs) {
        var startTime = System.currentTimeMillis();
        while (!condition.getAsBoolean()
                && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Sleeper.sleepMillis(100);
        }
    }
}
