package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lock.EmbeddedClusteredLockManagerFactory;
import org.infinispan.lock.api.ClusteredLockManager;

import com.norconex.crawler.core2.cluster.ClusterReducer;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.cluster.TaskException;
import com.norconex.crawler.core2.cluster.TaskManager;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the distributed execution of tasks using Infinispan for
 * synchronization and state management.
 */
@Slf4j
public class InfinispanTaskManager implements TaskManager {

    private static final String TASK_STATUS_CACHE_NAME = "taskStatusCache";
    private static final String TASK_RESULT_CACHE_NAME = "taskResultCache";
    private static final String TASK_RUNNING_CACHE_NAME = "taskRunningCache";
    private static final long LOCK_ACQUISITION_TIMEOUT_MS = 5000;
    private static final long STATUS_POLLING_INTERVAL_MS = 200;

    final Cache<String, TaskState> taskStatusCache;
    final Cache<String, StringSerializedObject> taskResultCache;
    private final ClusteredLockManager lockManager;
    private final InfinispanCluster cluster;
    private final String nodeId;

    /**
     * Constructs a DistributedTaskManager.
     *
     * @param cluster The Infinispan cluster instance.
     */
    public InfinispanTaskManager(InfinispanCluster cluster) {
        this.cluster = cluster;
        nodeId = cluster.getLocalNode().getNodeName();
        var cacheManager = cluster.getCacheManager().vendor();

        // Detect if the cache manager is standalone (no transport)
        var isClustered = false;
        var globalConfig = cacheManager.getCacheManagerConfiguration();
        if (globalConfig.transport() != null
                && globalConfig.transport().transport() != null) {
            isClustered = true;
        }

        // Ensure the task status cache is configured and retrieved
        if (!cacheManager.cacheExists(TASK_STATUS_CACHE_NAME)) {
            var builder = new ConfigurationBuilder();
            if (isClustered) {
                builder.clustering().cacheMode(CacheMode.DIST_SYNC);
            } else {
                builder.clustering().cacheMode(CacheMode.LOCAL);
            }
            cacheManager.defineConfiguration(
                    TASK_STATUS_CACHE_NAME, builder.build());
            cacheManager.defineConfiguration(
                    TASK_RESULT_CACHE_NAME, builder.build());
            cacheManager.defineConfiguration(
                    TASK_RUNNING_CACHE_NAME, builder.build());
        }
        taskStatusCache = cacheManager.getCache(TASK_STATUS_CACHE_NAME);
        taskResultCache = cacheManager.getCache(TASK_RESULT_CACHE_NAME);
        // Get the clustered lock manager
        lockManager = isClustered
                ? EmbeddedClusteredLockManagerFactory.from(cacheManager)
                : null;
    }

