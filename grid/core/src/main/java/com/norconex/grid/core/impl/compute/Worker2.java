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
package com.norconex.grid.core.impl.compute;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.compute.ExecutionMode;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.GridTask2;
import com.norconex.grid.core.compute.GridTaskRequest2;
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.task.TaskProgress;
import com.norconex.grid.core.impl.compute.task.TaskStoreManager2;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes tasks on a node, keep track of their status, and more.
 * Meant to be invoked remotely when a task is run on all nodes or targeting
 * this worker node.
 */
// A local instance is bound to the grid in the WorkDispatcher
@Slf4j
public class Worker2 {

    /**
     * Amount of time without the coordinator giving signs of live
     * after which we consider the grid stale and abort.
     */
    private static final Duration INACTIVE_COORD_TIMEOUT =
            Duration.ofMinutes(2);
    private static final Duration TASK_POLL_INTERVAL =
            Duration.ofSeconds(5);

    private final CoreGrid grid;
    private final TaskStoreManager2 taskStoreManager;
    private final Map<String, GridTask2> runningTasks =
            new ConcurrentHashMap<>();
    private final Map<String, GridTask2> terminatedTasks =
            new ConcurrentHashMap<>();

    /**
     * Update the database with the latest task progress.
     */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(1);

    boolean stopRequested = false;

    public Worker2(CoreGrid grid) {
        this.grid = grid;
        taskStoreManager = grid.getCompute().getTaskStoreManager();
    }

    /**
     * Mark this worker as ready for work. It will start listening for
     * tasks and execute them as they are distributed by the coordinator,
     * via the data store.
     */
    public void start() {
        var lastTimeWithTasks = System.currentTimeMillis();
        while (!stopRequested) {
            Set<String> activeGridTasksIds = new HashSet<>();
            var taskRequests = taskStoreManager.getTaskRequests();

            // If no more tasks after a while, abort.
            if (!taskRequests.isEmpty()) {
                lastTimeWithTasks = System.currentTimeMillis();
            } else if (System.currentTimeMillis()
                    - lastTimeWithTasks > INACTIVE_COORD_TIMEOUT.toMillis()) {
                LOG.warn("No tasks to execute for while now. Shutting down.");
                stopRequested = true;
            }

            // there are tasks: handle them
            taskRequests.forEach((id, req) -> {
                activeGridTasksIds.add(id);
                if (!terminatedTasks.containsKey(id)) {
                    if (runningTasks.containsKey(id)) {
                        updateTaskState(id, TaskState.RUNNING);
                    } else if (canExecute(req)) {
                        runningTasks.put(req.getId(), executeAsync(req));
                    } else {
                        updateTaskState(id, TaskState.PENDING);
                    }
                }
                return true;
            });

            // remove those that did not come back as running from grid
            //TODO those removed here should be used to stop zombie ones
            // on this worker if any
            CollectionUtils.subtract(runningTasks.keySet(), activeGridTasksIds)
                    .forEach(taskId -> {
                        LOG.warn("""
                            Task {} marked as no longer running by \
                            coordinator, Stopping it to avoid zombi \
                            process.""", taskId);
                        runningTasks.get(taskId).stop(grid);
                    });
            runningTasks.keySet().retainAll(activeGridTasksIds);
            terminatedTasks.keySet().retainAll(activeGridTasksIds);

            Sleeper.sleepMillis(TASK_POLL_INTERVAL.toMillis());
        }
    }

    public void stop() {
        stopRequested = true;
        runningTasks.values().forEach(task -> task.stop(grid));
    }

    private boolean canExecute(GridTaskRequest2 req) {
        return req.getExecutionMode() == ExecutionMode.ALL_NODES
                || req.getExecutionMode() == ExecutionMode.SINGLE_NODE
                        && grid.isCoordinator();
    }

