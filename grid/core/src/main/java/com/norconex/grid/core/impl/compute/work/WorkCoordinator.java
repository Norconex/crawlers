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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jgroups.Address;
import org.jgroups.util.Rsp;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.ExecutionMode;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.TaskProgress;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.TaskStatus;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Coordinate tasks execution across a grid.
 */
@Slf4j
public class WorkCoordinator {
    //TODO make configurable?
    private static final long POLLING_INTERVAL_MS = 2000;
    private static final long HEARTBEAT_EXPIRY_MS = 30000;
    private static final long MAX_TASK_DURATION_MS = 600000;

    private final WorkDispatcher dispatcher;
    private final Worker localWorker;
    private final CoreGrid grid;

    // MAYBE: move somewhere more generic, like former ComputeStateStore?
    // Make sure to use it or remove entirely. Likely still needed
    // for session/resumes.  Should be reset when session is reset.
    private final GridMap<TaskState> taskStateStore;

    public WorkCoordinator(CoreGrid grid) {
        this.grid = grid;
        localWorker = new Worker(grid);
        dispatcher = new WorkDispatcher(grid, localWorker);
        taskStateStore =
                grid.getStorage().getMap("task-state", TaskState.class);
    }

    public TaskStatus executeTask(GridTask task) throws Exception {
        //TODO, not a supported use case yet, but if joining remotely to
        // send an execution request, we'll have to make sure the coordinator
        // gets it.
        if (!grid.isCoordinator()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("""
                    Non-coordinator node {}. Letting the coordinator node \
                    start task {} and this node {}""",
                        grid.getNodeAddress(), task.getId(),
                        task.getExecutionMode() == ExecutionMode.SINGLE_NODE
                                ? "will wait for the coordinator to be done "
                                        + "with this single-node task."
                                : "will participate when asked by the "
                                        + "coordinator.");
            }
            return awaitCoordinatorDoneSignal(task);
        }

        var state = ofNullable(taskStateStore
                .get(task.getId())).orElse(TaskState.PENDING);

        // Ensure runOnce is respected
        if (task.isOnce() && state.isTerminal()) {
            LOG.error("Task {} is marked to run once, but already ran in this "
                    + "grid session with status: {}. Failing it.",
                    task.getId(), state);
            var taskStatus = new TaskStatus(
                    TaskState.FAILED,
                    null,
                    "Task already ran in this grid session with state: "
                            + state);
            dispatcher.setGridTaskProgressOnNodes(task.getId(),
                    new TaskProgress(taskStatus, System.currentTimeMillis()));
            return taskStatus;
        }

        // A coordinator can't start a job already running but can take over
        // if newly elected
        if (grid.isCoordinator() && state.isRunning()) {
            LOG.warn("""
                A coordinator {} tried to start a task already running: {}. \
                Could be newly elected. Will attempt to take over \
                instead.""", grid.getNodeAddress(), task.getId());
        } else {
            LOG.info("Coordinator {} executing task: {}",
                    grid.getNodeAddress(), task.getId());
            // Dispatch task to worker nodes
            dispatcher.startTaskOnNodes(task);
        }

