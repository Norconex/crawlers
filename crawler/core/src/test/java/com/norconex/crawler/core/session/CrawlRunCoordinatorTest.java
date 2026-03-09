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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.support.InMemoryCacheMap;

@Timeout(30)
class CrawlRunCoordinatorTest {

    private InMemoryCacheMap<String> sessionCache;
    private InMemoryCacheMap<String> runCache;
    private CrawlAttributes runAttrs;
    private CrawlRunCoordinator coordinator;

    @BeforeEach
    void setUp() {
        sessionCache = new InMemoryCacheMap<>("session");
        runCache = new InMemoryCacheMap<>("run");
        runAttrs = new CrawlAttributes(runCache);
        coordinator = new CrawlRunCoordinator(sessionCache, runCache, runAttrs);
    }

    @Test
    void oncePerSession_runsOnlyOnce() {
        var counter = new AtomicInteger(0);
        coordinator.oncePerSession("init", counter::incrementAndGet);
        coordinator.oncePerSession("init", counter::incrementAndGet);
        coordinator.oncePerSession("init", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void oncePerRun_runsOnlyOnce() {
        var counter = new AtomicInteger(0);
        coordinator.oncePerRun("setup", counter::incrementAndGet);
        coordinator.oncePerRun("setup", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void oncePerSessionAndGet_returnsStoredValue() {
        String result = coordinator.oncePerSessionAndGet("greet",
                () -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void oncePerSessionAndGet_cachedOnSecondCall() {
        var counter = new AtomicInteger(0);
        coordinator.oncePerSessionAndGet("counter", () -> {
            counter.incrementAndGet();
            return "done";
        });
        String second = coordinator.oncePerSessionAndGet("counter", () -> {
            counter.incrementAndGet();
            return "done";
        });
        assertThat(counter.get()).isEqualTo(1);
        assertThat(second).isEqualTo("done");
    }

    @Test
    void oncePerRunAndGet_returnsStoredValue() {
        Integer val = coordinator.oncePerRunAndGet("num", () -> 42);
        assertThat(val).isEqualTo(42);
    }

    @Test
    void oncePerRunAndGet_cachedOnSecondCall() {
        var counter = new AtomicInteger(0);
        coordinator.oncePerRunAndGet("calc", () -> {
            counter.incrementAndGet();
            return 99;
        });
        Integer second = coordinator.oncePerRunAndGet("calc", () -> {
            counter.incrementAndGet();
            return 99;
        });
        assertThat(counter.get()).isEqualTo(1);
        assertThat(second).isEqualTo(99);
    }

    @Test
    void oncePerSessionAndGet_nullValue_handledGracefully() {
        // Supplier returning null should not crash
        String val = coordinator.oncePerSessionAndGet("nullOp",
                () -> (String) null);
        // Since null is wrapped and unwrapped, we tolerate either null
        // or the expected type (null variant)
        assertThat(val).isNull();
    }

    @Test
    void oncePerSession_differentIds_runSeparately() {
        var counter = new AtomicInteger(0);
        coordinator.oncePerSession("taskA", counter::incrementAndGet);
        coordinator.oncePerSession("taskB", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void getCrawlRunAttributes_returnsSameInstance() {
        assertThat(coordinator.getCrawlRunAttributes()).isSameAs(runAttrs);
    }
}
