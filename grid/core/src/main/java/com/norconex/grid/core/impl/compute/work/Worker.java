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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.TaskProgress;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.TaskStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes tasks on a node, keep track of their status, and more.
 * Meant to be invoked remotely when a task is run on all nodes or targetting
 * this worker node.
 */
@Slf4j
public class Worker {

    // <taskId, ...>
    private final Map<String, TaskProgress> localTaskProgresses =
            new ConcurrentHashMap<>();

    // <taskId, ...>
    // Tasks that coordinator informed was done, successfully or not
    private final Map<String, TaskProgress> gridTaskProgresses =
            new ConcurrentHashMap<>();

    // A local instance is bound to the grid in the WorkDispatcher

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

        // If already completed, return now
        if (isTaskDone(task)) {
            LOG.debug("Task {} already done on node {}, skipping",
                    taskId, nodeAddr);
            return;
        }

        LOG.info("Node {} starting task {}", nodeAddr, taskId);
        localTaskProgresses.put(task.getId(), new TaskProgress(
                new TaskStatus(TaskState.RUNNING, null, null),
                System.currentTimeMillis()));

        // Schedule heartbeats every 5 seconds
        heartbeatScheduler.scheduleAtFixedRate(
                () -> updateHeartbeat(task), 0, 5, TimeUnit.SECONDS);

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
                cleanupProgress(task);
            }
        });
    }

    // Remote
    public TaskProgress getNodeTaskProgress(String taskId) {
        var progress = localTaskProgresses.getOrDefault(
                taskId, new TaskProgress(null, 0L));
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

    //--- Private methods ------------------------------------------------------

    private boolean isTaskDone(GridTask task) {
        return ofNullable(localTaskProgresses.get(task.getId()))
                .map(TaskProgress::getStatus)
                .map(TaskStatus::getState)
                .filter(TaskState::isTerminal)
                .isPresent();
    }

    private void updateHeartbeat(GridTask task) {
        localTaskProgresses.computeIfPresent(task.getId(), (id, progress) -> {
            LOG.debug("Node {} storing heartbeat for task {}",
                    grid.getNodeAddress(), task.getId());
            return new TaskProgress(
                    progress.getStatus(), System.currentTimeMillis());
        });
    }

    private void cleanupProgress(GridTask task) {
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