    private GridTask2 executeAsync(GridTaskRequest2 req) {
        GridTask2 task = ClassUtil.newInstance(req.getTaskClass());
        Executors.newSingleThreadExecutor().submit((Runnable) () -> {
            try {
                var result = task.execute(grid);
                updateTaskProgress(
                        req.getId(),
                        new TaskProgress(new TaskExecutionResult(
                                TaskState.COMPLETED, result, null),
                                System.currentTimeMillis()));
                LOG.info("Task {} completed on node {}", req.getId(),
                        grid.getNodeName());
            } catch (Exception e) {
                updateTaskProgress(
                        req.getId(),
                        new TaskProgress(new TaskExecutionResult(
                                TaskState.FAILED, null, e.getMessage()),
                                System.currentTimeMillis()));
                LOG.error("Task {} failed on node {}",
                        req.getId(), grid.getNodeName(), e);
            } finally {
                terminatedTasks.put(req.getId(), task);
                runningTasks.remove(req.getId());
            }
        });
        return task;
    }

    private void updateTaskState(String taskId, TaskState state) {
        updateTaskProgress(taskId, new TaskProgress(
                new TaskExecutionResult(state, null, null),
                System.currentTimeMillis()));
    }

    private void updateTaskProgress(String taskId, TaskProgress progress) {
        taskStoreManager.getWorkersTaskProgress()
                .put(taskId + ":" + grid.getNodeName(), progress);
    }

    //--- ABOVE IS NEW -----------------------------

    // <taskId, ...>
    private final Map<String, TaskProgress> localTaskProgresses =
            new ConcurrentHashMap<>();

    // <taskId>
    private final Set<String> requestedTaskStops = new CopyOnWriteArraySet<>();
    // <pipelineId>
    private final Set<String> requestedPipeStops = new CopyOnWriteArraySet<>();

    // <taskId, ...>
    // Tasks that coordinator informed was done, successfully or not
    private final Map<String, TaskProgress> gridTaskProgresses =
            new ConcurrentHashMap<>();

    // <pipelineId>
    private final Map<String, TaskExecutionResult> donePipelines =
            new ConcurrentHashMap<>();

    //TODO fail fast these methods if grid is not initialized.
    //TODO have dispatcher retry a few times, taking current pipeline stage
    // into account, and/or letting the worker figure out if already outdated.

