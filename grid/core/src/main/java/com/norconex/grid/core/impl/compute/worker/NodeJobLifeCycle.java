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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jgroups.Address;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.compute.StoppableRunnable;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.messages.JobStateMessage;
import com.norconex.grid.core.impl.compute.messages.StopJobMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NodeJobLifeCycle {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private final NodeJobWorker worker;
    private boolean requestAck = false;

    public NodeJobLifeCycle(NodeJobWorker worker) {
        this.worker = worker;
    }

    public void run(Runnable runnable) {
        var stopListener = new JobStopListener(runnable);
        worker.getGrid().addListener(stopListener);
        try {
            // set running
            var mutableState = new AtomicReference<>(GridJobState.RUNNING);
            var ackReceived = new AtomicBoolean(false);
            final var stateLock = new Object(); // Lock for synchronization

            // start coord notif scheduler
            var schedulerTask = scheduler.scheduleAtFixedRate(() -> {
                synchronized (stateLock) {
                    // Only send if acknowledgment has NOT been received
                    if (!ackReceived.get()) {
                        worker.getMessageSender().sendToCoord(
                                JobStateMessage.of(
                                        worker.getJobName(),
                                        mutableState.get(),
                                        requestAck));
                    }
                }
            }, 0, 5, TimeUnit.SECONDS);

            // run actual job
            try {
                runnable.run();
                // Ensure no other thread sends RUNNING after this
                synchronized (stateLock) {
                    mutableState.set(GridJobState.COMPLETED);
                }
            } catch (Exception e) {
                LOG.error("Job {} failed.", worker.getJobName(), e);
                // Ensure no other thread sends RUNNING after this
                synchronized (stateLock) {
                    mutableState.set(GridJobState.FAILED);
                }
            }

            // don't wait next scheduler cycle to send we're done
            requestAck = true;
            synchronized (stateLock) { // Prevent race condition
                worker.getMessageSender().sendToCoord(JobStateMessage.of(
                        worker.getJobName(), mutableState.get(), requestAck));
            }
            // wait for coord to acknowledge we're done.
            worker.getMessageReceiver().waitForWorkerDoneAckMsg();
            // Ensure the scheduler doesn't send more messages
            synchronized (stateLock) {
                ackReceived.set(true);
            }
            schedulerTask.cancel(false);
        } finally {
            worker.getGrid().removeListener(stopListener);
            scheduler.shutdown();
        }
    }

    @RequiredArgsConstructor
    private class JobStopListener implements MessageListener {
        private final Runnable job;

        @Override
        public void onMessage(Object payload, Address from) {
            StopJobMessage.onReceive(payload, worker.getJobName(), msg -> {
                if (job instanceof StoppableRunnable stoppable) {
                    LOG.info("Job \"{}\" received a stop request.",
                            worker.getJobName());
                    stoppable.stopRequested();
                } else {
                    LOG.info("Job \"{}\" does not support being stopped. "
                            + "Waiting until it finishes.",
                            worker.getJobName());
                }
            });
        }
    }
}
