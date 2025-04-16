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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConcurrentUtil {
    private ConcurrentUtil() {
    }

    public static CompletableFuture<Void> runWithAutoShutdown(
            @NonNull Runnable task, @NonNull ExecutorService executor) {
        return withAutoShutdown(
                CompletableFuture.runAsync(task, executor), executor);
    }

    public static <T> CompletableFuture<T> supplyWithAutoShutdown(
            @NonNull Supplier<T> task, @NonNull ExecutorService executor) {
        return withAutoShutdown(
                CompletableFuture.supplyAsync(task, executor), executor);
    }

    public static <T> CompletableFuture<T> callWithAutoShutdown(
            @NonNull Callable<T> task, @NonNull ExecutorService executor) {
        return withAutoShutdown(CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor), executor);
    }

    public static <T> CompletableFuture<T> withAutoShutdown(
            @NonNull CompletableFuture<T> future,
            @NonNull ExecutorService executor) {
        return future.handle((res, ex) -> {
            if (ex != null) {
                throw new CompletionException(ex);
            }
            return res;
        }).whenComplete((res, ex) -> {
            executor.shutdown();
            //            // running in separate thread not to block.
            //            CompletableFuture.runAsync(() -> {
            //                try {
            //                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            //                        executor.shutdownNow();
            //                    }
            //                } catch (InterruptedException e) {
            //                    Thread.currentThread().interrupt();
            //                    LOG.debug("Shutdown task interrupted while waiting for "
            //                            + "executor termination");
            //                }
            //            });
        });
    }

    /**
     * Calls {@link Future#get()} and wraps thread related exceptions
     * in a runtime CompletionException. See
     * {@link #wrapAsCompletionException(Exception)} for more details.
     * Passing a <code>null</code> future has no effect and returns
     * <code>null</code>.
     * @param future the future to get
     * @return the completed future value, if any, or <code>null</code>
     */
    public static <T> T get(Future<T> future) {
        if (future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (Exception e) {
            throw wrapAsCompletionException(e);
        }
    }

    /**
     * Calls {@link Future#get(long, TimeUnit)} and wraps thread related
     * exceptions in a runtime CompletionException. See
     * {@link #wrapAsCompletionException(Exception)} for more details.
     * Passing a <code>null</code> future has no effect and returns
     * <code>null</code>.
     * @param <T> future return value
     * @param future the future to get
     * @param timeout
     * @param unit
     * @return the completed future value, if any, or <code>null</code>
     */
    public static <T> T get(Future<T> future, long timeout, TimeUnit unit) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(timeout, unit);
        } catch (Exception e) {
            throw wrapAsCompletionException(e);
        }
    }

    public static <T> T getUnderAMinute(Future<T> future) {
        return get(future, 1, TimeUnit.MINUTES);
    }

    public static <T> T getUnderSecs(Future<T> future, int seconds) {
        return get(future, seconds, TimeUnit.SECONDS);
    }

    //    /**
    //     * A thread factory creating a simple thread with the supplied thread name.
    //     * @param name thread name
    //     * @return thread factory
    //     */
    //    public static ThreadFactory threadFactory(String name) {
    //        return r -> {
    //            var t = new Thread(r);
    //            t.setName(name);
    //            return t;
    //        };
    //    }

    /**
     * Wraps {@link InterruptedException}, {@link TimeoutException},
     * {@link ExecutionException} root cause, and any other checked exceptions
     * in a runtime {@link CompletionException}.
     * The {@link InterruptedException} is handled gracefully
     * @param e the original exception
     * @return the wrapping {@link CompletionException}
     */
    public static CompletionException wrapAsCompletionException(Exception e) {
        if (e instanceof CompletionException ce) {
            return ce;
        }
        if (e instanceof InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new CompletionException("Thread was interrupted.", ie);
        }
        if (e instanceof TimeoutException te) {
            return new CompletionException("Task timed out", te);
        }
        if (e instanceof ExecutionException && e.getCause() != null) {
            return new CompletionException(
                    "Task execution failed", e.getCause());
        }
        return new CompletionException("Task failed", e);
    }
}
