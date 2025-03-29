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

import static com.norconex.grid.core.impl.compute.LogTxt.COORD;
import static com.norconex.grid.core.impl.compute.LogTxt.RECV_FROM;
import static com.norconex.grid.core.impl.compute.LogTxt.WORKER;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jgroups.Address;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.compute.LogTxt;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.messages.JobStateMessageAck;
import com.norconex.grid.core.impl.compute.messages.GridJobDoneMessage;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class NodeJobMessageReceiver implements MessageListener {

    private final NodeJobWorker worker;

    private final CompletableFuture<Void> pendingWorkerDoneAck =
            new CompletableFuture<>();
    private final CompletableFuture<GridJobState> pendingCoordDone =
            new CompletableFuture<>();

    @Override
    public void onMessage(Object payload, Address from) {
        JobStateMessageAck.onReceive(payload, worker.getJobName(), () -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace(LogTxt.msg("RECV_ACK", WORKER, RECV_FROM, COORD,
                        worker.getJobName()));
            }
            pendingWorkerDoneAck.complete(null);
        });

        GridJobDoneMessage.onReceive(payload, worker.getJobName(),
                gridJobState -> {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(LogTxt.msg("RECV_ALLDONE", WORKER, RECV_FROM,
                                COORD,
                                worker.getJobName()));
                    }
                    pendingCoordDone.complete(gridJobState);
                });

        //            StopMessage.onReceive(payload, jobName, msg -> onStopRequested());
    }

    void waitForWorkerDoneAckMsg() {
        ConcurrentUtil.get(pendingWorkerDoneAck, 30, TimeUnit.SECONDS);
    }

    GridJobState waitForCoordDoneMsg() {
        return ConcurrentUtil.get(pendingCoordDone, 30, TimeUnit.SECONDS);
    }

    //        private void onStopRequested() {
    //            //TODO
    //            //for now:
    //            stopRequested = true;
    //        }
}
