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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.junit.WithLogLevel;

/**
 * Tests for {@link ScopedThreadFactoryCreator}.
 */
@Timeout(10)
class ScopedThreadFactoryCreatorTest {

    @Test
    void create_producesThreadWithScopedName() throws InterruptedException {
        var creator = new ScopedThreadFactoryCreator("testScope");
        var factory = creator.create("worker");
        var executed = new AtomicBoolean(false);

        var thread = factory.newThread(() -> executed.set(true));
        // Name format: "<scope>-<threadName>-<counter>"
        assertThat(thread.getName()).startsWith("testScope-worker-");

        thread.start();
        thread.join(3000);
        assertThat(executed.get()).isTrue();
    }

    @Test
    void create_multipleThreads_haveUniqueIncreasingNames() {
        var creator = new ScopedThreadFactoryCreator("multiScope");
        var factory = creator.create("task");

        var t1 = factory.newThread(() -> {});
        var t2 = factory.newThread(() -> {});

        // Names must differ (counters increment)
        assertThat(t1.getName()).isNotEqualTo(t2.getName());
        assertThat(t1.getName()).contains("multiScope").contains("task");
        assertThat(t2.getName()).contains("multiScope").contains("task");
    }

    @Test
    void create_threadActuallyRunsRunnable() throws InterruptedException {
        var capturedThreadName = new AtomicReference<String>();
        var creator = new ScopedThreadFactoryCreator("captureScope");
        var factory = creator.create("captureThread");

        var thread = factory.newThread(
                () -> capturedThreadName.set(Thread.currentThread().getName()));
        thread.start();
        thread.join(3000);

        assertThat(capturedThreadName.get()).startsWith("captureScope");
    }

    @Test
    void create_differentScopeNames_bothWork() throws InterruptedException {
        var creatorA = new ScopedThreadFactoryCreator("scopeA");
        var creatorB = new ScopedThreadFactoryCreator("scopeB");

        var factoryA = creatorA.create("job");
        var factoryB = creatorB.create("job");

        var tA = factoryA.newThread(() -> {});
        var tB = factoryB.newThread(() -> {});

        assertThat(tA.getName()).startsWith("scopeA");
        assertThat(tB.getName()).startsWith("scopeB");
        assertThat(tA.getName()).isNotEqualTo(tB.getName());
    }

    /**
     * With TRACE logging enabled, the anonymous ThreadFactory inner class
     * runs the debug() method before and after the runnable, exercising
     * the ScopedThreadFactoryCreator$1.debug() method body.
     */
    @Test
    @WithLogLevel(value = "TRACE", classes = ScopedThreadFactoryCreator.class)
    void create_withTraceLogging_coversDebugCodePath()
            throws InterruptedException {
        var creator = new ScopedThreadFactoryCreator("trace-scope");
        var factory = creator.create("trace-thread");
        var latch = new CountDownLatch(1);

        var thread = factory.newThread(latch::countDown);
        thread.start();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        thread.join(3000);
    }
}
