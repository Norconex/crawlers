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
package com.norconex.grid.core.impl.compute.worker;

import java.util.concurrent.CompletableFuture;

import com.norconex.grid.core.compute.GridCompute.RunOn;
import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.compute.GridComputeTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.coord.GridTaskCoordinator;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents one node running a job as part of a multi-nodes job.
 * There should be at most one node task worker with a given task name
 * running at any given time within the same grid node instance.
 */
@Slf4j
@Getter
public class NodeTaskWorker {
    private final CoreGrid grid;
    private final String taskName;
    private final RunOn runOn;

    public NodeTaskWorker(CoreGrid grid, String taskName, RunOn runOn) {
        this.grid = grid;
        this.taskName = taskName;
        this.runOn = runOn;
    }

    public <T> GridComputeResult<T> run(GridComputeTask<T> task) {

        var prevOrCurrentState = grid.computeStateStorage()
                .getComputeState(taskName).orElse(GridComputeState.IDLE);
        if (!okToRun(prevOrCurrentState)) {
            return new GridComputeResult<T>().setState(prevOrCurrentState);
        }

        var pendingGridResult = new CompletableFuture<GridComputeResult<T>>();
        @SuppressWarnings("unchecked")
        var gridResultListener = grid.taskPayloadMessenger()
                .addTaskMessageListener(taskName, GridComputeResult.class,
                        (payload, from) -> pendingGridResult.complete(
                                (GridComputeResult<T>) payload));

        var taskCoordinator =
                grid.isCoordinator() ? new GridTaskCoordinator(grid, taskName)
                        : null;

        try {
            if (taskCoordinator != null) {
                LOG.info("Starting task coordinator...");
                grid.getNodeExecutors().runLongTask(
                        "grid-task-coord", taskCoordinator);
            }

            NodeTaskLock.runExclusively(grid, taskName, () -> {
                new NodeTaskLifeCycle(this).run(task);
                // wait for coordinator to signal all nodes are done.
                LOG.info("Node \"{}\" is done with job \"{}\". "
                        + "Awaiting coordinator signal to proceed.",
                        grid.getLocalAddress(), taskName);

            });

            //MAYBE: have a timeout since all nodes should finish close
            // to each other in most cases?
            return ConcurrentUtil.get(pendingGridResult);

        } finally {
            if (taskCoordinator != null) {
                LOG.info("Stopping task coordinator...");
                taskCoordinator.stop();
            }
            grid.removeListener(gridResultListener);
        }

    }

    private boolean okToRun(GridComputeState state) {
        // A coordinator can't run a job already running.
        if (grid.isCoordinator() && state.isRunning()) {
            LOG.warn("A coordinator node tried to run a job already running.");
            return false;
        }
        // Ensure runOnce is respected
        if (runOn.isOnce() && state.hasRan()) {
            LOG.warn("Ignoring request to run job \"{}\" ONCE as it already "
                    + "ran in this crawl session with status: \"{}\".",
                    taskName, state);
            return false;
        }
        return true;
    }

}
