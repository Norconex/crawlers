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
package com.norconex.grid.core.compute;

import java.io.Serializable;
import java.util.List;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;

/**
 * Core interface for tasks that can be executed on the grid
 */
public interface GridTask extends Serializable {

    //MAYBE: add option to wait for all tasks to be done or not??

    String getId();

    ExecutionMode getExecutionMode();

    /**
     * Executes the task.
     * @param grid the grid instance executing this task
     * @return execution result, or {@code null} when the execution does not
     *         produce any result
     */
    Serializable execute(Grid grid);

    /**
     * Whether the task is meant to be run only once per grid session
     * (whether as a single or multiple nodes task).
     * Trying to run it again will throw a {@link GridException}.
     * @return <code>true</code>
     */
    boolean isOnce();

    //OR make this one part of compute.executeTask argument?
    // or put a map of reducers, keyed by taskId, or task type?
    /**
     * Aggregates all results from running execute on different nodes.
     * Meant to be called by a coordinator that sends back the final result
     * to each nodes.
     * @param results list of results from all nodes
     * @return aggregated result.
     */
    TaskExecutionResult aggregate(List<TaskExecutionResult> results);

    /**
     * <p>
     * Request for the task to stop. The request can be asynchronous and return
     * right away whether it actually stopped or not. In fact, it is not
     * required for a tasks to be stoppable. Some will ignore the request
     * and run until completion (especially short tasks).
     * </p>
     * @param grid the grid instance executing this task
     */
    void stop(Grid grid);
}
