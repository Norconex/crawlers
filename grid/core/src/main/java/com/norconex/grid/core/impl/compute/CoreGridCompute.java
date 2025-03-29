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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.messages.StopMessage;
import com.norconex.grid.core.impl.compute.worker.NodeJobWorker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CoreGridCompute implements GridCompute {

    private final CoreGrid grid;

    @Override
    public GridJobState runOnOne(String jobName, Runnable runnable)
            throws GridException {
        LOG.info("Running job \"{}\" on node \"{}\".",
                jobName, grid.getCoordinator());
        return new NodeJobWorker(grid, jobName, false).run(() -> {
            if (grid.isCoordinator()) {
                runnable.run();
            } else {
                LOG.info("Node \"{}\" wasn't selected to run job \"{}\". "
                        + "Marking as done.",
                        grid.getLocalAddress(), jobName);
            }
        });
    }

    @Override
    public GridJobState runOnOneOnce(String jobName, Runnable runnable)
            throws GridException {
        LOG.info("Running job \"{}\" on node \"{}\", maximum once",
                jobName, grid.getCoordinator());
        return new NodeJobWorker(grid, jobName, true).run(() -> {
            if (grid.isCoordinator()) {
                runnable.run();
            } else {
                LOG.info("Node \"{}\" as nothing to do for job \"{}\".",
                        grid.getLocalAddress(), jobName);
            }
        });
    }

    @Override
    public GridJobState runOnAll(String jobName, Runnable runnable)
            throws GridException {
        LOG.info("Running job \"{}\" on all nodes.", jobName);
        return new NodeJobWorker(grid, jobName, false).run(runnable);
    }

    @Override
    public GridJobState runOnAllOnce(String jobName, Runnable runnable)
            throws GridException {
        LOG.info("Running job \"{}\" on all nodes, maximum once.", jobName);
        return new NodeJobWorker(grid, jobName, true).run(runnable);
    }

    @Override
    public Future<Void> stop(String jobName) {
        var future = new CompletableFuture<Void>();
        CompletableFuture.runAsync(() -> {
            grid.send(new StopMessage(jobName));
            //TODO wait for acknowledgement with timeout
            future.complete(null);
        });
        return future;
    }

}
