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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

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

    /**
     * Executes a task without a return value asynchronously with clean
     * exception handling and thread shutdown.
     * @param runnable the task to be run
     * @return a future with a <code>null</code> return value
     */
    public static Future<Void> run(Runnable runnable) {
        return call(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Executes a task with a return value asynchronously with clean
     * exception handling and thread shutdown.
     * @param callable the task to be called
     * @return a future with the task return value.
     */
    public static <T> Future<T> call(Callable<T> callable) {
        var executor = Executors.newFixedThreadPool(1);
        var future = new CompletableFuture<T>();
        executor.submit((Runnable) () -> {
            try {
                future.complete(callable.call());
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                executor.shutdown();
                // necessary to ensure thread end event is not sometimes fired
                // after crawler run end.
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        LOG.warn("Executor did not terminate in "
                                + "the specified time.");
                    }
                } catch (InterruptedException e) {
                    LOG.error("Failed to wait for thread termination.", e);
                    Thread.currentThread().interrupt();
                }
            }
        });
        return future;
    }

    /**
     * Launch a number of tasks obtained dynamically, asynchronously with clean
     * exception handling and thread shutdown. For each
     * thread the task producer argument is invoked to get a new task instance.
     * The returned {@link Future} completes when all tasks have ended.
     * @param taskProducer a function receiving a task index, returning
     *      a runnable task
     * @param numTasks the number of tasks to run concurrently
     * @return a future with a <code>null</code> return value
     */
    public static Future<Void> run(
            @NonNull IntFunction<Runnable> taskProducer, int numTasks) {
        new CountDownLatch(numTasks);
        var executor = Executors.newFixedThreadPool(numTasks);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (var i = 0; i < numTasks; i++) {
            var task = taskProducer.apply(i);
            var future = CompletableFuture.runAsync(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);
            futures.add(future);
        }
        var allDone = CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]));

        // Wrap in another future that shuts down the executor
        return allDone.whenComplete((result, throwable) -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
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
            return new CompletionException(
                    "Interrupted while waiting for result", ie);
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
