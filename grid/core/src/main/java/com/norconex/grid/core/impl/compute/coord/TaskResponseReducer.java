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
package com.norconex.grid.core.impl.compute.coord;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jgroups.Address;

import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.worker.NodeTaskResult;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class TaskResponseReducer implements AutoCloseable {
    private static final long NODE_JOB_TIMEOUT_MS = 30_000;

    private final Map<Address, NodeTaskResult> workerResults =
            new ConcurrentHashMap<>();

    private final CoreGrid grid;
    private MessageListener listener;

    public static void map(CoreGrid grid, String taskName,
            Consumer<TaskResponseReducer> consumer) {

        try (var reducer = new TaskResponseReducer(grid)) {
            reducer.listener =
                    grid.taskPayloadMessenger().addTaskMessageListener(
                            taskName, NodeTaskResult.class, (payload, from) -> {
                                reducer.workerResults.put(from,
                                        (NodeTaskResult) payload);
                            });
            consumer.accept(reducer);
        }
    }

    //TODO make configurable to throw/stop on any node failure
    // or make sure to recover
    <T> GridComputeResult<T> reduce() {
        var defaultResult = new GridComputeResult<T>()
                .setState(GridComputeState.RUNNING);

        //TODO have a timeout that throws if we keep missing workers
        // For a little bit while the nodes are warming up that may be
        // fine but more than that we should throw.
        if (!workerResults.keySet().containsAll(grid.getClusterMembers())) {
            logWorkerStates();
            return defaultResult;
        }

        // at this point we have all nodes reporting... check global state,
        // considering complete if all ran and at least one succeeded.
        // (for now... maybe make configurable or implement fail-over).
        var gridResult = defaultResult;
        for (var entry : workerResults.entrySet()) {
            var node = entry.getKey();
            var nodeResult = entry.getValue();
            var nodeState = nodeResult.getState();
            if (nodeState.hasRan()
                    && nodeState.ordinal() > gridResult.getState()
                            .ordinal()) {
                gridResult = toGridResult(nodeResult);
            }

            if (nodeResult.elapsed() > NODE_JOB_TIMEOUT_MS) {
                LOG.warn("Node \"{}\" seems to have expired. "
                        + "No signal since {} seconds ago.", node,
                        Duration.ofMillis(nodeResult.elapsed())
                                .toSeconds());
            }

            // if still running and not expired, return right away
            // as we know the session is not done
            if (!nodeState.hasRan()) {
                logWorkerStates();
                return gridResult;
            }
        }
        return gridResult;
    }

    @Override
    public void close() {
        workerResults.clear();
        grid.removeListener(listener);
    }

    @SuppressWarnings("unchecked")
    private <T> GridComputeResult<T> toGridResult(NodeTaskResult nodeResult) {
        return new GridComputeResult<T>()
                .setExceptionStack(nodeResult.getExceptionStack())
                .setState(nodeResult.getState())
                .setValue((T) nodeResult.getValue());
    }

    private void logWorkerStates() {
        if (LOG.isTraceEnabled()) {
            var b = new StringBuilder();
            b.append("All node states:");
            workerResults.forEach((addr, result) -> b
                    .append("\n    " + addr + " -> " + result));
            LOG.trace(b.toString());
        }
    }

}
