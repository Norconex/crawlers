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
package com.norconex.grid.core.impl.compute.task;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jgroups.Address;
import org.jgroups.util.Rsp;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.ExecutionMode;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.GridTaskRequest2;
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.CoreCompute;
import com.norconex.grid.core.impl.compute.WorkDispatcher;
import com.norconex.grid.core.impl.compute.Worker;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinate tasks execution across a grid.
 */
@Slf4j
public class TaskCoordinator {

    private static final Duration WORKER_PROGRESS_POLL_INTERVAL =
            Duration.ofSeconds(2);

    /**
     * Amount of time without any workers giving signs of life
     * after which we consider the grid stale and abort.
     */
    private static final Duration INACTIVE_WORKERS_TIMEOUT =
            Duration.ofMinutes(2);

    private final WorkDispatcher dispatcher;
    private final Worker localWorker;
    private final CoreGrid grid;
    private final Duration taskTimeout;

    // MAYBE: move somewhere more generic, like former ComputeStateStore?
    // Make sure to use it or remove entirely. Likely still needed
    // for session/resumes.  Should be reset when session is reset.
    private final GridMap<TaskState> taskStateStore;

    private final TaskStoreManager2 taskStoreManager;

    // <taskId, ...>
    private Map<String, ResultsByNode> workersProgress =
            Collections.unmodifiableMap(new HashMap<>());

    private static class ResultsByNode {
        // <nodeId, ...>
        private final Map<String, TaskProgress> results = new HashMap<>();
    }

    /**
     * Periodically cache node task progress from DB for quick access.
     */
    private final ScheduledExecutorService workerProgressSyncScheduler =
            Executors.newScheduledThreadPool(1);

