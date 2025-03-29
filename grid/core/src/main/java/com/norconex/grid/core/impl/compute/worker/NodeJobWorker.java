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

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.coord.GridJobCoordinator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents one node running a job as part of a multi-nodes job.
 */
@Slf4j
@Getter
public class NodeJobWorker {
    private final CoreGrid grid;
    private final String jobName;
    private final boolean runOnce;
    private final NodeJobMessageReceiver messageReceiver;
    private final NodeJobMessageSender messageSender;

    public NodeJobWorker(CoreGrid grid, String jobName, boolean runOnce) {
        this.grid = grid;
        this.jobName = jobName;
        this.runOnce = runOnce;
        messageReceiver = new NodeJobMessageReceiver(this);
        messageSender = new NodeJobMessageSender(this);
    }

    public GridJobState run(Runnable job) {
        var prevOrCurrentState = grid.storageHelper()
                .getJobState(jobName).orElse(GridJobState.IDLE);
        if (!okToRun(prevOrCurrentState, runOnce)) {
            return prevOrCurrentState;
        }

        try {
            grid.addListener(messageReceiver);
            if (grid.isCoordinator()) {
                LOG.info("Starting job coordinator...");
                new Thread(new GridJobCoordinator(grid, jobName, runOnce))
                        .start();
            }

            return NodeJobLock.runExclusively(grid, jobName, () -> {
                new NodeJobLifeCycle(this).run(job);
                // wait for coordinator to signal all nodes are done.
                LOG.info("Node {} is done with job {}. Awaiting coordinator "
                        + "signal to proceed.",
                        grid.getLocalAddress(), jobName);
                return getMessageReceiver().waitForCoordDoneMsg();

            });
        } finally {
            grid.removeListener(messageReceiver);
        }

    }

    private boolean okToRun(GridJobState state, boolean runOnce) {
        // A coordinator can't run a job already running.
        if (grid.isCoordinator() && state.isRunning()) {
            LOG.warn("A coordinator node tried to run a job already running.");
            return false;
        }
        // Ensure runOnce is respected
        if (runOnce && state.hasRan()) {
            LOG.warn("Ignoring request to run job \"{}\" ONCE as it already "
                    + "ran in this crawl session with status: \"{}\".",
                    jobName, state);
            return false;
        }
        return true;
    }

}
