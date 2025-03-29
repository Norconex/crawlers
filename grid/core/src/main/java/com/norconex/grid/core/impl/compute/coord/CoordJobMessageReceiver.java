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

import static com.norconex.grid.core.impl.compute.LogTxt.COORD;
import static com.norconex.grid.core.impl.compute.LogTxt.RECV_FROM;
import static com.norconex.grid.core.impl.compute.LogTxt.WORKER;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jgroups.Address;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.compute.JobStateAtTime;
import com.norconex.grid.core.impl.compute.LogTxt;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.messages.JobStateMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class CoordJobMessageReceiver implements MessageListener {

    private static final long NODE_JOB_TIMEOUT_MS = 30_000;

    private final GridJobCoordinator coord;

    private final Map<Address, JobStateAtTime> workerStates =
            new ConcurrentHashMap<>();

    @Override
    public void onMessage(Object payload, Address from) {
        JobStateMessage.onReceive(payload, coord.getJobName(), msg -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace(LogTxt.msg("RECV_STATE", COORD, RECV_FROM, WORKER,
                        from, msg.getStateAtTime().getState()));
            }
            workerStates.put(from, msg.getStateAtTime());
            //            logWorkerStates(); // temp
            if (msg.isAckRequired()) {
                coord.getMessageSender().acknowledgeTo(from);
            }
        });
        //
        //            StopMessage.onReceive(payload, jobName, msg -> onStopRequested());
    }

    GridJobState waitForAllWorkersDone() {
        GridJobState state = null;
        while ((state = stateOfAll()).isRunning()) {
            Sleeper.sleepMillis(250);
        }
        return state;
    }

    //        private void onStopRequested() {
    //            //TODO
    //            //for now:
    //            stopRequested = true;
    //        }

    //TODO make configurable to throw/stop on any node failure
    // or make sure to recover
    public GridJobState stateOfAll() {
        //TODO have a timeout that throws if we keep missing workers
        // For a little bit while the nodes are warming up that may be
        // fine but more than that we should throw.
        if (!workerStates.keySet()
                .containsAll(coord.getGrid().getClusterMembers())) {
            logWorkerStates();
            return GridJobState.RUNNING;
        }

        // at this point we have all nodes reporting... check global state,
        // considering complete if all ran and at least one succeeded.
        // (for now... maybe make configurable or implement fail-over).
        var stateOfAll = GridJobState.IDLE;
        for (var entry : workerStates.entrySet()) {
            var node = entry.getKey();
            var atTime = entry.getValue();
            var state = atTime.getState();
            if (state.hasRan() && state.ordinal() > stateOfAll.ordinal()) {
                stateOfAll = state;
            }

            if (atTime.elapsed() > NODE_JOB_TIMEOUT_MS) {
                LOG.warn("Node \"{}\" seems to have expired. "
                        + "No signal since {} seconds ago.", node,
                        Duration.ofMillis(atTime.elapsed()).toSeconds());
            }

            // if still running and not expired, return right away
            // as we know the session is not done
            if (!state.hasRan()) {
                logWorkerStates();
                return GridJobState.RUNNING;
            }
        }
        return stateOfAll;
    }

    private void logWorkerStates() {
        if (LOG.isTraceEnabled()) {
            var b = new StringBuilder();
            b.append("All node states:");
            workerStates.forEach((addr, atTime) -> {
                b.append("\n    " + addr + " -> " + atTime);
            });
            LOG.trace(b.toString());
        }
    }
}
