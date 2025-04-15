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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.h2.mvstore.MVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LocalGridStorageConcurrencyTest {

    private File tempFile;
    private MVStore mvStore;
    private LocalGridStorage storage;

    void setup() {
        try {
            tempFile = File.createTempFile("mvstore-test", ".mv");
            mvStore = new MVStore.Builder()
                    .fileName(tempFile.getAbsolutePath())
                    .open();
            storage = new LocalGridStorage(mvStore);
        } catch (Exception e) {
            fail("Failed to initialize test store", e);
        }
    }

    @AfterEach
    void cleanup() {
        if (mvStore != null && !mvStore.isClosed()) {
            mvStore.close();
        }
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testConcurrentAccessAndDestroy() throws Exception {
        setup();

        var executor = Executors.newFixedThreadPool(10);
        var taskCount = 50;

        var latch = new CountDownLatch(taskCount);
        var errors = new ConcurrentLinkedQueue<Throwable>();

        // Spawn many tasks doing getMap + put
        for (var i = 0; i < taskCount; i++) {
            var index = i;
            executor.submit(() -> {
                try {
                    var map = storage.getMap("test-map", String.class);
                    map.put("key-" + index, "value-" + index);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait a bit, then destroy the storage in parallel
        Thread.sleep(50);
        executor.submit(() -> {
            try {
                storage.destroy();
            } catch (Throwable t) {
                errors.add(t);
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(errors.isEmpty(), () -> "Errors occurred: " + errors);

        // Confirm the map is cleared post-destroy
        assertTrue(storage.getStoreNames().isEmpty(),
                "Store names should be empty after destroy");
    }
}
