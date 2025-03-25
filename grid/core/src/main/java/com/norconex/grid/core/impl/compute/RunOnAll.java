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
package com.norconex.grid.core.impl.compute;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jgroups.Address;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RunOnAll extends BaseRunner {

    //TODO consider adding session it back into the mix so we know
    // it already ran "within a session"

    //TODO make configurable
    private static final long NODE_JOB_TIMEOUT_MS = 30_000;

    public RunOnAll(CoreGrid grid, boolean runOnce) {
        super(grid, runOnce);
    }

    @Override
    protected GridJobState runAsCoordinator(String jobName, Runnable runnable) {
        var nodeJobName = asNodeJobName(jobName);
        //TODO handle if this one fails
        Map<Address, JobStateAtTime> nodeStates = new ConcurrentHashMap<>();
        var jobState = new MutableObject<GridJobState>();
        MessageListener nodeStatesListener = (payload, from) -> {
            if (payload instanceof JobStateMessage msg
                    && Objects.equals(nodeJobName, msg.getJobName())) {
                LOG.trace("Heartbeat received: {} -> {} -> {}",
                        from,
                        msg.getJobName(),
                        msg.getStateAtTime().getState());
                nodeStates.put(from, msg.getStateAtTime());
                synchronized (RunOnAll.this) {
                    notifyAll();
                }
            }
        };
        try {
            grid.addListener(nodeStatesListener);
            // Tracking all jobs
            new JobExecutor(grid, jobName, true).execute(() -> {
                // The job on this node
                new JobExecutor(grid, nodeJobName, false).execute(runnable);

                LOG.trace("Coordinator finished its worker job. Now waiting "
                        + "for all notes to complete.");

                // coordinate that all are done before returning
                GridJobState stateOfAll = null;
                while ((stateOfAll = stateOfAll(nodeStates)).isRunning()) {
                    try {
                        synchronized (this) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        LOG.warn("Job {} interrupted.",
                                jobName,
                                ConcurrentUtil.wrapAsCompletionException(e));
                        jobState.setValue(GridJobState.FAILED);
                        Thread.currentThread().interrupt();
                    }
                }
                jobState.setValue(stateOfAll);
            });
        } finally {
            grid.removeListener(nodeStatesListener);
        }
        nodeStates.clear();
        return jobState.getValue();
    }

    @Override
    protected GridJobState runAsWorker(String jobName, Runnable runnable) {
        var nodeJobName = asNodeJobName(jobName);
        // Non-coordinator run and send events but do not persist state.
        // When done, they wait for the coordinator to be done.
        // The coordinator will only be done if all are done.
        var jobState = GridJobState.IDLE;
        new JobExecutor(grid, nodeJobName, false).execute(runnable);
        try {
            jobState = new JobListener(grid, jobName, null)
                    .startListening()
                    .waitForCompletion();
        } catch (InterruptedException e) {
            LOG.warn("Job {} interrupted.",
                    jobName, ConcurrentUtil.wrapAsCompletionException(e));
            //TODO make it STOPPED?
            jobState = GridJobState.FAILED;
            Thread.currentThread().interrupt();
        }
        return jobState;
    }

    private String asNodeJobName(String jobName) {
        return jobName + "__NODE";
    }

    //TODO make configurable to throw/stop on any node failure
    // or make sure to recover
    private GridJobState stateOfAll(Map<Address, JobStateAtTime> nodeStates) {
        try {
            var numOfNodes = grid.getClusterMembers().size();
            if (nodeStates.size() < numOfNodes
                    || !nodeStates.keySet()
                            .containsAll(grid.getClusterMembers())) {
                if (LOG.isTraceEnabled()) {
                    nodeStates.forEach((addr, atTime) -> {
                        LOG.trace(addr + " -> " + atTime);
                    });
                }
                return GridJobState.RUNNING;
            }

            // at this point we have all nodes reporting... check global state
            // considering complete if all ran and at least one succeeded.
            // (for now... maybe make configurable or implement fail-over).
            var stateOfAll = GridJobState.IDLE;
            for (var atTime : nodeStates.values()) {
                var state = atTime.getState();
                if (state.hasRan() && state.ordinal() > stateOfAll.ordinal()) {
                    stateOfAll = state;
                }
                // if still running and not expired, return right away
                if (!state.hasRan()
                        && atTime.since().toMillis() < NODE_JOB_TIMEOUT_MS) {
                    return GridJobState.RUNNING;
                }
            }
            return stateOfAll;
        } finally {
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
