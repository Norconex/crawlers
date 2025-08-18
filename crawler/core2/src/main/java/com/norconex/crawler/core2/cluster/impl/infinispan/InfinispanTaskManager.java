package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lock.EmbeddedClusteredLockManagerFactory;
import org.infinispan.lock.api.ClusteredLockManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;

import com.norconex.crawler.core2.cluster.AllOncePolicy;
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

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);

    // Metadata helper
    private record AllOnceMetadata(
            String taskName,
            long startTime,
            long endTime,
            Set<String> snapshotMembers,
            Set<String> successNodes,
            Map<String,String> failureReasons,
            boolean completed,
            boolean failed,
            AllOncePolicy policy) implements java.io.Serializable {}

    final Cache<String, TaskState> taskStatusCache;
    final Cache<String, StringSerializedObject> taskResultCache;
    private final ClusteredLockManager lockManager;
    private final InfinispanCluster cluster;
    private final String nodeId;
    private final Set<String> acquiredLocks = ConcurrentHashMap.newKeySet();

    // Track running tasks by name
    private final ConcurrentHashMap<String, ClusterTask<?>> runningTasks =
            new ConcurrentHashMap<>();

    // === Continuous Task Implementation ===

    // Track continuous tasks by name
    private final ConcurrentHashMap<String, Thread> continuousWorkers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,
            com.norconex.crawler.core2.cluster.ContinuousStats> continuousStats =
                    new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,
            CompletableFuture<Void>> continuousCompletionFutures =
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
        // Register listener for distributed runOnAllOnce tasks
        taskStatusCache.addListener(new AllOnceTriggerListener());
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
                var allNodeNames = cluster.getNodeNames();
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
        return runOnAllOnceAsync(taskName, task, reducer, AllOncePolicy.defaults());
    }

    @Override
    public <T, R> CompletableFuture<R> runOnAllOnceAsync(String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer,
            AllOncePolicy policy) {
        var resultFuture = new CompletableFuture<R>();
        var onceAllKey = taskName + ":allonce";
        var triggerKey = onceAllKey + ":trigger";
        var resultKeyPrefix = onceAllKey + ":result:";
        var errorKeyPrefix = onceAllKey + ":error:";
        var hbKeyPrefix = onceAllKey + ":hb:";
        var membersKey = onceAllKey + ":members";
        var metaKey = onceAllKey + ":meta";
        var taskDefKey = onceAllKey + ":def:task";
        var reducerDefKey = onceAllKey + ":def:reducer";

        CompletableFuture.runAsync(() -> {
            try {
                // Fast path
                var state = taskStatusCache.get(triggerKey);
                if (TaskState.COMPLETED.equals(state)) {
                    var cached = taskResultCache.get(onceAllKey + ":final");
                    if (cached != null) {
                        resultFuture.complete((R) cached.toObject());
                    } else {
                        resultFuture.completeExceptionally(new RuntimeException("No cached result found"));
                    }
                    return;
                } else if (TaskState.FAILED.equals(state)) {
                    resultFuture.completeExceptionally(new RuntimeException("Task previously failed"));
                    return;
                }

                // Attempt to become trigger
                var prev = taskStatusCache.putIfAbsent(triggerKey, TaskState.RUNNING);
                if (prev != null) {
                    // Another node is trigger or already done; wait.
                    while (true) {
                        var s = taskStatusCache.get(triggerKey);
                        if (TaskState.COMPLETED.equals(s)) {
                            var cached = taskResultCache.get(onceAllKey + ":final");
                            if (cached != null) {
                                resultFuture.complete((R) cached.toObject());
                            } else {
                                resultFuture.completeExceptionally(new RuntimeException("No cached result found after completion"));
                            }
                            return;
                        } else if (TaskState.FAILED.equals(s)) {
                            resultFuture.completeExceptionally(new RuntimeException("Task failed"));
                            return;
                        }
                        Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                    }
                }

                // === Trigger execution ===
                LOG.info("[{}] Trigger node starting cluster-wide once task '{}' with policy {}", nodeId, taskName, policy);
                // Serialize task & reducer
                taskResultCache.put(taskDefKey, new StringSerializedObject(task));
                if (reducer != null) {
                    taskResultCache.put(reducerDefKey, new StringSerializedObject(reducer));
                }

                // Snapshot members and persist
                var members = new HashSet<>(cluster.getNodeNames());
                taskResultCache.put(membersKey, new StringSerializedObject(members));
                var startTime = System.currentTimeMillis();

                // Containers
                var successNodes = new HashSet<String>();
                var failureReasons = new HashMap<String,String>();
                var lastNewResultTime = new long[]{System.currentTimeMillis()};

                // Execute locally with heartbeat support
                executeWithHeartbeat(taskName, onceAllKey, hbKeyPrefix + nodeId, policy, () -> {
                    var localResult = task.execute(CrawlSession.get(cluster.getLocalNode()));
                    taskResultCache.put(resultKeyPrefix + nodeId, new StringSerializedObject(localResult));
                    successNodes.add(nodeId);
                    lastNewResultTime[0] = System.currentTimeMillis();
                });

                // Monitoring loop
                while (true) {
                    // Collect new successes
                    for (String m : members) {
                        if (successNodes.contains(m) || failureReasons.containsKey(m)) {
                            continue;
                        }
                        var res = taskResultCache.get(resultKeyPrefix + m);
                        if (res != null) {
                            successNodes.add(m);
                            lastNewResultTime[0] = System.currentTimeMillis();
                            continue;
                        }
                        var err = taskResultCache.get(errorKeyPrefix + m);
                        if (err != null) {
                            failureReasons.put(m, (String) err.toObject());
                            lastNewResultTime[0] = System.currentTimeMillis();
                            continue;
                        }
                        // Stale heartbeat detection
                        if (policy.getStaleHeartbeatMs() > 0 && policy.getHeartbeatIntervalMs() > 0) {
                            var hb = taskResultCache.get(hbKeyPrefix + m);
                            if (hb != null) {
                                long last = (Long) hb.toObject();
                                if (System.currentTimeMillis() - last > policy.getStaleHeartbeatMs()) {
                                    failureReasons.put(m, "STALE_HEARTBEAT");
                                    lastNewResultTime[0] = System.currentTimeMillis();
                                }
                            }
                        }
                    }

                    boolean allAccounted = successNodes.size() + failureReasons.size() == members.size();
                    boolean quorumReached = successNodes.size() >= Math.max(1, policy.getMinSuccesses());

                    boolean finalizeNow = false;
                    if (policy.isRequireAll()) {
                        finalizeNow = allAccounted;
                    } else if (quorumReached) {
                        if (allAccounted) {
                            finalizeNow = true;
                        } else if (policy.getIdleResultWaitMs() >= 0 &&
                                System.currentTimeMillis() - lastNewResultTime[0] >= policy.getIdleResultWaitMs()) {
                            finalizeNow = true;
                        }
                    }

                    if (finalizeNow) {
                        boolean zeroSuccess = successNodes.isEmpty();
                        if (zeroSuccess && policy.isFailIfZeroSuccess()) {
                            LOG.warn("[{}] runOnAllOnce '{}' failing: zero successes (failIfZeroSuccess=true)", nodeId, taskName);
                            var meta = new AllOnceMetadata(taskName, startTime, System.currentTimeMillis(), members,
                                    successNodes, failureReasons, false, true, policy);
                            taskResultCache.put(metaKey, new StringSerializedObject(meta));
                            taskStatusCache.put(triggerKey, TaskState.FAILED);
                            resultFuture.completeExceptionally(new RuntimeException("No successful node executions"));
                        } else {
                            // Build result list from success nodes preserving deterministic order
                            var ordered = members.stream().filter(successNodes::contains).collect(Collectors.toList());
                            java.util.List<T> resultsList = new java.util.ArrayList<>();
                            for (String s : ordered) {
                                var so = taskResultCache.get(resultKeyPrefix + s);
                                if (so != null) {
                                    resultsList.add((T) so.toObject());
                                }
                            }
                            R reduced;
                            if (reducer != null) {
                                reduced = reducer.reduce(resultsList);
                            } else {
                                reduced = resultsList.isEmpty() ? null : (R) resultsList.get(0);
                            }
                            taskResultCache.put(onceAllKey + ":final", new StringSerializedObject(reduced));
                            var meta = new AllOnceMetadata(taskName, startTime, System.currentTimeMillis(), members,
                                    successNodes, failureReasons, true, false, policy);
                            taskResultCache.put(metaKey, new StringSerializedObject(meta));
                            taskStatusCache.put(triggerKey, TaskState.COMPLETED);
                            resultFuture.complete(reduced);
                            if (!failureReasons.isEmpty()) {
                                LOG.warn("[{}] runOnAllOnce '{}' completed with partial failures: {}", nodeId, taskName, failureReasons);
                            }
                            scheduleCleanup(onceAllKey, members, successNodes, failureReasons.keySet(), policy);
                        }
                        return;
                    }

                    Thread.sleep(STATUS_POLLING_INTERVAL_MS);
                }
            } catch (Exception e) {
                LOG.error("[{}] Trigger execution for '{}' failed: {}", nodeId, taskName, e.getMessage());
                taskStatusCache.put(triggerKey, TaskState.FAILED);
                resultFuture.completeExceptionally(e);
            }
        });

        return resultFuture;
    }

    private void executeWithHeartbeat(String taskName, String onceAllKey, String hbKey,
            AllOncePolicy policy, Runnable action) {
        if (policy.getHeartbeatIntervalMs() <= 0) {
            try { action.run(); } catch (RuntimeException e) { throw e; }
            return;
        }
        var done = new AtomicBoolean(false);
        ScheduledFuture<?> hbFuture = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                taskResultCache.put(hbKey, new StringSerializedObject(System.currentTimeMillis()));
            } catch (Exception e) {
                LOG.debug("[{}] Heartbeat update failed for {}: {}", nodeId, taskName, e.getMessage());
            }
        }, 0, policy.getHeartbeatIntervalMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
        try {
            action.run();
        } finally {
            done.set(true);
            hbFuture.cancel(false);
            // final heartbeat to mark completion
            try { taskResultCache.put(hbKey, new StringSerializedObject(System.currentTimeMillis())); } catch (Exception ex) { /*ignore*/ }
        }
    }

    private void scheduleCleanup(String onceAllKey, Set<String> members, Set<String> successes, Set<String> failures, AllOncePolicy policy) {
        if (policy.getRetentionMs() <= 0) {
            return; // keep forever
        }
        SCHEDULER.schedule(() -> {
            try {
                var resultKeyPrefix = onceAllKey + ":result:";
                var errorKeyPrefix = onceAllKey + ":error:";
                var hbKeyPrefix = onceAllKey + ":hb:";
                for (String m : members) {
                    taskResultCache.remove(resultKeyPrefix + m);
                    taskResultCache.remove(errorKeyPrefix + m);
                    taskResultCache.remove(hbKeyPrefix + m);
                }
            } catch (Exception e) {
                LOG.debug("[{}] Cleanup skipped for '{}' due to: {}", nodeId, onceAllKey, e.getMessage());
            }
        }, policy.getRetentionMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Listener(clustered = true, observation = Listener.Observation.POST)
    private class AllOnceTriggerListener { // NOSONAR nested class
        @CacheEntryCreated
        public void onCreate(CacheEntryCreatedEvent<String, TaskState> e) {
            handle(e.getKey(), e.getValue(), e.isPre());
        }
        @CacheEntryModified
        public void onModify(CacheEntryModifiedEvent<String, TaskState> e) {
            handle(e.getKey(), e.getValue(), e.isPre());
        }
        private void handle(String key, TaskState state, boolean pre) {
            if (pre) { return; }
            if (state != TaskState.RUNNING) { return; }
            if (!key.endsWith(":allonce:trigger")) { return; }
            var onceAllKey = key.substring(0, key.length() - ":trigger".length());
            var resultKeyPrefix = onceAllKey + ":result:";
            var taskDefKey = onceAllKey + ":def:task";
            var errorKeyPrefix = onceAllKey + ":error:";
            var hbKeyPrefix = onceAllKey + ":hb:";
            // If this node already produced a result or error, skip
            if (taskResultCache.get(resultKeyPrefix + nodeId) != null || taskResultCache.get(errorKeyPrefix + nodeId) != null) { return; }
            try {
                var serTask = taskResultCache.get(taskDefKey);
                if (serTask == null) {
                    LOG.warn("[{}] Task definition not yet available for key '{}'", nodeId, key);
                    return; // Will catch on modification event
                }
                @SuppressWarnings("unchecked")
                var task = (ClusterTask<Object>) serTask.toObject();
                // Basic heartbeat emission (without policy retrieval, using defaults)
                taskResultCache.put(hbKeyPrefix + nodeId, new StringSerializedObject(System.currentTimeMillis()));
                var result = task.execute(CrawlSession.get(cluster.getLocalNode()));
                taskResultCache.put(resultKeyPrefix + nodeId, new StringSerializedObject(result));
                LOG.info("[{}] Executed once-all task '{}' after trigger", nodeId, onceAllKey);
            } catch (Exception ex) {
                LOG.error("[{}] Remote execution failure for key '{}': {}", nodeId, key, ex.getMessage());
                try {
                    taskResultCache.put(errorKeyPrefix + nodeId, new StringSerializedObject(ex.getClass().getSimpleName()+":"+ex.getMessage()));
                } catch (Exception ignore) { /* ignore */ }
            }
        }
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
        for (String n : cluster.getNodeNames()) {
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

    /**
     * Test utility method to check if a raw task result exists in the cache.
     * This is used by tests to verify retention cleanup behavior.
     */
    public boolean hasRawTaskResult(String key) {
        return taskResultCache.get(key) != null;
    }

    @Override
    public void startContinuous(String taskName,
            com.norconex.crawler.core2.cluster.ClusterContinuousTask worker) {
        LOG.info("[{}] Starting continuous task: {}", nodeId, taskName);

        // Initialize stats for this task
        var stats = new com.norconex.crawler.core2.cluster.ContinuousStats();
        continuousStats.put(taskName, stats);

        // Create completion future for this task
        var completionFuture = new CompletableFuture<Void>();
        continuousCompletionFutures.put(taskName, completionFuture);

        // Start worker thread
        var workerThread = new Thread(() -> {
            try {
                var session = CrawlSession.get(cluster.getLocalNode());
                worker.onStart(session);

                var consecutiveNoWork = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    // Check if stop was requested
                    var stopKey = taskName + ":stop";
                    if (TaskState.STOP_REQUESTED
                            .equals(taskStatusCache.get(stopKey))) {
                        LOG.info("[{}] Stop requested for continuous task: {}",
                                nodeId, taskName);
                        break;
                    }

                    try {
                        var result = worker.executeOne(session);

                        switch (result.status()) {
                            case WORK_DONE:
                                stats.incProcessed();
                                consecutiveNoWork = 0;
                                break;
                            case NO_WORK:
                                stats.incNoWork();
                                consecutiveNoWork++;
                                if (consecutiveNoWork >= 5) { // Auto-complete after 5 consecutive no-work cycles
                                    LOG.info(
                                            "[{}] Auto-completing continuous task '{}' after {} consecutive no-work cycles",
                                            nodeId, taskName,
                                            consecutiveNoWork);
                                    break;
                                }
                                Thread.sleep(1000); // Wait 1 second before next attempt
                                break;
                            case FAILED_RETRYABLE:
                                stats.incFailed();
                                Thread.sleep(100); // Brief delay before retry
                                break;
                            case FAILED_FATAL:
                                stats.incFailed();
                                LOG.error(
                                        "[{}] Fatal failure in continuous task '{}', stopping",
                                        nodeId, taskName);
                                return;
                        }

                        if (consecutiveNoWork >= 5) {
                            break; // Exit the loop to complete the task
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.error("[{}] Error in continuous task '{}': {}",
                                nodeId, taskName, e.getMessage());
                        stats.incFailed();
                    }
                }

                worker.onStop(session);
                LOG.info("[{}] Continuous task '{}' completed", nodeId,
                        taskName);

            } catch (Exception e) {
                LOG.error("[{}] Continuous task '{}' failed: {}", nodeId,
                        taskName, e.getMessage());
            } finally {
                // Mark task as completed
                completionFuture.complete(null);
                continuousWorkers.remove(taskName);
            }
        });

        workerThread.setName("continuous-" + taskName + "-" + nodeId);
        continuousWorkers.put(taskName, workerThread);
        workerThread.start();
    }

    @Override
    public void stopContinuous(String taskName) {
        LOG.info("[{}] Stopping continuous task: {}", nodeId, taskName);

        // Set stop flag in cache
        var stopKey = taskName + ":stop";
        taskStatusCache.put(stopKey, TaskState.STOP_REQUESTED);

        // Interrupt the worker thread
        var workerThread = continuousWorkers.get(taskName);
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }
    }

    @Override
    public CompletableFuture<Void> awaitContinuousCompletion(String taskName) {
        LOG.info("[{}] Awaiting continuous task completion: {}", nodeId,
                taskName);

        var completionFuture = continuousCompletionFutures.get(taskName);
        if (completionFuture != null) {
            return completionFuture;
        } else {
            // Task not found or already completed
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public <R> R finalizeContinuous(String taskName,
            com.norconex.crawler.core2.cluster.ClusterReducer<
                    com.norconex.crawler.core2.cluster.ContinuousStats,
                    R> reducer) {
        LOG.info("[{}] Finalizing continuous task: {}", nodeId, taskName);

        // Wait for completion first
        awaitContinuousCompletion(taskName).join();

        // Get stats from all nodes and reduce
        var allStats = getContinuousStats(taskName);
        if (reducer != null && !allStats.isEmpty()) {
            return reducer.reduce(allStats.values().stream().toList());
        }

        return null;
    }

    @Override
    public java.util.Map<String,
            com.norconex.crawler.core2.cluster.ContinuousStats>
            getContinuousStats(String taskName) {
        LOG.info("[{}] Getting continuous stats for task: {}", nodeId,
                taskName);

        var result = new java.util.HashMap<String,
                com.norconex.crawler.core2.cluster.ContinuousStats>();

        // Add local stats
        var localStats = continuousStats.get(taskName);
        if (localStats != null) {
            result.put(nodeId, localStats);
        }

        // In a full implementation, we would collect stats from all nodes in the cluster
        // For now, we just return the local stats
        return result;
    }
}
