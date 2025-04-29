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
package com.norconex.grid.core.util;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * A scheduler-driven notifier that keeps sending node state updates until
 * a terminal state is reached and acknowledged by the coordinator.
 */
@Slf4j
public final class ReliableCompletionNotifier {

    private ReliableCompletionNotifier() {
    }

    /**
     * Starts the notifier with the given configuration.
     * Periodically sends updates and stops once an acknowledgment is
     * confirmed after a terminal state.
     *
     * @param config the notifier configuration
     */
    public static void start(NotifierConfig config) {
        start(config, null);
    }

    /**
     * <p>
     * Starts the notifier with the given configuration.
     * Periodically sends updates and stops once an acknowledgment is
     * confirmed after a terminal state.
     * </p>
     * <p>
     * The supplied executor (provided not <code>null</code>) will be
     * automatically shutdown.
     * </p>
     *
     * @param config the notifier configuration
     * @param executor scheduled executor service
     */
    public static void start(
            NotifierConfig config, ScheduledExecutorService executor) {
        var scheduler = ofNullable(executor)
                .orElseGet(Executors::newSingleThreadScheduledExecutor);

        final var futureRef = new ScheduledFuture<?>[1];
        final int[] attempts = { 0 };
        final var lastSync = new AtomicLong();

        futureRef[0] = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (config.shouldNotify.getAsBoolean()
                        || mustNotify(lastSync, config.maxNotifyInterval)) {
                    var acknowledged = config.notify.getAsBoolean();
                    lastSync.set(System.currentTimeMillis());

                    if (config.isDone.getAsBoolean()) {
                        if (acknowledged) {
                            config.onSuccess.run();
                            futureRef[0].cancel(false);
                            shutdownScheduler(scheduler);
                            return;
                        }

                        attempts[0]++;
                        if (config.maxAttempts != -1
                                && attempts[0] >= config.maxAttempts) {
                            config.onFail.run();
                            futureRef[0].cancel(false);
                            shutdownScheduler(scheduler);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Notifier task error.", e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private static boolean mustNotify(
            AtomicLong lastSync, Duration maxNotifyInterval) {
        return (System.currentTimeMillis()
                - lastSync.get()) >= maxNotifyInterval.toMillis();
    }

    /**
     * Shuts down the scheduler gracefully, allowing the scheduled task to
     * finish before termination.
     */
    private static void shutdownScheduler(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}
