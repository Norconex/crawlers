package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lock.EmbeddedClusteredLockManagerFactory;
import org.infinispan.lock.api.ClusteredLockManager;

import com.norconex.crawler.core2.cluster.TaskManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the distributed execution of tasks using Infinispan for
 * synchronization and state management.
 */
@Slf4j
public class InfinispanTaskManager implements TaskManager {

    private static final String TASK_STATUS_CACHE_NAME = "taskStatusCache";
    private static final long LOCK_ACQUISITION_TIMEOUT_MS = 5000;
    private static final long STATUS_POLLING_INTERVAL_MS = 200;

    final Cache<String, TaskState> taskStatusCache;
    private final ClusteredLockManager lockManager;
    private final String nodeId; // Unique identifier for this node

    /**
     * Constructs a DistributedTaskManager.
     *
     * @param manager The Infinispan EmbeddedCacheManager instance.
     * @param nodeId A unique identifier for the current node, useful for
     *     logging.
     */
    public InfinispanTaskManager(InfinispanCacheManager manager,
            String nodeId) {
        this.nodeId = nodeId;
        var cacheManager = manager.vendor();

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
        }
        taskStatusCache = cacheManager.getCache(TASK_STATUS_CACHE_NAME);
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
     * @throws InterruptedException If the current thread is interrupted while
     *     waiting.
     * @throws TimeoutException If waiting for task completion times out (not
     *     implemented with explicit timeout here, but possible).
     * @throws ExecutionException could not execute task
     */
    @Override
    public void runOnOneOnceAndWait(String taskName, Runnable task)
            throws InterruptedException, TimeoutException, ExecutionException {
        LOG.info("[{}] Attempting to run task: {}", nodeId, taskName);

        // 1. Check if the task is already completed
        var currentState = taskStatusCache.get(taskName);
        if (TaskState.COMPLETED.equals(currentState)) {
            LOG.info("{} Task '{}' already completed. Proceeding without "
                    + "waiting.", nodeId, taskName);
            return;
        }

        if (lockManager == null) {
            // Local mode: just run the task directly
            try {
                taskStatusCache.put(taskName, TaskState.RUNNING);
                task.run();
                taskStatusCache.put(taskName, TaskState.COMPLETED);
            } catch (Exception e) {
                taskStatusCache.put(taskName, TaskState.FAILED);
                throw e;
            }
            return;
        }

        // Create the lock if it doesn't exist
        if (!lockManager.isDefined(taskName)) {
            lockManager.defineLock(taskName);
            LOG.info("[{}] Created lock for task: {}", nodeId, taskName);
        }

        // Get the clustered lock for this specific task
        var taskLock = lockManager.get(taskName);

        // 2. Try to acquire the lock
        var lockAcquiredSuccessfully = false;
        try {
            var lockFuture = taskLock.tryLock(LOCK_ACQUISITION_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            lockAcquiredSuccessfully = lockFuture.get();

            // Rest of the method remains the same...
            if (lockAcquiredSuccessfully) {
                // Lock acquired, this node is the designated runner
                // (or potential runner)
                LOG.info("[{}] Acquired lock for task: {}", nodeId, taskName);

                // Double-check the status after acquiring the lock (important
                // for correctness)
                currentState = taskStatusCache.get(taskName);
                if (TaskState.COMPLETED.equals(currentState)) {
                    LOG.info("[{}] Task '{}' completed by another node while "
                            + "acquiring lock. Releasing lock and proceeding.",
                            nodeId, taskName);
                    return;
                }

                if (TaskState.RUNNING.equals(currentState)) {
                    LOG.warn("""
                        [{}] Task '{}' found RUNNING after acquiring \
                        lock. Releasing lock and waiting for \
                        completion.""", nodeId, taskName);
                    taskLock.unlock();
                    // Mark as not acquired so finally block doesn't try to
                    // unlock again
                    lockAcquiredSuccessfully = false;
                    waitForTaskCompletion(taskName);
                    return;
                }

                // If we reached here, the task is NOT_STARTED and we hold the
                // lock
                taskStatusCache.put(taskName, TaskState.RUNNING);
                LOG.info("[{}] Node is running task: {}", nodeId, taskName);
                try {
                    task.run(); // Execute the actual task
                    taskStatusCache.put(taskName, TaskState.COMPLETED);
                    LOG.info("[{}] Task '{}' completed successfully.", nodeId,
                            taskName);
                } catch (Exception e) {
                    LOG.error("[{}] Task '{}' failed: {}", nodeId, taskName,
                            e.getMessage());
                    taskStatusCache.put(taskName, TaskState.FAILED);
                    throw e;
                }

            } else {
                LOG.info("[{}] Could not acquire lock for task '{}'. Waiting "
                        + "for completion by another node.",
                        nodeId, taskName);
                waitForTaskCompletion(taskName);
            }
        } finally {
            if (lockAcquiredSuccessfully) {
                taskLock.unlock();
                LOG.info("[{}] Released lock for task: {}", nodeId, taskName);
            }
        }
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
}
