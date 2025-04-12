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
package com.norconex.grid.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import com.norconex.grid.core.GridException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class LocalGridStopHandler {

    public static final String STOPFILE_NAME = "stop-requested";

    private final Path stopFile;
    private final LocalGrid grid;
    private boolean stopListening;
    private Thread listenerThread;

    public LocalGridStopHandler(LocalGrid grid) {
        this.grid = grid;
        stopFile = grid.getStoragePath().resolve(STOPFILE_NAME);
    }

    public void listenForStopRequest() {
        // We assume if one is there to begin with, it is a remain from
        // the past, so we remove it.
        deleteStopFile();

        listenerThread = new Thread(() -> {
            LOG.debug("Stop-Request-Watcher started.");
            var shouldExit = false;
            while (!shouldExit && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    LOG.debug("Stop-Request-Watcher interrupted during sleep. "
                            + "Exiting.");
                    // restore interrupt status
                    Thread.currentThread().interrupt();
                    shouldExit = true;
                }

                if (stopListening || grid.isClosed()) {
                    shouldExit = true;
                } else if (Files.exists(stopFile)) {
                    LOG.info("Received request to stop.");
                    grid.stop();
                    deleteStopFile();
                    shouldExit = true;
                }
            }

            LOG.debug("Stop-Request-Watcher thread exiting.");
        }, "Stop-Request-Watcher");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stopListening() {
        stopListening = true;
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
        }
    }

    public static void requestStop(Path storageDir) {
        try {
            FileUtils.touch(storageDir.resolve(STOPFILE_NAME).toFile());
        } catch (IOException e) {
            throw new GridException("Could not issue stop request.", e);
        }
    }

    private void deleteStopFile() {
        try {
            Files.deleteIfExists(stopFile);
        } catch (IOException e) {
            LOG.error("Could not delete {}", stopFile);
        }
    }
}