    /**
     * Executes a given task exactly once across the cluster.
     *
     * If this node is the one to run the task:
     * - It acquires a distributed lock.
     * - It sets the task status to RUNNING.
     * - It executes the task.
     * - It sets the task status to COMPLETED (or FAILED).
     * - It releases the lock.
     *
     * If another node is running the task or has already completed it:
     * - If completed, this method returns immediately.
     * - If running, this method waits until the task is completed by the other
     *   node.
     *
     * @param taskName A unique name for the task.
     * @param task The Runnable task to execute.
     */
    @Override
    public <T> Optional<T> runOnOneOnceSync(
            String taskName, ClusterTask<T> task) {
        try {
            return runOnOneOnceAsync(taskName, task).get();
        } catch (Exception e) { //NOSONAR
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }

    @Override
    public <T> Optional<T> runOnOneSync(String taskName, ClusterTask<T> task)
            throws TaskException {
        try {
            return runOnOneAsync(taskName, task).get();
        } catch (Exception e) { //NOSONAR
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }

    @Override
    public <T> CompletableFuture<Optional<T>> runOnOneAsync(String taskName,
            ClusterTask<T> task) {
        LOG.info("[{}] Attempting to run repeatable task: {}", nodeId,
                taskName);

        // Create a completable future to hold the result
        var resultFuture = new CompletableFuture<Optional<T>>();

        if (lockManager == null) {
            // Local mode: just run the task directly
            executeLocalTask(taskName, task, resultFuture);
            return resultFuture;
        }

        // Execute the clustered task asynchronously
        executeClusteredRepeatableTaskAsync(taskName, task, resultFuture);
        return resultFuture;
    }

    /**
     * Executes a task locally (non-clustered mode).
     */
    private <T> void executeLocalTask(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {
        try {
            // For repeatable tasks, we don't need to check if it's already done
            var result = task.execute(
                    CrawlSession.get(cluster.getLocalNode()));
            // Store the result temporarily
            taskResultCache.put(taskName,
                    new StringSerializedObject(result));
            resultFuture.complete(ofNullable(result));
        } catch (Exception e) {
            resultFuture.completeExceptionally(e);
        }
    }

    /**
     * Executes a repeatable task in a clustered environment asynchronously.
     * Unlike the "once" variant, this doesn't check for or store persistent
     * completion status.
     */
    private <T> void executeClusteredRepeatableTaskAsync(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {

        // Create the lock if it doesn't exist
        ensureLockExists(taskName);

        // Get the clustered lock for this specific task
        var taskLock = lockManager.get(taskName);

        // Execute in a separate thread to avoid blocking
        CompletableFuture.runAsync(() -> {
            var lockAcquiredSuccessfully = false;
            try {
                lockAcquiredSuccessfully = acquireLock(taskName, taskLock);

                if (lockAcquiredSuccessfully) {
                    // Lock acquired, this node is the designated runner
                    executeRepeatableTask(taskName, task, resultFuture);
                } else {
                    LOG.info(
                            "[{}] Could not acquire lock for repeatable task '{}'. "
                                    + "Waiting for completion by another node.",
                            nodeId, taskName);
                    waitForRepeatableTaskCompletionAsync(taskName,
                            resultFuture);
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(
                        ConcurrentUtil.wrapAsCompletionException(e));
            } finally {
                if (lockAcquiredSuccessfully) {
                    cleanupRunningTask(taskName);
                    taskLock.unlock();
                    LOG.info("[{}] Released lock for task: {}", nodeId,
                            taskName);
                }
            }
        });
    }

    /**
     * Execute a repeatable task when this node is designated as the runner.
     */
    private <T> void executeRepeatableTask(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {
        // Mark the task as running in the temporary running cache
        taskResultCache.remove(taskName); // Clear any previous results
        LOG.info("[{}] Node is running task: {}", nodeId, taskName);
        try {
            // Execute the actual task
            var result = task.execute(
                    CrawlSession.get(cluster.getLocalNode()));
            // Store result in cache
            taskResultCache.put(taskName,
                    new StringSerializedObject(result));
            LOG.info("[{}] Task '{}' completed successfully.",
                    nodeId,
                    taskName);
            resultFuture.complete(ofNullable(result));
        } catch (Exception e) {
            LOG.error("[{}] Task '{}' failed: {}", nodeId, taskName,
                    e.getMessage());
            resultFuture.completeExceptionally(e);
        }
    }

    /**
     * Clean up after a repeatable task has finished.
     */
    private void cleanupRunningTask(String taskName) {
        // Nothing to clean up for repeatable tasks, as we don't track completion status
    }

    /**
     * Waits for a repeatable task completion asynchronously.
     */
    private <T> void waitForRepeatableTaskCompletionAsync(
            String taskName,
            CompletableFuture<Optional<T>> resultFuture) {

        CompletableFuture.runAsync(() -> {
            try {
                // Wait for the lock to become available
                waitForTaskLockAvailability(taskName);
                // Task has completed, get the result if available
                resultFuture.complete(fetchTaskResult(taskName));
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Waits until the lock for a task becomes available,
     * which indicates the task has completed.
     */
    private void waitForTaskLockAvailability(String taskName)
            throws InterruptedException {
        var taskLock = lockManager.get(taskName);
        while (true) {
            try {
                var acquiredLock = taskLock.tryLock(STATUS_POLLING_INTERVAL_MS,
                        TimeUnit.MILLISECONDS).get();
                if (acquiredLock) {
                    // Release it immediately, we just needed to know it's available
                    taskLock.unlock();
                    LOG.info(
                            "[{}] Task '{}' lock is now available. Task completed.",
                            nodeId, taskName);
                    return;
                }
            } catch (Exception e) {
                LOG.warn("[{}] Error while checking lock for task '{}': {}",
                        nodeId, taskName, e.getMessage());
            }
            Thread.sleep(STATUS_POLLING_INTERVAL_MS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> CompletableFuture<Optional<T>> runOnOneOnceAsync(
            String taskName, ClusterTask<T> task) {
        LOG.info("[{}] Attempting to run task: {}", nodeId, taskName);

        // Create a completable future to hold the result
        var resultFuture = new CompletableFuture<Optional<T>>();

        // 1. Check if the task is already completed
        var currentState = taskStatusCache.get(taskName);
        if (TaskState.COMPLETED.equals(currentState)) {
            LOG.info("[{}] Task '{}' already completed. Proceeding without "
                    + "waiting.", nodeId, taskName);
            resultFuture.complete(fetchTaskResult(taskName));
            return resultFuture;
        }

        if (lockManager == null) {
            // Local mode: just run the task directly
            try {
                taskStatusCache.put(taskName, TaskState.RUNNING);
                var result = task.execute(
                        CrawlSession.get(cluster.getLocalNode()));
                taskStatusCache.put(taskName, TaskState.COMPLETED);
                taskResultCache.put(taskName,
                        new StringSerializedObject(result));
                resultFuture.complete(ofNullable(result));
            } catch (Exception e) {
                taskStatusCache.put(taskName, TaskState.FAILED);
                resultFuture.completeExceptionally(e);
            }
            return resultFuture;
        }

        // Execute the clustered task asynchronously
        executeClusteredTaskAsync(taskName, task, resultFuture);
        return resultFuture;
    }

    /**
     * Executes the task in a clustered environment asynchronously.
     */
    private <T> void executeClusteredTaskAsync(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {

        // Create the lock if it doesn't exist
        ensureLockExists(taskName);

        // Get the clustered lock for this specific task
        var taskLock = lockManager.get(taskName);

        // Execute in a separate thread to avoid blocking
        CompletableFuture.runAsync(() -> {
            var lockAcquiredSuccessfully = false;
            try {
                lockAcquiredSuccessfully = acquireLock(taskName, taskLock);

                if (lockAcquiredSuccessfully) {
                    // Lock acquired, this node is the designated runner
                    processTaskWithLock(taskName, task, resultFuture, taskLock);
                } else {
                    LOG.info(
                            "[{}] Could not acquire lock for task '{}'. Waiting "
                                    + "for completion by another node.",
                            nodeId, taskName);
                    waitForTaskCompletionAsync(taskName, resultFuture);
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(
                        ConcurrentUtil.wrapAsCompletionException(e));
            } finally {
                if (lockAcquiredSuccessfully) {
                    taskLock.unlock();
                    LOG.info("[{}] Released lock for task: {}", nodeId,
                            taskName);
                }
            }
        });
    }

    /**
     * Ensures the lock for the given task exists.
     */
    private void ensureLockExists(String taskName) {
        if (!lockManager.isDefined(taskName)) {
            lockManager.defineLock(taskName);
            LOG.info("[{}] Created lock for task: {}", nodeId, taskName);
        }
    }

    /**
     * Tries to acquire a lock for the task.
     *
     * @return true if lock was acquired successfully
     */
    private boolean acquireLock(String taskName,
            org.infinispan.lock.api.ClusteredLock taskLock) throws Exception {
        var lockFuture = taskLock.tryLock(LOCK_ACQUISITION_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        boolean acquired = lockFuture.get();
        if (acquired) {
            LOG.info("[{}] Acquired lock for task: {}", nodeId, taskName);
        }
        return acquired;
    }

    /**
     * Process a task when this node has successfully acquired the lock.
     */
    private <T> void processTaskWithLock(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture,
            org.infinispan.lock.api.ClusteredLock taskLock) {

        // Double-check the status after acquiring the lock
        var currentState = taskStatusCache.get(taskName);

        if (TaskState.COMPLETED.equals(currentState)) {
            handleCompletedTask(taskName, resultFuture);
            return;
        }

        if (TaskState.RUNNING.equals(currentState)) {
            handleAlreadyRunningTask(taskName, resultFuture, taskLock);
            return;
        }

        // If we reached here, the task is NOT_STARTED and we hold the lock
        executeTask(taskName, task, resultFuture);
    }

    /**
     * Handle the case where the task was already completed by another node.
     */
    private <T> void handleCompletedTask(
            String taskName,
            CompletableFuture<Optional<T>> resultFuture) {
        LOG.info("[{}] Task '{}' completed by another node while "
                + "acquiring lock. Releasing lock and proceeding.",
                nodeId, taskName);
        resultFuture.complete(fetchTaskResult(taskName));
    }

    /**
     * Handle the case where the task is already running on another node.
     */
    private <T> void handleAlreadyRunningTask(
            String taskName,
            CompletableFuture<Optional<T>> resultFuture,
            org.infinispan.lock.api.ClusteredLock taskLock) {
        LOG.warn("""
            [{}] Task '{}' found RUNNING after acquiring \
            lock. Releasing lock and waiting for \
            completion.""", nodeId, taskName);
        taskLock.unlock();
        waitForTaskCompletionAsync(taskName, resultFuture);
    }

    /**
     * Execute the task when this node is designated as the runner.
     */
    private <T> void executeTask(
            String taskName,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {
        taskStatusCache.put(taskName, TaskState.RUNNING);
        LOG.info("[{}] Node is running task: {}", nodeId, taskName);
        try {
            // Execute the actual task
            var result = task.execute(
                    CrawlSession.get(cluster.getLocalNode()));
            taskStatusCache.put(taskName, TaskState.COMPLETED);
            taskResultCache.put(taskName,
                    new StringSerializedObject(result));
            LOG.info("[{}] Task '{}' completed successfully.",
                    nodeId,
                    taskName);
            resultFuture.complete(ofNullable(result));
        } catch (Exception e) {
            LOG.error("[{}] Task '{}' failed: {}", nodeId, taskName,
                    e.getMessage());
            taskStatusCache.put(taskName, TaskState.FAILED);
            resultFuture.completeExceptionally(e);
        }
    }

    private <T> Optional<T> fetchTaskResult(String taskName) {
        return ofNullable(taskResultCache.get(taskName))
                .map(StringSerializedObject::toObject);
    }

    /**
     * Waits for the specified task to reach a COMPLETED state in the cache.
     * This method polls the cache at regular intervals.
     *
     * @param taskName The name of the task to wait for.
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    private void waitForTaskCompletion(String taskName)
            throws InterruptedException {
        while (true) {
            var currentState = taskStatusCache.get(taskName);
            if (TaskState.COMPLETED.equals(currentState)
                    || TaskState.FAILED.equals(currentState)) {
                LOG.info("[{}] Task '{}' finished (state: {}). Proceeding.",
                        nodeId, taskName, currentState);
                break;
            }
            // If NOT_STARTED or RUNNING, wait and re-check
            Thread.sleep(STATUS_POLLING_INTERVAL_MS);
        }
    }

    /**
     * Waits for the task completion asynchronously and completes the future.
     */
    private <T> void waitForTaskCompletionAsync(
            String taskName,
            CompletableFuture<Optional<T>> resultFuture) {

        CompletableFuture.runAsync(() -> {
            try {
                waitForTaskCompletion(taskName);
                var state = taskStatusCache.get(taskName);
                if (TaskState.COMPLETED.equals(state)) {
                    resultFuture.complete(fetchTaskResult(taskName));
                } else {
                    // Task failed
                    resultFuture.completeExceptionally(
                            new RuntimeException("Task execution failed"));
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Executes a given task on all nodes exactly once and reduces the results.
     * Only one node triggers the task, all nodes participate.
     */
    @Override
    public <T, R> CompletableFuture<R> runOnAllOnceAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        var resultFuture = new CompletableFuture<R>();
        var triggerKey = taskName + ":trigger";
        var resultKeyPrefix = taskName + ":result:";
        // Only one node triggers the task
        synchronized (InfinispanTaskManager.class) {
            if (taskStatusCache.get(triggerKey) == null) {
                taskStatusCache.put(triggerKey, TaskState.RUNNING);
            }
        }
        // All nodes participate
        CompletableFuture.runAsync(() -> {
            try {
                // Each node checks if it has already participated
                var nodeResultKey = resultKeyPrefix + nodeId;
                if (taskResultCache.get(nodeResultKey) == null) {
                    var result = task
                            .execute(CrawlSession.get(cluster.getLocalNode()));
                    taskResultCache.put(nodeResultKey,
                            new StringSerializedObject(result));
                }
                // Wait for all nodes to participate
                while (true) {
                    var allNodeNames = cluster.getAllNodeNames();
                    var allDone = true;
                    java.util.List<T> results = new java.util.ArrayList<>();
                    for (String n : allNodeNames) {
                        var obj = taskResultCache.get(resultKeyPrefix + n);
                        if (obj == null) {
                            allDone = false;
                            break;
                        }
                        results.add((T) obj.toObject());
                    }
                    if (allDone) {
                        // Reduce and complete
                        var reduced = reducer.reduce(results);
                        resultFuture.complete(reduced);
                        taskStatusCache.put(triggerKey, TaskState.COMPLETED);
                        break;
                    }
                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
        return resultFuture;
    }

    @Override
    public <T, R> R runOnAllOnceSync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        try {
            return runOnAllOnceAsync(taskName, task, reducer).get();
        } catch (Exception e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }

    /**
     * Executes a repeatable task on all nodes and reduces the results.
     */
    @Override
    public <T, R> CompletableFuture<R> runOnAllAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        var resultFuture = new CompletableFuture<R>();
        var resultKeyPrefix = taskName + ":result:";
        CompletableFuture.runAsync(() -> {
            try {
                var nodeResultKey = resultKeyPrefix + nodeId;
                var result =
                        task.execute(CrawlSession.get(cluster.getLocalNode()));
                taskResultCache.put(nodeResultKey,
                        new StringSerializedObject(result));
                // Wait for all nodes to participate
                while (true) {
                    var allNodeNames = cluster.getAllNodeNames();
                    var allDone = true;
                    java.util.List<T> results = new java.util.ArrayList<>();
                    for (String n : allNodeNames) {
                        var obj = taskResultCache.get(resultKeyPrefix + n);
                        if (obj == null) {
                            allDone = false;
                            break;
                        }
                        results.add((T) obj.toObject());
                    }
                    if (allDone) {
                        var reduced = reducer.reduce(results);
                        resultFuture.complete(reduced);
                        break;
                    }
                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
        return resultFuture;
    }

    @Override
    public <T, R> R runOnAllSync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        try {
            return runOnAllAsync(taskName, task, reducer).get();
        } catch (Exception e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }
}