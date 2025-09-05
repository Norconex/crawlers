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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;

import com.norconex.crawler.core.cluster.Cache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StopController {

    private final org.infinispan.Cache<String, String> controlCache;
    //TODO replace void by String to log or store a reason for stopping
    private final Consumer<Void> stopAction;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private static final String STOP_KEY = "STOP";

    private ControlListener controlListener;

    public StopController(
            Cache<String> controlCache, Consumer<Void> stopAction) {
        this.controlCache =
                ((InfinispanCacheAdapter<String>) controlCache).vendor();
        this.stopAction = stopAction;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "stop-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /** Call during startup */
    public void start() {
        // 1. Immediate startup check
        if (controlCache.containsKey(STOP_KEY)) {
            triggerStop("Startup check");
            return;
        }

        // 2. Register listener for fast shutdown
        controlListener = new ControlListener();
        controlCache.addListener(controlListener);

        // 3. Polling safety net (every 3 minutes, tune as needed)
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (controlCache.containsKey(STOP_KEY)) {
                    triggerStop("Polling check");
                }
            } catch (Exception e) {
                LOG.error("Stop poller failed.", e);
            }
        }, 1, 180, TimeUnit.SECONDS);
    }

    /** Call to clean up (optional if JVM exits anyway) */
    public void stop() {
        scheduler.shutdownNow();
        if (controlListener != null) {
            controlCache.removeListener(controlListener);
        }
    }

    private void triggerStop(String source) {
        if (stopping.compareAndSet(false, true)) {
            LOG.info("STOP triggered via {}", source);
            stopAction.accept(null);
        }
    }

    @Listener
    public class ControlListener {
        @CacheEntryCreated
        public void onControl(CacheEntryCreatedEvent<String, String> event) {
            if (!event.isPre() && STOP_KEY.equals(event.getKey())) {
                triggerStop("Cache event");
            }
        }
    }

    /**
     * Utility: sends STOP into the cache.
     * @param controlCache the cache to send the signal to
     */
    public static void sendStop(Cache<String> controlCache) {
        controlCache.putIfAbsent(STOP_KEY, "1");
    }
}
