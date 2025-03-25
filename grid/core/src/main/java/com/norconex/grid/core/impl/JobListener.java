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
package com.norconex.grid.core.impl;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jgroups.Address;

import com.norconex.grid.core.compute.JobState;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobListener implements MessageListener {

    private static final long POLLING_INTERVAL_MS = 30_000;

    private final String jobName;
    private long lastSignal;
    private final CoreGrid grid;
    private final Consumer<JobState> consumer;
    private ScheduledExecutorService scheduler;
    @Getter
    private JobState currentJobState = JobState.IDLE;

    public JobListener(
            CoreGrid grid, String jobName, Consumer<JobState> consumer) {
        this.grid = grid;
        this.jobName = jobName;
        this.consumer = consumer;
    }

    @Override
    public void onMessage(Object payload, Address from) {
        if (payload instanceof JobStateMessage msg
                && Objects.equals(msg.getJobName(), jobName)) {
            currentJobState = msg.getStateAtTime().getState();
            lastSignal = System.currentTimeMillis();
            if (consumer != null) {
                consumer.accept(currentJobState);
            }
            synchronized (this) {
                notifyAll();
            }
        }
    }

    public JobListener startListening() {
        lastSignal = System.currentTimeMillis();
        grid.addListener(this);
        startPolling();
        return this;
    }

    public JobListener stopListening() {
        grid.removeListener(this);
        stopPolling();
        return this;
    }

    // will automatically invoke stopListening when completed.
    public synchronized JobState waitForCompletion()
            throws InterruptedException {
        while (currentJobState != JobState.COMPLETED
                && currentJobState != JobState.FAILED) {
            wait(); // Wait for either polling or the message
        }
        stopListening();
        return currentJobState;
    }

    // Periodically check the shared state for job completion
    private void startPolling() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - lastSignal > POLLING_INTERVAL_MS) {
                // TODO store timestamp with shared state so we can check
                // that it expired there as well so we can react to that.
                LOG.warn("A job missed updating us. "
                        + "Falling back to shared state.");
                var stateAtTimeOpt =
                        grid.storageHelper().getJobStateAtTime(jobName);
                if (stateAtTimeOpt.isPresent()) {
                    currentJobState = stateAtTimeOpt.get().getState();
                } else {
                    //TODO threat this as an error, converting status to FAILED or EXPIRED
                }
                if (consumer != null) {
                    consumer.accept(currentJobState);
                }
                synchronized (this) {
                    notifyAll();
                }
            }
        }, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
