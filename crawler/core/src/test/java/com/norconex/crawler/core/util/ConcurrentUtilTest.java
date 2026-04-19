/* Copyright 2025-2026 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ConcurrentUtilTest {

    // -----------------------------------------------------------------
    // runWithAutoShutdown
    // -----------------------------------------------------------------

    @Test
    void testRunWithAutoShutdown_executesTask() throws Exception {
        var executed = new AtomicBoolean(false);
        var executor = Executors.newSingleThreadExecutor();
        ConcurrentUtil
                .runWithAutoShutdown(() -> executed.set(true), executor)
                .get(5, TimeUnit.SECONDS);
        assertThat(executed.get()).isTrue();
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testSupplyWithAutoShutdown_returnsValue() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var result = ConcurrentUtil
                .supplyWithAutoShutdown(() -> "hello", executor)
                .get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("hello");
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testCallWithAutoShutdown_returnsValue() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var result = ConcurrentUtil
                .callWithAutoShutdown(() -> 42, executor)
                .get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(42);
        assertThat(executor.isShutdown()).isTrue();
    }

    // -----------------------------------------------------------------
    // shutdownAndAwait
    // -----------------------------------------------------------------

    @Test
    void testShutdownAndAwait_waitsForTermination() {
        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            // small work
        });
        ConcurrentUtil.shutdownAndAwait(executor, Duration.ofSeconds(5));
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testShutdownAndAwait_nullMaxWait() {
        var executor = Executors.newSingleThreadExecutor();
        // null should still shut down
        assertThatCode(() -> ConcurrentUtil.shutdownAndAwait(executor, null))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------
    // get(Future)
    // -----------------------------------------------------------------

    @Test
    void testGet_returnsFutureValue() {
        var future = CompletableFuture.completedFuture("value");
        assertThat(ConcurrentUtil.get(future)).isEqualTo("value");
    }

    @Test
    void testGet_nullFutureReturnsNull() {
        assertThat(ConcurrentUtil.<String>get(null)).isNull();
    }

    @Test
    void testGet_withTimeoutReturnsValue() {
        var future = CompletableFuture.completedFuture("timed");
        assertThat(ConcurrentUtil.get(future, 5, TimeUnit.SECONDS))
                .isEqualTo("timed");
    }

    @Test
    void testGet_nullFutureWithTimeoutReturnsNull() {
        assertThat(ConcurrentUtil.<String>get(null, 5, TimeUnit.SECONDS))
                .isNull();
    }

    @Test
    void testGetUnderAMinute() {
        var future = CompletableFuture.completedFuture("quick");
        assertThat(ConcurrentUtil.getUnderAMinute(future)).isEqualTo("quick");
    }

    @Test
    void testGetUnderSecs() {
        var future = CompletableFuture.completedFuture("secs");
        assertThat(ConcurrentUtil.getUnderSecs(future, 5)).isEqualTo("secs");
    }

    // -----------------------------------------------------------------
    // waitUntil
    // -----------------------------------------------------------------

    @Test
    void testWaitUntil_immediatelyTrue() {
        assertThatCode(() -> ConcurrentUtil.waitUntil(() -> true))
                .doesNotThrowAnyException();
    }

    @Test
    void testWaitUntil_withTimeout_success() {
        var flag = new AtomicBoolean(false);
        var future = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            flag.set(true);
        });
        var result = ConcurrentUtil.waitUntil(
                flag::get, Duration.ofSeconds(5));
        future.join(); // ensure async task fully completed
        assertThat(result).isTrue();
        assertThat(future).isCompleted();
    }

    @Test
    void testWaitUntil_withTimeout_timedOut() {
        var result = ConcurrentUtil.waitUntil(
                () -> false, Duration.ofMillis(200));
        assertThat(result).isFalse();
    }

    @Test
    void testWaitUntil_withTimeoutAndCheckInterval_timedOut() {
        var result = ConcurrentUtil.waitUntil(
                () -> false,
                Duration.ofMillis(300),
                Duration.ofMillis(50));
        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------
    // waitUntilOrThrow
    // -----------------------------------------------------------------

    @Test
    void testWaitUntilOrThrow_immediatelyTrue() {
        assertThatCode(() -> ConcurrentUtil.waitUntilOrThrow(
                () -> true, Duration.ofSeconds(5)))
                        .doesNotThrowAnyException();
    }

    @Test
    void testWaitUntilOrThrow_throwsWhenTimedOut() {
        assertThatThrownBy(() -> ConcurrentUtil.waitUntilOrThrow(
                () -> false, Duration.ofMillis(200)))
                        .isInstanceOf(TimeoutException.class);
    }

    @Test
    void testWaitUntilOrThrow_withCheckInterval() {
        assertThatThrownBy(() -> ConcurrentUtil.waitUntilOrThrow(
                () -> false,
                Duration.ofMillis(300),
                Duration.ofMillis(50)))
                        .isInstanceOf(TimeoutException.class);
    }

    // -----------------------------------------------------------------
    // cleanShutdown
    // -----------------------------------------------------------------

    @Test
    void testCleanShutdown_nullExecutor() {
        assertThatCode(() -> ConcurrentUtil.cleanShutdown(null))
                .doesNotThrowAnyException();
    }

    @Test
    void testCleanShutdown_executorShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        ConcurrentUtil.cleanShutdown(executor);
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testCleanShutdown_alreadyShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        assertThatCode(() -> ConcurrentUtil.cleanShutdown(executor))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------
    // allOf
    // -----------------------------------------------------------------

    @Test
    void testAllOf_combinesResults() throws Exception {
        var f1 = CompletableFuture.completedFuture(1);
        var f2 = CompletableFuture.completedFuture(2);
        var f3 = CompletableFuture.completedFuture(3);
        var combined = ConcurrentUtil.allOf(List.of(f1, f2, f3));
        var results = combined.get(5, TimeUnit.SECONDS);
        assertThat(results).containsExactly(1, 2, 3);
    }

    // -----------------------------------------------------------------
    // wrapAsCompletionException
    // -----------------------------------------------------------------

    @Test
    void testWrapAsCompletionException_alreadyWrapped() {
        var ce = new CompletionException("orig", new RuntimeException());
        assertThat(ConcurrentUtil.wrapAsCompletionException(ce)).isSameAs(ce);
    }

    @Test
    void testWrapAsCompletionException_fromInterrupted() {
        var ie = new InterruptedException("interrupted");
        var result = ConcurrentUtil.wrapAsCompletionException(ie);
        assertThat(result).isInstanceOf(CompletionException.class);
        // Thread's interrupted status should be set
        assertThat(Thread.interrupted()).isTrue(); // clears interrupted status
    }

    @Test
    void testWrapAsCompletionException_fromTimeout() {
        var te = new TimeoutException("timeout");
        var result = ConcurrentUtil.wrapAsCompletionException(te);
        assertThat(result).isInstanceOf(CompletionException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void testWrapAsCompletionException_fromExecutionException() {
        var cause = new RuntimeException("root cause");
        var ee = new java.util.concurrent.ExecutionException(
                "execution", cause);
        var result = ConcurrentUtil.wrapAsCompletionException(ee);
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void testWrapAsCompletionException_fromGenericException() {
        var ex = new Exception("generic");
        var result = ConcurrentUtil.wrapAsCompletionException(ex);
        assertThat(result).isInstanceOf(CompletionException.class);
    }

    // -----------------------------------------------------------------
    // isInterruption
    // -----------------------------------------------------------------

    @Test
    void testIsInterruption_withInterruptedException() {
        var ex = new CompletionException(new InterruptedException());
        assertThat(ConcurrentUtil.isInterruption(ex)).isTrue();
    }

    @Test
    void testIsInterruption_withNormalException() {
        var ex = new RuntimeException("normal");
        assertThat(ConcurrentUtil.isInterruption(ex)).isFalse();
    }

    // -----------------------------------------------------------------
    // withAutoShutdown (CompletableFuture variant)
    // -----------------------------------------------------------------

    @Test
    void testWithAutoShutdown_shutsDownOnCompletion() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var future = CompletableFuture.completedFuture("done");
        ConcurrentUtil.withAutoShutdown(future, executor)
                .get(5, TimeUnit.SECONDS);
        // Give executor time to shut down
        executor.awaitTermination(1, TimeUnit.SECONDS);
        assertThat(executor.isShutdown()).isTrue();
    }
}