    public TaskCoordinator(CoreCompute compute) {
        grid = compute.getGrid();
        taskStoreManager = compute.getTaskStoreManager();
        localWorker = compute.getLocalWorker();
        dispatcher = compute.getDispatcher();
        taskStateStore =
                grid.getStorage().getMap("task_state", TaskState.class);
        taskTimeout = grid.getConnectorConfig().getTaskTimeout();

        // Schedule heartbeats at interval
        //TODO shut it down when done?
        workerProgressSyncScheduler.scheduleAtFixedRate(
                this::syncWorkersProgress, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Mark this coordinator as ready to manage tasks. It will start monitoring
     * to worker progress and keep overall state in sync.
     */
    public void start() {

    }

    private void syncWorkersProgress() {
        var workProg = new HashMap<String, ResultsByNode>();
        taskStoreManager.getWorkersTaskProgress().forEach((key, progress) -> {
            var tskId = StringUtils.substringBefore(key, ":");
            var nodeId = StringUtils.substringAfter(key, ":");
            var resultsByNode = workProg.computeIfAbsent(
                    tskId, id -> new ResultsByNode());
            resultsByNode.results.put(nodeId, progress);
            return true;
        });
        workersProgress = workProg;
    }

    //NOTE: if resuming, tasks will pickup what is still running
    // pipeline will be also be initialized properly when resuming

    public void planTaskExecution2(GridTaskRequest2 taskReq) {

        // Ignore request if not the coordinator
        if (!grid.isCoordinator()) {
            return;
        }

        // Ignore request if it is already being executed (or planned to be).
        if (taskStoreManager.getTaskRequests().contains(taskReq.getId())) {
            LOG.error("Received a request for task {} while it is already "
                    + "being executed.");
        }

        // Ignore request if marked to run "once" and already ran
        var taskExecResult =
                taskStoreManager.getTerminatedTasks().get(taskReq.getId());
        if (taskReq.isOnce() && taskExecResult != null) {
            LOG.error("Task {} is marked to run once, but already ran in this "
                    + "grid session with status: {}.", taskReq.getId(),
                    taskExecResult.getState());
        }

        // Store the request for workers to pick up.
        taskStoreManager.getTaskRequests().put(taskReq.getId(), taskReq);

        //TODO track execution separately and remove it from taskRequests
        // and add it to terminatedTasks.

    }

    public TaskExecutionResult awaitTaskExecutionResult(String taskId) {

        // if already executed, return it
        var taskResult = taskStoreManager.getTerminatedTasks().get(taskId);
        if (taskResult != null) {
            LOG.info("Task {} already ran with status: {}", taskId,
                    taskResult.getState());
            return taskResult;
        }

        var lastWorkerActivity = System.currentTimeMillis();

        while (System.currentTimeMillis()
                - lastWorkerActivity < INACTIVE_WORKERS_TIMEOUT.toMillis()) {

            var hasNonTerminal = false;
            TaskProgress completed = null;
            TaskProgress failed = null;

            // if all nodes are in terminal state or expired,
            // we consider the whole clustered task to be terminated
            var resultsByNode = workersProgress.get(taskId);
            for (Map.Entry<String, TaskProgress> entry : resultsByNode.results
                    .entrySet()) {
                entry.getKey();
                var progress = entry.getValue();
                if (progress.getLastHeartbeat() > lastWorkerActivity) {
                    lastWorkerActivity = progress.getLastHeartbeat();
                }
                var state = progress.getResult().getState();
                if (state.isAny(TaskState.RUNNING, TaskState.PENDING)) {
                    hasNonTerminal = true;
                } else if (state == TaskState.FAILED) {
                    failed = progress;
                } else if (state == TaskState.COMPLETED) {
                    completed = progress;
                }
            }

            if (!hasNonTerminal) {
                if (completed != null) {
                    return completed.getResult();
                }
                if (failed != null) {
                    return failed.getResult();
                }
            }

            Sleeper.sleepSeconds(1);
        }

        return new TaskExecutionResult(TaskState.FAILED, null, "Expired.");
    }

    //--- ABOVE IS NEW --------------------

    public TaskExecutionResult executeTask(GridTask task) throws Exception {
        //TODO, not a supported use case yet, but if joining remotely to
        // send an execution request, we'll have to make sure the coordinator
        // gets it.

        //NOTE: the reason we return right away if not the coordinator is
        // because workers tasks are invoked directly on workers by the
        // coordinator.
        if (!grid.isCoordinator()) {
            TaskUtil.logNonCoordinatorCantExecute(task);
            //TODO verify if this will get called even if joining,
            // late, after the task is executed... will it receive remote task
            // request in parallel?
            return awaitCoordinatorDoneSignal(task);
        }

        var state = getStoredTaskState(task.getId());

        // Ensure runOnce is respected
        if (task.isOnce() && state.isTerminal()) {
            LOG.error("Task {} is marked to run once, but already ran in this "
                    + "grid session with status: {}. Failing it.",
                    task.getId(), state);
            var taskStatus = new TaskExecutionResult(
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
                Could be newly elected. Will attempt to take over.""",
                    grid.getNodeAddress(), task.getId());
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

    private TaskState getStoredTaskState(String taskId) {
        return ofNullable(taskStateStore
                .get(taskId)).orElse(TaskState.PENDING);
    }

    private TaskExecutionResult awaitCoordinatorDoneSignal(GridTask task) {
        var taskStatusRef = new AtomicReference<TaskExecutionResult>();
        ConcurrentUtil.waitUntil(() -> {
            if (localWorker.isNodeTaskStopRequested(task.getId())) {
                LOG.info("""
                    Node {} received a request to stop the \
                    task {} while waiting for a coordinator signal. \
                    Stopping (no longer waiting).""",
                        grid.getNodeAddress(), task.getId());
                taskStatusRef.set(new TaskExecutionResult(
                        TaskState.COMPLETED, null, null));
                return true;
            }

            var gridProgress = localWorker.getGridTaskProgress(
                    task.getId()).orElse(null);
            if (gridProgress == null) {
                return false;
            }
            var heartbeat = gridProgress.getLastHeartbeat();

            if (System.currentTimeMillis() - heartbeat > grid
                    .getConnectorConfig()
                    .getNodeTimeout().toMillis()) {
                throw new GridException(
                        "Task expired. No hearbeat received from coordinator.");
            }
            var status = gridProgress.getResult();
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

    private TaskExecutionResult trackAndAggregateResult(GridTask task)
            throws Exception {

        taskStateStore.put(task.getId(), TaskState.RUNNING);

        var agg = new AggregatedContext(taskTimeout);

        // mark the starting status for all nodes to be PENDING
        grid.getGridMembers().forEach(addr -> agg.lastProgresses.put(
                addr, new TaskProgress(new TaskExecutionResult(),
                        agg.taskStartTime)));

        while (!agg.allDone && !agg.taskExpired()) {
            var pendingNodes = getPendingNodes(task, agg.doneNodes);
            if (pendingNodes.isEmpty()) {
                agg.allDone = true;
                break;
            }

            trackProgress(agg, task, pendingNodes);
            notifyNodesOfGridProgressOrAggregate(agg, task, false);

            if (!agg.allDone) {
                Thread.sleep(grid.getConnectorConfig().getHeartbeatInterval()
                        .toMillis());
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

    private TaskExecutionResult notifyNodesOfGridProgressOrAggregate(
            AggregatedContext agg, GridTask task, boolean coordDone)
            throws Exception {
        TaskExecutionResult status = null;
        if (!agg.allDone) {
            // if not all nodes are done but the coordinator is, it likely
            // means a task has expired.
            if (coordDone) {
                String err;
                if (agg.taskExpired()) {
                    err = "Task %s timed out after %s seconds."
                            .formatted(task.getId(), taskTimeout.toSeconds());
                } else {
                    err = """
                        Task controller reported being done while some \
                        nodes are still executing. This should not \
                        happen. Failing the task.""";
                }
                LOG.error(err);
                taskStateStore.put(task.getId(), TaskState.FAILED);
                status = new TaskExecutionResult(TaskState.FAILED, null, err);
            } else {
                status = new TaskExecutionResult(TaskState.RUNNING, null, null);
            }
        } else {
            status = task.aggregate(new ArrayList<>(agg.lastProgresses
                    .values()
                    .stream()
                    .map(TaskProgress::getResult).toList()));
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
            agg.lastProgresses.put(srcNode, progress);
            var status = progress != null ? progress.getResult() : null;

            if (status == null) {
                // The status is set on remote nodes the moment task execution
                // is invoked. If null, it likely means a new node has joined
                // and we need to resend the command so it knows what to do.
                dispatcher.startTaskOnNode(task, srcNode);
            }

            // check if node expired
            if (TaskUtil.hasNodeTaskExpired(
                    agg.taskStartTime,
                    progress,
                    grid.getConnectorConfig().getNodeTimeout())) {
                agg.doneNodes.add(srcNode);
                LOG.error("Node {} expired - No heartbeat received.",
                        grid.getNodeAddress());
                continue;
            }

            if (TaskUtil.isValidNodeResponse(rsp)) {
                if (status != null
                        && status.getState().isTerminal()) {
                    LOG.debug("Task {} done on node {} with state: {}",
                            task.getId(),
                            grid.getNodeAddress(),
                            status.getState());
                    agg.doneNodes.add(srcNode);
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
        var status = progress == null ? null : progress.getResult();
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
        if (!TaskUtil.isState(progress, TaskState.FAILED)) {
            agg.lastProgresses.put(srcNode, new TaskProgress(
                    new TaskExecutionResult(TaskState.FAILED, null, error),
                    heartbeat));
        }
    }

    private Set<Address> getPendingNodes(
            GridTask task, Set<Address> doneNodes) {
        var workers = task.getExecutionMode() == ExecutionMode.SINGLE_NODE
                ? List.of(grid.getCoordAddress())
                : grid.getGridMembers();
        return workers.stream()
                .filter(addr -> !doneNodes.contains(addr))
                .collect(Collectors.toSet());
    }

    @RequiredArgsConstructor
    static class AggregatedContext {
        private final Map<Address, TaskProgress> lastProgresses =
                new HashMap<>();
        private final Set<Address> doneNodes = new HashSet<>();
        final long taskStartTime = System.currentTimeMillis();
        private final Duration taskTimeout;
        private boolean allDone;

        long taskElapsed() {
            return System.currentTimeMillis() - taskStartTime;
        }

        boolean taskExpired() {
            if (taskTimeout == null) {
                return false;
            }
            return taskElapsed() >= taskTimeout.toMillis();
        }
    }
}
