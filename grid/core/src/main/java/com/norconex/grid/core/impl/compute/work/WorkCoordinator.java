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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.jgroups.Address;
import org.jgroups.util.Rsp;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.TaskProgress;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.TaskStatus;
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

    public WorkCoordinator(CoreGrid grid) {
        this.grid = grid;
        localWorker = new Worker(grid);
        dispatcher = new WorkDispatcher(grid, localWorker);
    }

    public void executeTask(GridTask task) throws Exception {
        if (!grid.isCoordinator()) {
            LOG.debug("Non-coordinator node {}, ignoring task {} and "
                    + "waiting for signal from coordinator to move on.",
                    grid.getNodeAddress(), task.getId());
            awaitCoordinatorSignal(task);
            return;
        }

        LOG.info("Coordinator {} executing task: {}",
                grid.getNodeAddress(), task.getId());

        // Dispatch task to worker nodes
        dispatcher.startTaskOnNodes(task);

        trackAndAggregateResult(task);
    }

    private void awaitCoordinatorSignal(GridTask task) {
        var startTime = new AtomicLong(System.currentTimeMillis());
        ConcurrentUtil.waitUntil(() -> {
            var opt = localWorker.getGridTaskProgress(task.getId());
            if (opt.isEmpty()) {
                return false;
            }
            long heartbeat =
                    opt.map(TaskProgress::getLastHeartbeat).orElse(0L);
            if (heartbeat - startTime.get() > HEARTBEAT_EXPIRY_MS) {
                throw new GridException("Task expired. No hearbeat "
                        + "received from coordinator.");
            }
            var stateOpt =
                    opt.map(TaskProgress::getStatus).map(TaskStatus::getState);
            if (stateOpt.isEmpty()) {
                return false;
            }
            return stateOpt.map(TaskState::isTerminal).orElse(false);
        });
    }

    private void trackAndAggregateResult(GridTask task) throws Exception {

        //TODO ^^^^^^&&&&&*****~~~ store RUNNING in store

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
            notifyNodesOfGridProgress(agg, task, false);

            //            //STORE on the coord worker node
            //            grid.getCompute().getWorker().getTaskManager()
            //                    .storePartialResults(taskId, aggregated.toString());
            //            //            taskManager.storePartialResults(taskId, aggregated.toString());
            //
            //            //store on other/all worker nodes
            //            dispatcher.receivePartialResults(taskId, aggregated.toString());

            if (!agg.allDone) {
                Thread.sleep(POLLING_INTERVAL_MS);
            }

        }

        notifyNodesOfGridProgress(agg, task, true);

        //        //NOTE: for now, we need only one COMPLETED to be a success.
        //        for (TaskProgress progress : agg.lastProgresses.values()) {
        //
        //        }
        //        if (agg.lastProgresses.values().stream().allMatch(prog -> {
        //            prog.getStatus().getState()
        //        }))

        //        agg.lastProgresses.forEach((addr, prog) -> {
        //            System.err.println("XXX " + addr + " -> " + prog.getStatus());
        //        });

        //        dispatcher.receivePartialResults(taskId, aggregated.toString());

        //TODO make sure we can get results before clearing??
        //        dispatcher.clearTaskStatus(taskId);
        //        taskManager.clearTask(taskId);

    }

    private void notifyNodesOfGridProgress(
            AggregatedContext agg, GridTask task, boolean coordDone)
            throws Exception {
        TaskStatus status = null;
        if (!agg.allDone) {
            if (coordDone) {
                var err = "Task %s timed out after %s seconds."
                        .formatted(task.getId(), MAX_TASK_DURATION_MS / 1000);
                LOG.error(err);
                //TODO ^^^^^^&&&&&*****~~~ store FAILED in store
                status = new TaskStatus(TaskState.FAILED, null, err);
            } else {
                status = new TaskStatus(TaskState.RUNNING, null, null);
            }
        } else {
            //NOTE: for now, we need only one COMPLETED to be a success.
            for (TaskProgress progress : agg.lastProgresses.values()) {
                status = progress.getStatus();
                if (status.getState() == TaskState.COMPLETED) {
                    break;
                }
            }
            //TODO ^^^^^^&&&&&*****~~~ store STATE in store
        }
        dispatcher.setGridTaskProgressOnNodes(task.getId(),
                new TaskProgress(status, System.currentTimeMillis()));
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
        //TODO string for now, but store Status object possibly with results
        // if we make methods return values.
        //        private final StringBuilder xaggregated = new StringBuilder();

        //        private final Map<Address, Long> lastHeartbeats = new HashMap<>();
        private final Map<Address, TaskProgress> lastProgresses =
                new HashMap<>();
        // need this one:?? can be derived from above
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
