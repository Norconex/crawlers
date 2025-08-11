package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
public class InfinispanTaskManager implements TaskManager, Closeable {

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
    private final Set<String> acquiredLocks = ConcurrentHashMap.newKeySet();

    // Track running tasks by name
    private final ConcurrentHashMap<String, ClusterTask<?>> runningTasks =
            new ConcurrentHashMap<>();

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
        if (runningTasks.containsKey(taskName)) {
            LOG.warn(
                    "[{}] Task '{}' is already running on this node. Preventing concurrent execution.",
                    nodeId, taskName);
            throw new IllegalStateException(
                    "Task '" + taskName + "' is already running on this node.");
        }
        runningTasks.put(taskName, task); // Track running instance
        // Create a completable future to hold the result
        var resultFuture = new CompletableFuture<Optional<T>>();

        if (lockManager == null) {
            // Local mode: just run the task directly
            executeTaskWithStopCheck(taskName, task, resultFuture);
            runningTasks.remove(taskName); // Remove after execution
            return resultFuture;
        }

        // Execute the clustered task asynchronously
        executeClusteredRepeatableTaskAsyncWithStop(taskName, task,
                resultFuture);
        runningTasks.remove(taskName); // Remove after execution
        return resultFuture;
    }

    private <T> void executeClusteredRepeatableTaskAsyncWithStop(
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
                    executeTaskWithStopCheck(taskName, task, resultFuture);
                } else {
                    LOG.info("[{}] Could not acquire lock for repeatable task "
                            + "'{}'. Skipping execution.",
                            nodeId, taskName);
                    resultFuture.complete(Optional.empty());
                    // Do NOT wait for completion
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
            acquiredLocks.add(taskName);
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
        // Deprecated: now handled by executeTaskWithStopCheck
        executeTaskWithStopCheck(taskName, task, resultFuture);
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
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
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
                        var reduced = reducer != null
                                ? reducer.reduce(results)
                                : null;
                        resultFuture.complete(reduced);
                        break;
                    }
                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) { //NOSONAR
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

    @Override
    public void stopTask(String taskName) {
        LOG.info("[{}] Stop requested for task: {}", nodeId, taskName);
        ClusterTask<?> runningTask = runningTasks.get(taskName);
        if (runningTask != null) {
            LOG.info("[{}] Invoking stop() on running task instance: {}",
                    nodeId, taskName);
            runningTask.stop(CrawlSession.get(cluster.getLocalNode()));
            runningTasks.remove(taskName); // Remove after stopping
        }
        for (String n : cluster.getAllNodeNames()) {
            taskStatusCache.put(taskName + ":" + n, TaskState.STOP_REQUESTED);
        }
    }

    private boolean isStopRequested(String taskName, String nodeId) {
        return TaskState.STOP_REQUESTED
                .equals(taskStatusCache.get(taskName + ":" + nodeId));
    }

    private void setStopped(String taskName, String nodeId) {
        taskStatusCache.put(taskName + ":" + nodeId, TaskState.STOPPED);
    }

    @Override
    public void close() {
        // Release all acquired locks before shutdown
        if (lockManager != null) {
            for (String lockName : acquiredLocks) {
                try {
                    var lock = lockManager.get(lockName);
                    if (lock != null) {
                        lock.unlock();
                        LOG.info("[{}] Released lock '{}' during shutdown.",
                                nodeId, lockName);
                    }
                } catch (Exception e) {
                    LOG.warn(
                            "[{}] Failed to release lock '{}' during shutdown: {}",
                            nodeId, lockName, e.getMessage());
                }
            }
            acquiredLocks.clear();
        }
        // No explicit resources to release, but nullify references for GC
        // If future resources (threads, etc.) are added, clean up here
        // Example: if you add background threads, interrupt/stop them here
    }

    private <T> void executeTaskWithStopCheck(String taskName,
            ClusterTask<T> task, CompletableFuture<Optional<T>> resultFuture) {
        var session = CrawlSession.get(cluster.getLocalNode());
        taskStatusCache.put(taskName + ":" + nodeId, TaskState.RUNNING);
        LOG.info("[{}] Node is running task: {}", nodeId, taskName);
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(() -> {
                T result = null;
                var executed = false;
                while (true) {
                    if (isStopRequested(taskName, nodeId)) {
                        LOG.info(
                                "[{}] Stop requested for task '{}'. Stopping...",
                                nodeId, taskName);
                        task.stop(session);
                        setStopped(taskName, nodeId);
                        return null;
                    }
                    if (!executed) {
                        result = task.execute(session);
                        executed = true;
                    }
                    // After execution, just wait for stop request or exit
                    if (executed) {
                        break;
                    }
                }
                return result;
            });
            var result = future.get();
            if (taskStatusCache
                    .get(taskName + ":" + nodeId) == TaskState.STOPPED) {
                resultFuture.complete(Optional.empty());
            } else {
                taskResultCache.put(taskName,
                        new StringSerializedObject(result));
                resultFuture.complete(ofNullable(result));
            }
        } catch (Exception e) {
            LOG.error("[{}] Task '{}' failed: {}", nodeId, taskName,
                    e.getMessage());
            resultFuture.completeExceptionally(e);
        } finally {
            executor.shutdownNow();
        }
    }
}