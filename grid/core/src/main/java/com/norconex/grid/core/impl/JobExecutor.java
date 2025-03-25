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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.compute.JobState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobExecutor {

    // so other nodes can know of failures without notifications
    //TODO make configurable
    private static final long HEART_BEAT_INTERVAL_SEC = 10;

    private final CoreGrid grid;
    private final String jobName;
    private final boolean persistState;
    private ScheduledExecutorService scheduler;
    private JobState jobState = JobState.IDLE;

    public JobExecutor(CoreGrid grid, String jobName, boolean persistState) {
        this.grid = grid;
        this.jobName = jobName;
        this.persistState = persistState;
    }

    public JobState execute(Runnable runnable) {
        LOG.info("Starting job: {}.", jobName);
        jobState = JobState.RUNNING;

        try {
            startBroadcasting();
            runnable.run();
            jobState = JobState.COMPLETED;
            LOG.info("Job completed: {}.", jobName);
        } catch (Exception e) {
            jobState = JobState.FAILED;
            LOG.error("Job {} failed.", jobName, e);
        } finally {
            stopBroadcasting();
        }
        return jobState;
    }

    // Send periodic heartbeat messages
    private void startBroadcasting() {
        doBroadcast(jobState);
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                doBroadcast(jobState);
                LOG.trace("Heartbeat sent for node->job->state: {}->{}->{}",
                        grid.getNodeName(), jobName, jobState);
            } catch (Exception e) {
                LOG.error("Could not send job status update.", e);
            }
        }, 0, HEART_BEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void stopBroadcasting() {
        doBroadcast(jobState);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void doBroadcast(JobState jobState) {
        var time = System.currentTimeMillis();
        if (persistState) {
            grid.storageHelper().setJobStateAtTime(jobName, jobState, time);
        }
        grid.send(new JobStateMessage(jobName,
                new JobStateAtTime(jobState, time, grid.getNodeName())));
    }
}