        return trackAndAggregateResult(task);
    }

    public void stopTask(String taskId) throws Exception {
        // Since we don't know which node may ask for stopping, we
        // don't check if we are coordinator and we let all broadcast to all

        //TODO consider sending the request to coord first if not the coord.
        // or have the STOPPED status saved in store and check
        // if running first (or just not "done").
        // Or have STOPPING and STOPPED and don't broadcase if already
        // "STOPPING" as it would mean someone already broadcasted.
        dispatcher.stopTaskOnNodes(taskId);
    }

    private TaskStatus awaitCoordinatorDoneSignal(GridTask task) {
        var startTime = new AtomicLong(System.currentTimeMillis());
        var taskStatusRef = new AtomicReference<TaskStatus>();
        ConcurrentUtil.waitUntil(() -> {
            if (localWorker.isNodeTaskStopRequested(task.getId())) {
                LOG.info("""
                    Node {} received a request to stop the \
                    task {} while waiting for coordinator signal. \
                    Stopping (no longer waiting).""",
                        grid.getNodeAddress(), task.getId());
                taskStatusRef.set(new TaskStatus(
                        TaskState.COMPLETED, null, null));
                return true;
            }

            var gridProgress = localWorker.getGridTaskProgress(
                    task.getId()).orElse(null);
            if (gridProgress == null) {
                return false;
            }
            var heartbeat = gridProgress.getLastHeartbeat();
            if (heartbeat - startTime.get() > HEARTBEAT_EXPIRY_MS) {
                throw new GridException("Task expired. No hearbeat "
                        + "received from coordinator.");
            }
            var status = gridProgress.getStatus();
            if (status == null) {
                return false;
            }

            var state = status.getState();
            if (state == null) {
                return false;
            }

            if (state.isTerminal()) {
                taskStatusRef.set(status);
                if (taskStatusRef.get() == null) {
                    LOG.warn("No task status from coordinator.");
                }
                return true;
            }
            return false;
        });
        return taskStatusRef.get();
    }

    private TaskStatus trackAndAggregateResult(GridTask task) throws Exception {

        taskStateStore.put(task.getId(), TaskState.RUNNING);

        var agg = new AggregatedContext();

        // mark the starting status for all nodes to be PENDING
        grid.getGridMembers().forEach(addr -> agg.lastProgresses.put(
                addr, new TaskProgress(new TaskStatus(), agg.taskStartTime)));

        //TODO remove `&& !agg.taskExpired()` or allow infinite?
        while (!agg.allDone && !agg.taskExpired()) {
            var pendingNodes = getPendingNodes(agg.doneNodes);
            if (pendingNodes.isEmpty()) {
                agg.allDone = true;
                break;
            }

            trackProgress(agg, task, pendingNodes);
            notifyNodesOfGridProgressOrAggregate(agg, task, false);

            if (!agg.allDone) {
                Thread.sleep(POLLING_INTERVAL_MS);
            }

        }

        try {
            return notifyNodesOfGridProgressOrAggregate(agg, task, true);
        } finally {
            try {
                dispatcher.clearTaskStatusOnNodes(task.getId());
            } catch (Exception e) {
                LOG.error("Could not clear task status on nodes for task {}",
                        task.getId(), e);
            }
        }
    }

    private TaskStatus notifyNodesOfGridProgressOrAggregate(
            AggregatedContext agg, GridTask task, boolean coordDone)
            throws Exception {
        TaskStatus status = null;
        if (!agg.allDone) {
            if (coordDone) {
                var err = "Task %s timed out after %s seconds."
                        .formatted(task.getId(), MAX_TASK_DURATION_MS / 1000);
                LOG.error(err);
                taskStateStore.put(task.getId(), TaskState.FAILED);
                status = new TaskStatus(TaskState.FAILED, null, err);
            } else {
                status = new TaskStatus(TaskState.RUNNING, null, null);
            }
        } else {
            status = task.aggregate(
                    new ArrayList<>(agg.lastProgresses.values()));
            taskStateStore.put(task.getId(),
                    status != null ? status.getState() : TaskState.FAILED);
        }
        dispatcher.setGridTaskProgressOnNodes(task.getId(),
                new TaskProgress(status, System.currentTimeMillis()));
        return status;
    }

    private void trackProgress(
            AggregatedContext agg, GridTask task, Set<Address> pendingNodes)
            throws Exception {

        var progressList = dispatcher.getTaskProgressFromNodes(
                task.getId(), pendingNodes);

        for (Map.Entry<Address, Rsp<TaskProgress>> entry : progressList
                .entrySet()) {
            var srcNode = entry.getKey();
            var rsp = entry.getValue();
            var progress = rsp.getValue();
            var status = progress != null ? progress.getStatus() : null;
            System.err.println("PROGRESS: " + srcNode + ": " + progress);
            agg.lastProgresses.put(srcNode, progress);

            // check if node expired
            if (hasNodeExpired(agg, progress)) {
                agg.doneNodes.add(srcNode);
                LOG.error("Node {} expired - No heartbeat received.",
                        grid.getNodeAddress());
                continue;
            }

            if (isGoodNodeResponse(rsp)) {
                if (status.getState() == TaskState.COMPLETED) {
                    LOG.debug("Task {} completed on node {}",
                            task.getId(), grid.getNodeAddress());
                    agg.doneNodes.add(srcNode);
                } else {
                    agg.allDone = false; // needed? false by default.
                }
            } else {
                handleError(agg, rsp, progress, srcNode);
                agg.doneNodes.add(srcNode);
            }
        }
    }

    private void handleError(
            AggregatedContext agg,
            Rsp<TaskProgress> rsp,
            TaskProgress progress,
            Address srcNode) {
        var status = progress == null ? null : progress.getStatus();
        var heartbeat = progress == null ? 0 : progress.getLastHeartbeat();
        String error;
        if (status != null && status.getError() != null) {
            error = status.getError();
        } else if (rsp.hasException()) {
            error = ExceptionUtil.getFormattedMessages(rsp.getException());
        } else {
            error = "No response or failed RPC";
        }
        LOG.error(error);
        if (!isState(progress, TaskState.FAILED)) {
            agg.lastProgresses.put(srcNode, new TaskProgress(
                    new TaskStatus(TaskState.FAILED, null, error),
                    heartbeat));
        }
    }

    private boolean isGoodNodeResponse(Rsp<TaskProgress> rsp) {
        return rsp.wasReceived()
                && !rsp.hasException()
                && !rsp.wasSuspected()
                && !rsp.wasUnreachable()
                && rsp.getValue() != null;
    }

    private boolean hasNodeExpired(AggregatedContext agg,
            TaskProgress progress) {
        var lastHeartbeat = progress != null
                ? progress.getLastHeartbeat()
                : agg.taskStartTime;
        return System.currentTimeMillis()
                - lastHeartbeat > HEARTBEAT_EXPIRY_MS;
    }

    private boolean isState(TaskProgress progress, TaskState state) {
        return ofNullable(progress)
                .map(TaskProgress::getStatus)
                .map(TaskStatus::getState)
                .filter(s -> s == state)
                .isPresent();
    }

    private Set<Address> getPendingNodes(Set<Address> completedNodes) {
        return grid.getGridMembers().stream()
                .filter(addr -> !completedNodes.contains(addr))
                .collect(Collectors.toSet());
    }

    private static class AggregatedContext {
        private final Map<Address, TaskProgress> lastProgresses =
                new HashMap<>();
        private final Set<Address> doneNodes = new HashSet<>();
        private final long taskStartTime = System.currentTimeMillis();
        private boolean allDone;

        long taskElapsed() {
            return System.currentTimeMillis() - taskStartTime;
        }

        boolean taskExpired() {
            return taskElapsed() >= MAX_TASK_DURATION_MS;
        }
    }
}
