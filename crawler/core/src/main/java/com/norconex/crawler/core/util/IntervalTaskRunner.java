/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.util;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches a new threads that executes the supplied {@link Runnable} at
 * specified interval until explicitly stopped.
 */
@RequiredArgsConstructor
@Slf4j
//TODO used at all?
//MAYBE: consider moving to Nx Commons
public class IntervalTaskRunner {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private CountDownLatch latch;
    private Future<?> future;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    @NonNull
    private final Duration interval;

    public void start(Runnable task) {
        stopRequested.set(false);
        latch = new CountDownLatch(1);
        future = scheduler.scheduleAtFixedRate(() -> {
            if (stopRequested.get()) {
                return;
            }
            task.run();
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        stopRequested.set(true);
        if (future != null) {
            future.cancel(false);  // Cancel the scheduled task
        }
        // We don't shut down the scheduler here; it can be  reused for future
        // tasks
        if (latch != null) {
            latch.countDown(); // Ensure latch is counted down on stop
        }
    }

    public void waitUntilStopped() {
        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for scheduler to stop", e);
            }
        }
    }

    public void shutdown() {
        stopRequested.set(true);
        if (future != null) {
            future.cancel(false);  // Cancel the scheduled task
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                // If termination didn't complete within the timeout, force shutdown
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOG.error("Scheduler did not terminate");
                }
            }
        } catch (InterruptedException e) {
            // If interrupted while waiting for termination, force shutdown
            scheduler.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupt status
            LOG.error("Interrupted while waiting for scheduler to shutdown", e);
        } finally {
            // in case latch was not called before shutdown
            if (latch != null && latch.getCount() > 0) {
                latch.countDown(); // Ensure latch is counted down on shutdown
            }
        }
    }
}
