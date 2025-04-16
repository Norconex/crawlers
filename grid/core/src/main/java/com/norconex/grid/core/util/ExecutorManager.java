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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to run tasks using different execution strategies on a
 * single JVM. Each methods creating a thread pool ensures the pool is tied
 * to the executor name supplied when creating this manager.
 */
@Slf4j
public class ExecutorManager {

    private final ExecutorService shortTaskExecutor;
    private final Map<String, ExecutorService> longTaskExecutors =
            new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> scheduledTaskExecutors =
            new ConcurrentHashMap<>();
    private final AtomicInteger threadCount = new AtomicInteger();
    private final ScopedThreadFactoryCreator tfc;
    private final String baseName;

    public ExecutorManager(String baseName) {
        this.baseName = baseName;
        tfc = new ScopedThreadFactoryCreator(baseName);
        // Short-lived tasks will share the same cached thread pool per node
        shortTaskExecutor = Executors.newCachedThreadPool(
                tfc.create("short-task-" + threadCount.incrementAndGet()));
    }

    public ThreadFactory threadFactory(String threadName) {
        return tfc.create(threadName);
    }

    //--- Shutdowns ------------------------------------------------------------

    /**
     * Gracefully shuts down all executors for this node.
     */
    public void shutdown() {
        shortTaskExecutor.shutdown();
        longTaskExecutors.values().forEach(ExecutorService::shutdown);
        scheduledTaskExecutors.values().forEach(ExecutorService::shutdown);
    }

    /**
     * Forcefully shuts down all executors for this node.
     */
    public void shutdownNow() {
        shortTaskExecutor.shutdownNow();
        longTaskExecutors.values().forEach(ExecutorService::shutdownNow);
        scheduledTaskExecutors.values().forEach(ExecutorService::shutdownNow);
    }

    //--- Executors ------------------------------------------------------------

    /**
     * <p>
     * Gets the short-lived task executor. Meant for quick threads that
     * are expected to last a few milliseconds up to a few seconds at most.
     * </p>
     * <p>
     * Make sure to shut down the executor when no longer using it.
     * (e.g., when your application stops.)
     * </p>
     * @return short-lived executor service
     */
    public ExecutorService getShortTaskExecutor() {
        return shortTaskExecutor;
    }

    /**
     * <p>
     * Gets or creates a dedicated single-thread pool for a long-lived task.
     * The task name should be unique per long task identity.
     * </p>
     * <p>
     * Make sure to shut down the executor when no longer using it.
     * </p>
     * <p>
     * If you do not plan to re-use the fixed-thread pool and don't care
     * to track completion, consider using
     * <code>Thread.start()</code> directly instead (which does not need
     * explicit shutdown).
     * </p>
     * @param taskName task name
     * @return long-lived executor service
     */
    public ExecutorService getOrCreateLongTaskExecutor(
            @NonNull String taskName) {
        var key = baseName + "-long-task-" + taskName;
        return longTaskExecutors.computeIfAbsent(key,
                k -> Executors.newFixedThreadPool(
                        1, tfc.create("long-task-" + taskName)));
    }

    /**
     * <p>
     * Gets or creates a dedicated single-thread pool for a scheduled task.
     * Each taskKey should be unique per long task identity.
     * </p>
     * <p>
     * Make sure to shut down the executor when no longer using it.
     * </p>
     * @param taskName task name
     * @return scheduled executor service
     */
    public ScheduledExecutorService getOrCreateScheduledTaskExecutor(
            @NonNull String taskName) {
        var key = baseName + "-scheduled-task-" + taskName;
        return scheduledTaskExecutors.computeIfAbsent(key,
                k -> Executors.newScheduledThreadPool(
                        1, tfc.create("scheduled-task-" + taskName)));
    }

    //--- Short Task -----------------------------------------------------------

    /**
     * Submit a short-lived task to the shared cached thread pool for this node.
     * @param taskName short task name
     * @param task task to run
     * @return future
     * @see #getShortTaskExecutor()
     */
    public <T> Future<T> callShortTask(
            @NonNull String taskName, @NonNull Callable<T> task) {
        return shortTaskExecutor.submit(ThreadRenamer.suffix(taskName, task));
    }

    /**
     * Submit a short-lived task to the shared cached thread pool for this node.
     * @param taskName short task name
     * @param task task to run
     * @return future
     * @see #getShortTaskExecutor()
     */
    public <T> Future<T> runShortTask(
            @NonNull String taskName, @NonNull Supplier<T> task) {
        return callShortTask(taskName, task::get);
    }

    /**
     * Submit a short-lived task to the shared cached thread pool for this node.
     * @param taskName short task name
     * @param task task to run
     * @return future
     * @see #getShortTaskExecutor()
     */
    public Future<Void> runShortTask(
            @NonNull String taskName, @NonNull Runnable task) {
        return callShortTask(taskName, () -> {
            task.run();
            return null;
        });
    }

    //--- Long Task ------------------------------------------------------------

