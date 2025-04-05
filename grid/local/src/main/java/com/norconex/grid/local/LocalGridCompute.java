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
package com.norconex.grid.local;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.compute.StoppableRunnable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LocalGridCompute implements GridCompute {

    private final LocalGrid grid;
    private static final Map<String, Runnable> activeJobs =
            new ConcurrentHashMap<>();

    @Override
    public GridJobState runOnOne(String jobName, Runnable runnable)
            throws GridException {
        return run(jobName, runnable, false);
    }

    @Override
    public GridJobState runOnOneOnce(String jobName, Runnable runnable)
            throws GridException {
        return run(jobName, runnable, true);
    }

    @Override
    public GridJobState runOnAll(String jobName, Runnable runnable)
            throws GridException {
        return run(jobName, runnable, false);
    }

    @Override
    public GridJobState runOnAllOnce(String jobName, Runnable runnable)
            throws GridException {
        return run(jobName, runnable, true);
    }

    private GridJobState run(String jobName, Runnable runnable, boolean once) {
        // Check if ok to run
        if (activeJobs.containsKey(jobName)) {
            throw new IllegalStateException(
                    "Job \"%s\" is already running.".formatted(jobName));
        }
        if (once) {
            var prevOrCurrentState = grid.jobStateStorage()
                    .getJobState(jobName).orElse(GridJobState.IDLE);
            if (prevOrCurrentState.hasRan()) {
                LOG.warn("""
                    Ignoring request to run job "{}" ONCE as it \
                    already ran in this crawl session with \
                    status: "{}".""",
                        jobName, prevOrCurrentState);
                return prevOrCurrentState;
            }
        }

        try {
            activeJobs.put(jobName, runnable);
            grid.jobStateStorage().setJobStateAtTime(
                    jobName, GridJobState.RUNNING);
            runnable.run();
            grid.jobStateStorage().setJobStateAtTime(
                    jobName, GridJobState.COMPLETED);
            return GridJobState.COMPLETED;
        } catch (Exception e) {
            LOG.error("Job {} failed.", jobName, e);
            grid.jobStateStorage().setJobStateAtTime(
                    jobName, GridJobState.FAILED);
            return GridJobState.FAILED;
        } finally {
            activeJobs.remove(jobName);
        }
    }

    @Override
    public void requestStop(String jobName) {
        for (Entry<String, Runnable> entry : activeJobs.entrySet()) {
            if ((jobName == null || entry.getKey().equals(jobName))
                    && entry.getValue() instanceof StoppableRunnable job) {
                job.stopRequested();
            }
        }
    }
}