    // Remote
    public void startNodeTask(GridTask task) {
        //        if (!grid.isInitialized() && !grid.isCoordinator()) {
        //            LOG.info("Grid not yet initialized. "
        //                    + "Task {} execution will be delayed on node {}.",
        //                    task.getId(), grid.getNodeAddress());
        //            if (!ConcurrentUtil.waitUntil(
        //                    grid::isInitialized, Duration.ofMinutes(1))) {
        //                LOG.error("""
        //                    Grid took abnormally long to initialize waiting \
        //                    for task {}. Stopping the grid {} to avoid \
        //                    zombie processes.""",
        //                        task.getId(), grid.getNodeAddress());
        //                grid.stop();
        //                throw new IllegalStateException(
        //                        "Grid could not be initialized.");
        //            }
        //
        //            LOG.info("Grid initialized: task {} can resume.", task.getId());
        //
        //            // Check if task is already completed (done by other nodes).
        //            var progress = gridTaskProgresses.get(task.getId());
        //            if (progress.getStatus().getState().isTerminal()) {
        //                LOG.info("Delayed task {} already terminated, skipping it.",
        //                        task.getId());
        //                return;
        //            }
        //        }

        var taskId = task.getId();
        var nodeAddr = grid.getNodeAddress();

        // If already done or running, return now
        if (isTaskDone(task)) {
            LOG.debug("Task {} already done on node {}, skipping",
                    taskId, nodeAddr);
            return;
        }
        if (isTaskRunning(task)) {
            LOG.debug("Task {} already running on node {}, skipping",
                    taskId, nodeAddr);
            return;
        }

        LOG.info("Node {} starting task {}", nodeAddr, taskId);
        localTaskProgresses.put(task.getId(), new TaskProgress(
                new TaskExecutionResult(TaskState.RUNNING, null, null),
                System.currentTimeMillis()));

        // Schedule heartbeats at interval
        heartbeatScheduler.scheduleAtFixedRate(
                () -> updateHeartbeat(task), 0, 1, TimeUnit.SECONDS);

        // Run task asynchronously
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                var result = task.execute(grid);
                localTaskProgresses.put(taskId, new TaskProgress(
                        new TaskExecutionResult(
                                TaskState.COMPLETED, result, null),
                        System.currentTimeMillis()));
                LOG.info("Task {} completed on node {}", taskId, nodeAddr);
            } catch (Exception e) {
                localTaskProgresses.put(taskId, new TaskProgress(
                        new TaskExecutionResult(TaskState.FAILED, null,
                                e.getMessage()),
                        System.currentTimeMillis()));
                LOG.error("Task {} failed on node {}", taskId, nodeAddr, e);
            } finally {
                delayedCleanupProgress(task);
            }
        });
    }

    // Remote
    public TaskProgress getNodeTaskProgress(String taskId) {
        var progress = localTaskProgresses.getOrDefault(
                taskId, new TaskProgress(null, System.currentTimeMillis()));
        LOG.trace("Node {} returning progress for task {}: state={}, "
                + "heartbeat={}",
                grid.getNodeAddress(),
                taskId,
                progress.getResult() != null
                        ? progress.getResult().getState()
                        : "NONE (PENDING)",
                progress.getLastHeartbeat());
        return progress;
    }

    // Remote
    public void setGridTaskProgress(String taskId, TaskProgress progress) {
        gridTaskProgresses.put(taskId, progress);
        LOG.trace("Node {} received notification of {} grid task progress: {}",
                grid.getNodeAddress(), taskId, progress);
    }

    // Local
    public Optional<TaskProgress> getGridTaskProgress(String taskId) {
        return Optional.ofNullable(gridTaskProgresses.get(taskId));
    }

    // Remote
    public void stopNodeTask(String taskId) {
        requestedTaskStops.add(taskId);
        LOG.debug("Node {} received request for stopping task {}",
                grid.getNodeAddress(), taskId);
    }

    // Local
    public boolean isNodeTaskStopRequested(String taskId) {
        return requestedTaskStops.remove(taskId);
    }

    // Remote
    public void stopPipeline(String pipelineId) {
        requestedPipeStops.add(pipelineId);
        LOG.debug("Node {} received request for stopping pipeline {}",
                grid.getNodeAddress(), pipelineId);
    }

    // Local (only relevant to coordinator, who will stop pipe + all tasks)
    public boolean isPipelineStopRequested(String pipelineId) {
        return requestedPipeStops.remove(pipelineId);
    }

    // Remote
    public void clearTaskStatus(String taskId) {
        localTaskProgresses.remove(taskId);
        // don't clear other member variables
    }

    // Remote
    public void setPipelineDone(
            String pipelineId, TaskExecutionResult result) {
        donePipelines.put(pipelineId, result);
        LOG.debug("Node {} received notification of pipeline done: {}",
                grid.getNodeAddress(), pipelineId);
    }

    // Local
    public TaskExecutionResult getPipelineDone(String pipelineId) {
        // null if not done
        return donePipelines.remove(pipelineId);
    }

    //--- Private methods ------------------------------------------------------

    private boolean isTaskDone(GridTask task) {
        return ofNullable(localTaskProgresses.get(task.getId()))
                .map(TaskProgress::getResult)
                .map(TaskExecutionResult::getState)
                .filter(TaskState::isTerminal)
                .isPresent();
    }

    private boolean isTaskRunning(GridTask task) {
        return ofNullable(localTaskProgresses.get(task.getId()))
                .map(TaskProgress::getResult)
                .map(TaskExecutionResult::getState)
                .filter(TaskState::isRunning)
                .isPresent();
    }

    private void updateHeartbeat(GridTask task) {
        localTaskProgresses.computeIfPresent(task.getId(), (id, progress) -> {
            LOG.trace("Node {} storing heartbeat for task {}",
                    grid.getNodeAddress(), task.getId());
            return new TaskProgress(
                    progress.getResult(), System.currentTimeMillis());
        });
        // Also check for a stop request if one was made.
        if (isNodeTaskStopRequested(task.getId())) {
            LOG.info("Node {} received request to stop task {}. Task notified.",
                    grid.getNodeAddress(), task.getId());
            task.stop(grid);
        }
    }

    private void delayedCleanupProgress(GridTask task) {
        // Schedule cleanup after 5 minutes
        localTaskProgresses.computeIfPresent(task.getId(), (id, progress) -> {
            heartbeatScheduler.schedule(() -> {
                localTaskProgresses.remove(id);
                LOG.info("Cleaned up task {} on node {}",
                        id, grid.getNodeAddress());
            }, 300, TimeUnit.SECONDS);
            return progress;
        });
    }
}
