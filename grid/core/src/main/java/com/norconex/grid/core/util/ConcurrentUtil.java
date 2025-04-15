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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConcurrentUtil {
    private ConcurrentUtil() {
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

    public static CompletableFuture<Void> runOneFixedThread(
            String threadName, Runnable runnable) {
        return CompletableFuture.runAsync(
                withThreadName(threadName, runnable),
                Executors.newFixedThreadPool(1));
    }

    public static <T> CompletableFuture<T> supplyOneFixedThread(
            String threadName, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(
                withThreadName(threadName, supplier),
                Executors.newFixedThreadPool(1));
    }

    public static Runnable withThreadName(
            @NonNull String name, @NonNull Runnable task) {
        return () -> {
            try {
                withThreadName(name, Executors.callable(task)).call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    public static <T> Supplier<T> withThreadName(
            @NonNull String name, @NonNull Supplier<T> supplier) {
        Callable<T> callable = supplier::get;
        Callable<T> namedCallable = withThreadName(name, callable);
        return () -> {
            try {
                return namedCallable.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    public static <T> Callable<T> withThreadName(
            @NonNull String name, @NonNull Callable<T> task) {
        return () -> {
            var current = Thread.currentThread();
            var originalName = current.getName();
            current.setName(name);
            try {
                return task.call();
            } finally {
                current.setName(originalName);
            }
        };
    }

    /**
     * Launch asynchronously a fixed number of tasks obtained dynamically.
     * For each thread the task producer argument is invoked to get a new task
     * instance.
     * The returned {@link Future} completes when all tasks have ended.
     * @param taskProducer a function receiving a task index, returning
     *      a runnable task
     * @param numTasks the number of tasks to run concurrently
     * @return a future with a <code>null</code> return value
     */
    public static Future<Void> run(
            @NonNull IntFunction<Runnable> taskProducer, int numTasks) {
        var executor = Executors.newFixedThreadPool(numTasks);
        var futures = IntStream.range(0, numTasks)
                .mapToObj(i -> CompletableFuture.runAsync(
                        withThreadName("run-pool", () -> {
                            try {
                                taskProducer.apply(i).run();
                            } catch (Exception e) {
                                LOG.error("Problem running task {} of {}.",
                                        i, numTasks, e);
                                throw new CompletionException(e);
                            }
                        }), executor))
                .toList();

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]));
    }

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
