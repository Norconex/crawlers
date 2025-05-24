/* Copyright 2024-2025 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class ConcurrentUtilTest {

    @SuppressWarnings("unchecked")
    @Test
    void testGet() throws InterruptedException, ExecutionException {
        var future = mock(Future.class);
        when(future.get()).thenReturn("future");

        assertThat(ConcurrentUtil.<String>get(null)).isNull();
        assertThat(ConcurrentUtil.get(future)).isEqualTo("future");

        when(future.get()).thenThrow(InterruptedException.class);
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> ConcurrentUtil.get(future))
                .withMessage("Thread was interrupted.");

    }

    @Test
    void testRunWithAutoShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        assertThatNoException().isThrownBy(() -> {
            ConcurrentUtil.getUnderAMinute(
                    ConcurrentUtil.runWithAutoShutdown(() -> {
                        //NOOP
                    }, executor));
        });
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testSupplyWithAutoShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        assertThatNoException().isThrownBy(() -> {
            ConcurrentUtil.getUnderSecs(
                    ConcurrentUtil.supplyWithAutoShutdown(
                            () -> null, executor),
                    1);
        });
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testCallWithAutoShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        assertThatNoException().isThrownBy(() -> {
            ConcurrentUtil.get(ConcurrentUtil.callWithAutoShutdown(
                    () -> null, executor), 1, TimeUnit.SECONDS);
        });
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testAutoShutdownWithException() {
        var executor = Executors.newSingleThreadExecutor();
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> { // NOSONAR
                    ConcurrentUtil.callWithAutoShutdown(() -> {
                        throw new RuntimeException("Simulating exception.");
                    }, executor).get(1, TimeUnit.SECONDS);
                });
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testWaitUntil() {
        assertThatNoException().isThrownBy(() -> {
            ConcurrentUtil.waitUntil(() -> true);
        });
    }

    @Test
    void testCleanShutdown() {
        var executor = Executors.newSingleThreadExecutor();
        assertThatNoException().isThrownBy(() -> {
            ConcurrentUtil.cleanShutdown(null);
            ConcurrentUtil.cleanShutdown(executor);
        });
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void testWrapAsCompletionException() {
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new CompletionException("failed", null)))
                        .isInstanceOf(CompletionException.class)
                        .hasMessageContaining("failed");
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new InterruptedException("interrupted")))
                        .isInstanceOf(CompletionException.class)
                        .hasMessageContaining("Thread was interrupted.");
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new TimeoutException("timedOut")))
                        .isInstanceOf(CompletionException.class)
                        .hasMessageContaining("Task timed out.");
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new ExecutionException("ExecExcept", null)))
                        .isInstanceOf(CompletionException.class)
                        .hasMessageContaining("Task failed.");
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new ExecutionException("ExecExcept",
                        new IllegalStateException("Cause", null))))
                                .isInstanceOf(CompletionException.class)
                                .hasMessageContaining("Task execution failed.");
        assertThat(ConcurrentUtil.wrapAsCompletionException(
                new IllegalStateException("Other", null)))
                        .isInstanceOf(CompletionException.class)
                        .hasMessageContaining("Task failed.");
    }
}
