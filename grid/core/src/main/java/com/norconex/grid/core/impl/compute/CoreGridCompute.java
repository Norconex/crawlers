/* Copyright 2024-2025 Norconex Inc.
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

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.messages.StopComputeMessage;
import com.norconex.grid.core.impl.compute.worker.NodeTaskWorker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CoreGridCompute implements GridCompute {

    private final CoreGrid grid;

    @Override
    public <T> GridComputeResult<T> runOnOne(
            String taskName, GridComputeTask<T> task) throws GridException {
        LOG.info("Running job \"{}\" on node \"{}\".",
                taskName, grid.getCoordinator());
        return new NodeTaskWorker(grid, taskName, RunOn.ONE).run(task);
    }

    @Override
    public <T> GridComputeResult<T> runOnOneOnce(
            String taskName, GridComputeTask<T> task) throws GridException {
        LOG.info("Running job \"{}\" on node \"{}\", maximum once",
                taskName, grid.getCoordinator());
        return new NodeTaskWorker(grid, taskName, RunOn.ONE_ONCE).run(task);
    }

    @Override
    public <T> GridComputeResult<T> runOnAll(
            String taskName, GridComputeTask<T> task) throws GridException {
        LOG.info("Running job \"{}\" on all nodes.", taskName);
        return new NodeTaskWorker(grid, taskName, RunOn.ALL).run(task);
    }

    @Override
    public <T> GridComputeResult<T> runOnAllOnce(
            String taskName, GridComputeTask<T> task) throws GridException {
        LOG.info("Running job \"{}\" on all nodes, maximum once.", taskName);
        return new NodeTaskWorker(grid, taskName, RunOn.ALL_ONCE).run(task);
    }

    @Override
    public void stop(String taskName) {
        grid.send(new StopComputeMessage(taskName));
    }
}