    /**
     * Executes a task in a fixed thread pool of one.
     * @param taskName the task name
     * @param task the task to execute
     * @return completable future
     * @see #getOrCreateLongTaskExecutor(String)
     */
    public <T> CompletableFuture<T> callLongTask(
            @NonNull String taskName, @NonNull Callable<T> task) {
        var executor = longTaskExecutors.computeIfAbsent(taskName,
                key -> Executors.newFixedThreadPool(1, tfc.create(
                        "long-task-" + taskName)));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Create or reuse a long-running task executor (fixed-size pool with 1
     * thread).
     * @param taskName long task name
     * @param task task to run
     * @return completable future
     * @see #getOrCreateLongTaskExecutor(String)
     */
    public CompletableFuture<Void> runLongTask(
            @NonNull String taskName, @NonNull Runnable task) {
        return callLongTask(taskName, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Create or reuse a long-running task executor (fixed-size pool with 1
     * thread).
     * @param taskName long task name
     * @param task task to run
     * @return completable future
     * @see #getOrCreateLongTaskExecutor(String)
     */
    public <T> CompletableFuture<T> supplyLongTask(
            @NonNull String taskName, @NonNull Supplier<T> task) {
        return callLongTask(taskName, task::get);
    }

    //--- One-Off Task ---------------------------------------------------------

    /**
     * Launch the task in a new <code>Thread</code> and forgets about it
     * (releasing the thread when done).
     * If you want to track/control the execution, use another method.
     * @param taskName the task name
     * @param task the task to run
     */
    public void runOneOffLongTask(
            @NonNull String taskName, @NonNull Runnable task) {
        tfc.create(taskName).newThread(task).start();
    }

    //--- Long Task w/ Auto-Shutdow --------------------------------------------

    /**
     * Executes a task in a fixed thread pool of one and automatically
     * shut down the pool executor when done.
     * If you don't need execution tracking, you can also use the one-off
     * method instead.
     * @param taskName the task name
     * @param task the task to execute
     * @return completable future
     */
    public <T> CompletableFuture<T> callLongTaskWithAutoShutdown(
            @NonNull String taskName, @NonNull Callable<T> task) {
        var executor = Executors.newFixedThreadPool(1, tfc.create(taskName));
        return ConcurrentUtil.callWithAutoShutdown(task, executor);
    }

    /**
     * Executes a task in a fixed thread pool of one and automatically
     * shut down the pool executor when done.
     * If you don't need execution tracking, you can also use the one-off
     * method instead.
     * @param taskName the task name
     * @param task the task to run
     * @return completable future
     */
    public CompletableFuture<Void> runLongTaskWithAutoShutdown(
            @NonNull String taskName, @NonNull Runnable task) {
        return callLongTaskWithAutoShutdown(taskName, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Executes a task in a fixed thread pool of one and automatically
     * shut down the pool executor when done.
     * If you don't need execution tracking, you can also use the one-off
     * method instead.
     * @param taskName the task name
     * @param task the task to run
     * @return completable future
     */
    public <T> CompletableFuture<T> supplyLongTaskWithAutoShutdown(
            @NonNull String taskName, @NonNull Supplier<T> task) {
        return callLongTaskWithAutoShutdown(taskName, task::get);
    }

    //--- Long Tasks (many) w/ Auto-Shutdow ------------------------------------

    /**
     * Launch asynchronously a fixed number of tasks obtained dynamically
     * using a fresh thread pool.
     * For each thread the task producer argument is invoked to get a new task
     * instance.
     * The returned {@link CompletableFuture} completes when all tasks have
     * ended.
     * @param taskName task name prefix
     * @param taskProducer a function receiving a task index, returning
     *      a runnable task
     * @param numTasks the number of tasks to run concurrently
     * @return a completable future
     */
    public CompletableFuture<Void> runLongTasksWithAutoShutdown(
            @NonNull String taskName,
            @NonNull IntFunction<Runnable> taskProducer,
            int numTasks) {
        var count = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(numTasks,
                tfc.create("-" + taskName + "-" + count.getAndIncrement()));
        var futures = IntStream.range(0, numTasks)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        taskProducer.apply(i).run();
                    } catch (Exception e) {
                        LOG.error("Problem running task {} {} of {}.",
                                baseName + "-" + taskName,
                                i, numTasks, e);
                        throw new CompletionException(e);
                    }
                }, executor))
                .toList();
        return ConcurrentUtil.withAutoShutdown(
                CompletableFuture
                        .allOf(futures.toArray(new CompletableFuture[0])),
                executor);
    }

    //TO CONSIDER IF NEEDED:
    //    runSingleTaskWithRetry: Tasks that should retry on failure.
    //
    //    runPeriodicTask: Periodic tasks, like recurring checks.
    //
    //    runIsolatedTaskWithTimeout: Isolated long-running tasks with a timeout.
    //
    //    runTaskInSequence: Sequential task execution.
    //
    //    runShortTaskWithTimeout: Short tasks with a timeout.
    //
    //    runTaskWithFixedDelay: Tasks that run periodically with a delay.
    //
    //    runAsyncTaskWithCompletion: Asynchronous tasks with a CompletableFuture.
    //
    //    runTaskWithRetryAndTimeout: Tasks that retry with a timeout.
    //
    //    runTaskInParallel: Parallel task execution.
    //
    //    runTaskOnDedicatedThread: Tasks on a dedicated thread.
}
