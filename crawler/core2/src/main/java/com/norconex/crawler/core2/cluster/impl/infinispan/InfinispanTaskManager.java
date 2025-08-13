package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

        var resultFuture = new CompletableFuture<Optional<T>>();

        if (lockManager == null) {
            // Local mode: just run the task directly
            executeTaskDirectly(taskName, task, resultFuture);
            return resultFuture;
        }

        // Execute the clustered task asynchronously
        ensureLockExists(taskName);
        var taskLock = lockManager.get(taskName);

        CompletableFuture.runAsync(() -> {
            var lockAcquiredSuccessfully = false;
            try {
                lockAcquiredSuccessfully = acquireLock(taskName, taskLock);

                if (lockAcquiredSuccessfully) {
                    // This node will execute the task
                    executeTaskDirectly(taskName, task, resultFuture);
                } else {
                    // Wait for the executing node to complete and get cached result
                    LOG.info(
                            "[{}] Could not acquire lock for task '{}'. Waiting for completion by another node.",
                            nodeId, taskName);
                    waitForTaskCompletionAndGetResult(taskName, resultFuture);
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

        return resultFuture;
    }

    @Override
    public <T> CompletableFuture<Optional<T>> runOnOneOnceAsync(
            String taskName, ClusterTask<T> task) {
        LOG.info("[{}] Attempting to run 'once' task: {}", nodeId, taskName);

        var resultFuture = new CompletableFuture<Optional<T>>();
        var onceKey = taskName + ":once";

        if (lockManager == null) {
            // Local mode: check if already completed
            var currentState = taskStatusCache.get(onceKey);
            if (TaskState.COMPLETED.equals(currentState)) {
                LOG.info("[{}] Task '{}' already completed once in local mode",
                        nodeId, taskName);
                resultFuture.complete(fetchTaskResult(onceKey));
                return resultFuture;
            }

            executeTaskDirectlyOnce(onceKey, task, resultFuture);
            return resultFuture;
        }

        // Execute the clustered task asynchronously
        ensureLockExists(onceKey);
        var taskLock = lockManager.get(onceKey);

        CompletableFuture.runAsync(() -> {
            var lockAcquiredSuccessfully = false;
            try {
                lockAcquiredSuccessfully = acquireLock(onceKey, taskLock);

                if (lockAcquiredSuccessfully) {
                    // Check if task was already completed
                    var currentState = taskStatusCache.get(onceKey);
                    if (TaskState.COMPLETED.equals(currentState)) {
                        LOG.info("[{}] Task '{}' already completed once by "
                                + "another node", nodeId, taskName);
                        resultFuture.complete(fetchTaskResult(onceKey));
                        return;
                    }

                    // This node will execute the task
                    executeTaskDirectlyOnce(onceKey, task, resultFuture);
                } else {
                    // Wait for the executing node to complete and get cached result
                    LOG.info(
                            "[{}] Could not acquire lock for task '{}'. Waiting for completion by another node.",
                            nodeId, taskName);
                    waitForTaskCompletionAndGetResult(onceKey, resultFuture);
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

        return resultFuture;
    }

    @Override
    public <T, R> CompletableFuture<R> runOnAllAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        var resultFuture = new CompletableFuture<R>();
        var allKey = taskName + ":all";
        var resultKeyPrefix = allKey + ":result:";

        CompletableFuture.runAsync(() -> {
            try {
                // Each node executes the task and stores its result
                var nodeResultKey = resultKeyPrefix + nodeId;
                var result =
                        task.execute(CrawlSession.get(cluster.getLocalNode()));
                taskResultCache.put(nodeResultKey,
                        new StringSerializedObject(result));

                // Wait for all nodes to complete their execution
                var allNodeNames = cluster.getAllNodeNames();
                while (true) {
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
                        // All nodes completed, reduce results
                        var reduced = reducer != null ? reducer.reduce(results)
                                : null;

                        // Cache the final result for all nodes
                        taskResultCache.put(allKey + ":final",
                                new StringSerializedObject(reduced));

                        resultFuture.complete(reduced);
                        break;
                    }

                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                LOG.error("[{}] RunOnAll task '{}' failed: {}", nodeId,
                        taskName, e.getMessage());
                resultFuture.completeExceptionally(e);
            }
        });

        return resultFuture;
    }

    @Override
    public <T, R> CompletableFuture<R> runOnAllOnceAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer) {
        var resultFuture = new CompletableFuture<R>();
        var onceAllKey = taskName + ":allonce";
        var triggerKey = onceAllKey + ":trigger";
        var resultKeyPrefix = onceAllKey + ":result:";

        CompletableFuture.runAsync(() -> {
            try {
                // Check if this task has already been completed once
                var triggerState = taskStatusCache.get(triggerKey);
                if (TaskState.COMPLETED.equals(triggerState)) {
                    LOG.info(
                            "[{}] Task '{}' already completed once, returning cached result",
                            nodeId, taskName);
                    // Return cached final result
                    var cachedResult =
                            taskResultCache.get(onceAllKey + ":final");
                    if (cachedResult != null) {
                        resultFuture.complete((R) cachedResult.toObject());
                    } else {
                        resultFuture.completeExceptionally(
                                new RuntimeException("No cached result found"));
                    }
                    return;
                }

                // Mark as triggered if not already done (atomic check-and-set)
                synchronized (InfinispanTaskManager.class) {
                    triggerState = taskStatusCache.get(triggerKey);
                    if (triggerState == null) {
                        taskStatusCache.put(triggerKey, TaskState.RUNNING);
                    }
                }

                // Each node executes the task once and stores result
                var nodeResultKey = resultKeyPrefix + nodeId;
                if (taskResultCache.get(nodeResultKey) == null) {
                    var result = task
                            .execute(CrawlSession.get(cluster.getLocalNode()));
                    taskResultCache.put(nodeResultKey,
                            new StringSerializedObject(result));
                }

                // Wait for all nodes to participate
                var allNodeNames = cluster.getAllNodeNames();
                while (true) {
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
                        // All nodes completed, reduce and mark as completed
                        var reduced = reducer.reduce(results);

                        // Cache the final result for future calls
                        taskResultCache.put(onceAllKey + ":final",
                                new StringSerializedObject(reduced));
                        taskStatusCache.put(triggerKey, TaskState.COMPLETED);

                        resultFuture.complete(reduced);
                        break;
                    }

                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                LOG.error("[{}] RunOnAllOnce task '{}' failed: {}", nodeId,
                        taskName, e.getMessage());
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

    /**
     * Execute task directly without complex stop checking for runOnOne methods
     */
    private <T> void executeTaskDirectly(String taskName, ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {
        try {
            var session = CrawlSession.get(cluster.getLocalNode());
            var result = task.execute(session);

            // Store result for other nodes to access
            taskResultCache.put(taskName, new StringSerializedObject(result));
            resultFuture.complete(ofNullable(result));

            LOG.info("[{}] Task '{}' completed successfully", nodeId, taskName);
        } catch (Exception e) {
            LOG.error("[{}] Task '{}' failed: {}", nodeId, taskName,
                    e.getMessage());
            resultFuture.completeExceptionally(e);
        }
    }

    /**
     * Execute task directly for "once" methods with completion status tracking
     */
    private <T> void executeTaskDirectlyOnce(String taskKey,
            ClusterTask<T> task,
            CompletableFuture<Optional<T>> resultFuture) {
        try {
            taskStatusCache.put(taskKey, TaskState.RUNNING);

            var session = CrawlSession.get(cluster.getLocalNode());
            var result = task.execute(session);

            // Store result and mark as completed
            taskResultCache.put(taskKey, new StringSerializedObject(result));
            taskStatusCache.put(taskKey, TaskState.COMPLETED);
            resultFuture.complete(ofNullable(result));

            LOG.info("[{}] Once task '{}' completed successfully", nodeId,
                    taskKey);
        } catch (Exception e) {
            LOG.error("[{}] Once task '{}' failed: {}", nodeId, taskKey,
                    e.getMessage());
            taskStatusCache.put(taskKey, TaskState.FAILED);
            resultFuture.completeExceptionally(e);
        }
    }

    /**
     * Wait for task completion and get the cached result
     */
    private <T> void waitForTaskCompletionAndGetResult(String taskKey,
            CompletableFuture<Optional<T>> resultFuture) {
        CompletableFuture.runAsync(() -> {
            try {
                // Poll for result availability
                while (true) {
                    var cachedResult = taskResultCache.get(taskKey);
                    if (cachedResult != null) {
                        resultFuture.complete(
                                Optional.of((T) cachedResult.toObject()));
                        return;
                    }

                    // Check if task failed
                    var status = taskStatusCache.get(taskKey);
                    if (TaskState.FAILED.equals(status)) {
                        resultFuture.completeExceptionally(new RuntimeException(
                                "Task failed on executing node"));
                        return;
                    }

                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
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

    private <T> Optional<T> fetchTaskResult(String taskName) {
        return ofNullable(taskResultCache.get(taskName))
                .map(StringSerializedObject::toObject);
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
    }
}
