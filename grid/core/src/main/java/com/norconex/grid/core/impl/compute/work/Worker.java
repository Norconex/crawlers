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
package com.norconex.grid.core.impl.compute.work;

import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.compute.TaskStatus;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.TaskProgress;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes tasks on a node, keep track of their status, and more.
 * Meant to be invoked remotely when a task is run on all nodes or targetting
 * this worker node.
 */
// A local instance is bound to the grid in the WorkDispatcher
@Slf4j
public class Worker {

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
    private final Set<String> donePipelines = new CopyOnWriteArraySet<>();

    private final CoreGrid grid;

    /**
     * Store local heartbeat so coordinator knows how recent it did something
     * when pulling for info.
     */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(1);

    public Worker(CoreGrid grid) {
        this.grid = grid;
    }

    // Remote
    public void startNodeTask(GridTask task) {
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
                new TaskStatus(TaskState.RUNNING, null, null),
                System.currentTimeMillis()));

        // Schedule heartbeats at interval
        heartbeatScheduler.scheduleAtFixedRate(
                () -> updateHeartbeat(task), 0, 1, TimeUnit.SECONDS);

        // Run task asynchronously
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                var result = task.execute(grid.getGridContext());
                localTaskProgresses.put(taskId, new TaskProgress(
                        new TaskStatus(TaskState.COMPLETED, result, null),
                        System.currentTimeMillis()));
                LOG.info("Task {} completed on node {}", taskId, nodeAddr);
            } catch (Exception e) {
                localTaskProgresses.put(taskId, new TaskProgress(
                        new TaskStatus(TaskState.FAILED, null, e.getMessage()),
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
        LOG.debug("Node {} returning progress for task {}: state={}, "
                + "heartbeat={}",
                grid.getNodeAddress(),
                taskId,
                progress.getStatus() != null
                        ? progress.getStatus().getState()
                        : "NONE (PENDING)",
                progress.getLastHeartbeat());
        return progress;
    }

    // Remote
    public void setGridTaskProgress(String taskId, TaskProgress progress) {
        gridTaskProgresses.put(taskId, progress);
        LOG.debug("Node {} received notification of {} grid task progress: {}",
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
    public void setPipelineDone(String pipelineId) {
        donePipelines.add(pipelineId);
        LOG.debug("Node {} received notification of pipeline done: {}",
                grid.getNodeAddress(), pipelineId);
    }

    // Local
    public boolean isPipelineDone(String pipelineId) {
        return donePipelines.remove(pipelineId);
    }

    //--- Private methods ------------------------------------------------------

    private boolean isTaskDone(GridTask task) {
        return ofNullable(localTaskProgresses.get(task.getId()))
                .map(TaskProgress::getStatus)
                .map(TaskStatus::getState)
                .filter(TaskState::isTerminal)
                .isPresent();
    }

    private boolean isTaskRunning(GridTask task) {
        return ofNullable(localTaskProgresses.get(task.getId()))
                .map(TaskProgress::getStatus)
                .map(TaskStatus::getState)
                .filter(TaskState::isRunning)
                .isPresent();
    }

    private void updateHeartbeat(GridTask task) {
        localTaskProgresses.computeIfPresent(task.getId(), (id, progress) -> {
            LOG.debug("Node {} storing heartbeat for task {}",
                    grid.getNodeAddress(), task.getId());
            return new TaskProgress(
                    progress.getStatus(), System.currentTimeMillis());
        });
        // Also check for a stop request if one was made.
        if (isNodeTaskStopRequested(task.getId())) {
            LOG.info("Node {} received request to stop task {}. Task notified.",
                    grid.getNodeAddress(), task.getId());
            task.stop();
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
