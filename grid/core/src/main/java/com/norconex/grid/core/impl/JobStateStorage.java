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

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.compute.JobStateAtTime;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridStorage;

import lombok.extern.slf4j.Slf4j;

/**
 * Storage-related utility methods specific to the Core implementation.
 */
@Slf4j
public class JobStateStorage {

    private static final String JOB_STATES_KEY = "__jobStates";

    private final GridMap<JobStateAtTime> jobStates;
    private final GridStorage storage;

    public JobStateStorage(Grid grid) {
        storage = grid.storage();
        jobStates = storage.getMap(JOB_STATES_KEY, JobStateAtTime.class);
    }

    public Optional<GridJobState> getJobState(String jobName) {
        return ifStateStoreExists(
                () -> Optional.ofNullable(jobStates.get(jobName))
                        .map(JobStateAtTime::getState));
    }

    public Optional<JobStateAtTime> getJobStateAtTime(String jobName) {
        return ifStateStoreExists(
                () -> Optional.ofNullable(jobStates.get(jobName)));
    }

    public void setJobStateAtTime(String jobName, GridJobState state) {
        ifStateStoreExists(() -> {
            jobStates.put(
                    jobName,
                    new JobStateAtTime(state, System.currentTimeMillis()));
            return null;
        });

    }

    public Map<String, JobStateAtTime> getRunningJobs() {
        Map<String, JobStateAtTime> jobs = new ListOrderedMap<>();
        return ifStateStoreExists(() -> {
            jobStates.forEach((name, job) -> {
                if (job.getState().isRunning()) {
                    jobs.put(name, job);
                }
                return true;
            });
            return jobs;
        }, () -> jobs);
    }

    public boolean reset() {
        return ifStateStoreExists(() -> {
            var hasStates = !jobStates.isEmpty();
            jobStates.clear();
            return hasStates;
        }, () -> false);
    }

    // This wrapper method is necessary in case we are destroying the stores
    // as part of a job and we want to set/get the job state.
    private <T> T ifStateStoreExists(Supplier<T> s) {
        return ifStateStoreExists(s, null);
    }

    private <T> T ifStateStoreExists(Supplier<T> supply, Supplier<T> orElse) {
        if (storage.storeExists(JOB_STATES_KEY)) {
            return supply.get();
        }
        LOG.info("State store no longer exists.");
        return orElse != null ? orElse.get() : null;
    }
}
