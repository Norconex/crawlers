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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebDriverManager {

    private final WebDriverFetcherConfig config;
    private final Map<Thread, DriverEntry> drivers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger cleanedCount = new AtomicInteger(0);

    public WebDriverManager(WebDriverFetcherConfig config) {
        this.config = config;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "WebDriverManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::cleanupDeadThreads,
                config.getCleanupInterval().toMillis(),
                config.getCleanupInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the Thread‑local WebDriver, creating (and registering) it on
     * first call.
     * @return web driver
     */
    public WebDriver getDriver() {
        var entry = getDriverEntry();
        return entry.touchAndGet(config);
    }

    private DriverEntry getDriverEntry() {
        var t = Thread.currentThread();
        return drivers.computeIfAbsent(
                t, thread -> createAndRegisterDriver());
    }

    /**
     * Explicitly quit and remove the current thread's driver
     * (optional — the driver manager will cleanup instances that were used
     * by dead threads).
     */
    public void quitDriver() {
        var t = Thread.currentThread();
        var entry = drivers.remove(t);
        if (entry != null) {
            entry.quit();
            var total = cleanedCount.incrementAndGet();
            LOG.info("Quit driver for thread [{}], total cleaned={}",
                    t.getName(), total);
        }
    }

    /**
     * Wrap any WebDriver action so that on failure we reset and retry once.
     * @param action action to perform with a web driver
     * @param <T> action's return value type
     * @return action's return value
     */
    public <T> T safeCall(Function<WebDriver, T> action) {
        var t = Thread.currentThread();
        var entry = getDriverEntry();
        if (entry == null) {
            throw new IllegalStateException(
                    "No WebDriver registered for thread " + t.getName());
        }
        try {
            return action.apply(entry.touchAndGet(config));
        } catch (WebDriverException e) {
            LOG.warn("WebDriverException on thread [{}]: {} — resetting driver "
                    + "and retrying.", t.getName(), e.getMessage());
            // FIX: Properly quit the old driver before creating new one
            entry.forceReset();
            try {
                return action.apply(entry.touchAndGet(config));
            } catch (WebDriverException retryException) {
                LOG.error("WebDriverException on retry for thread [{}]: {}",
                        t.getName(), retryException.getMessage());
                throw retryException;
            }
        }
    }

    /** Scheduled task: quit any drivers whose threads have died */
    private void cleanupDeadThreads() {
        var live = Thread.getAllStackTraces().keySet();
        for (Thread t : drivers.keySet()) {
            if (!live.contains(t)) {
                var entry = drivers.remove(t);
                if (entry != null) {
                    LOG.info("Quitting WebDriver for dead thread {}.",
                            t.getName());
                    entry.quit();
                    cleanedCount.incrementAndGet();
                }
            }
        }
    }

    // FIX: Add shutdown method to clean up all drivers
    public void shutdown() {
        LOG.info("Shutting down WebDriverManager...");
        scheduler.shutdown();

        // Clean up all remaining drivers
        for (var entry : drivers.values()) {
            entry.quit();
            cleanedCount.incrementAndGet();
        }
        drivers.clear();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info(
                "WebDriverManager shutdown complete. "
                        + "Total created: {}, total cleaned: {}",
                createdCount.get(), cleanedCount.get());
    }

    private DriverEntry createAndRegisterDriver() {
        return new DriverEntry(() -> {
            LOG.debug("Creating new WebDriver for thread {}",
                    Thread.currentThread().getName());
            var driver = WebDriverFactory.create(config);
            createdCount.incrementAndGet();
            LOG.info("Created WebDriver #{} for thread [{}]",
                    createdCount.get(), Thread.currentThread().getName());
            return driver;
        });
    }

    private static class DriverEntry {
        private WebDriver driver;
        private Instant createdAt;
        private final AtomicInteger navCount = new AtomicInteger(0);
        private final Supplier<WebDriver> driverSupplier;

        DriverEntry(Supplier<WebDriver> driverSupplier) {
            this.driverSupplier = driverSupplier;
            driver = driverSupplier.get();
            createdAt = Instant.now();
        }

        WebDriver touchAndGet(WebDriverFetcherConfig cfg) {
            var maxNav = cfg.getBrowserMaxNavigations();
            var navCnt = navCount.incrementAndGet();
            var maxAge = cfg.getBrowserMaxAge();
            if (maxNav > -1 && navCnt > maxNav ||
                    maxAge != null && Duration.between(
                            createdAt, Instant.now()).compareTo(maxAge) > 0) {
                reset();
            }
            return driver;
        }

        void reset() {
            safeQuit();
            driver = driverSupplier.get();
            createdAt = Instant.now();
            navCount.set(0);
        }

        // FIX: Add forceReset method that ensures old driver is quit
        void forceReset() {
            LOG.debug("Force resetting WebDriver for thread {}",
                    Thread.currentThread().getName());
            safeQuit();
            driver = driverSupplier.get();
            createdAt = Instant.now();
            navCount.set(0);
        }

        void quit() {
            safeQuit();
        }

        // FIX: Improved quit method with better error handling
        private void safeQuit() {
            if (driver != null) {
                try {
                    LOG.debug("Quitting WebDriver for thread {}",
                            Thread.currentThread().getName());
                    driver.quit();
                    LOG.debug("Successfully quit WebDriver for thread {}",
                            Thread.currentThread().getName());
                } catch (Exception e) {
                    LOG.error("Could not quit WebDriver for thread {}: {}",
                            Thread.currentThread().getName(), e.getMessage());
                    // Even if quit fails, we still need to null the reference
                    // to prevent memory leaks
                } finally {
                    driver = null;
                }
            }
        }
    }
}
