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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jgroups.Address;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.compute.GridComputeTask;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.messages.StopComputeMessage;
import com.norconex.grid.core.impl.compute.messages.TaskPayloadMessenger;
import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.grid.core.util.NamingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NodeTaskLifeCycle {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private final NodeTaskWorker worker;
    private final TaskPayloadMessenger messenger;
    private final NodeTaskResult nodeResult = new NodeTaskResult();
    private final String taskName;
    private final boolean skipping;
    private final ReentrantLock stateLock = new ReentrantLock();

    public NodeTaskLifeCycle(NodeTaskWorker worker) {
        this.worker = worker;
        taskName = worker.getTaskName();
        messenger = worker.getGrid().taskPayloadMessenger();
        skipping = worker.getRunOn().isOnOne()
                && !worker.getGrid().isCoordinator();
    }

    public <T> void run(GridComputeTask<T> task) {
        var stopListener = new JobStopListener(task);
        worker.getGrid().addListener(stopListener);
        try {
            nodeResult.setState(GridComputeState.RUNNING);

            var schedulerTask = scheduler.scheduleAtFixedRate(() -> {
                if (stateLock.tryLock()) {
                    try {
                        messenger.sendToCoord(taskName, nodeResult);
                    } finally {
                        stateLock.unlock();
                    }
                } else {
                    LOG.debug(worker.getGrid().getLocalAddress()
                            + " Skipping update, stateLock busy.");
                }
            }, 0, 5, TimeUnit.SECONDS);

            // Actual task execution
            try {
                if (skipping) {
                    LOG.info("Node \"{}\" wasn't selected to run job \"{}\". "
                            + "Marking as done.",
                            worker.getGrid().getLocalAddress(), taskName);
                } else {
                    nodeResult.setValue(task.execute());
                }

                // Final state set
                stateLock.lock();
                try {
                    nodeResult.setState(GridComputeState.COMPLETED);
                } finally {
                    stateLock.unlock();
                }
            } catch (Exception e) {
                LOG.error("Job {} failed.", taskName, e);
                stateLock.lock();
                try {
                    nodeResult.getExceptionStack().addAll(
                            ExceptionUtil.getExceptionMessageList(e));
                    nodeResult.setState(GridComputeState.FAILED);
                } finally {
                    stateLock.unlock();
                }
            }

            // Wait for final ACK
            NodeTaskResult resultSnapshot;
            stateLock.lock();
            try {
                resultSnapshot = BeanUtil.clone(nodeResult);
            } finally {
                stateLock.unlock();
            }

            // Send final ACK in separate thread, wait safely
            var ackFuture = CompletableFuture.supplyAsync(
                    () -> ConcurrentUtil.get(messenger.sendToCoordAndAwaitAck(
                            taskName, resultSnapshot), 1, TimeUnit.MINUTES),
                    new NamingExecutor(
                            Executors.newFixedThreadPool(1),
                            "msg-send-await-ack"));

            ConcurrentUtil.get(ackFuture, 1, TimeUnit.MINUTES);

            // Stop periodic updates now that final result is sent
            schedulerTask.cancel(true);

        } finally {
            worker.getGrid().removeListener(stopListener);
            scheduler.shutdown();
        }
    }

    @RequiredArgsConstructor
    private class JobStopListener implements MessageListener {
        private final GridComputeTask<?> task;

        @Override
        public void onMessage(Object payload, Address from) {
            StopComputeMessage.onReceive(payload, worker.getTaskName(),
                    stopMsg -> {
                        LOG.info("Job \"{}\" received a stop request.",
                                worker.getTaskName());
                        task.stop();
                    });
        }
    }
}
