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

import java.util.Optional;

import com.norconex.grid.core.compute.JobState;
import com.norconex.grid.core.storage.GridMap;

/**
 * Storage-related utility methods specific to the Core implementation.
 */
public class CoreStorageHelper {

    private static final String JOB_STATES_KEY = "__jobStates";

    private final CoreGrid grid;
    private final GridMap<JobStateAtTime> jobStates;

    public CoreStorageHelper(CoreGrid grid) {
        this.grid = grid;
        jobStates = grid.storage().getMap(JOB_STATES_KEY, JobStateAtTime.class);
    }

    public Optional<JobState> getJobState(String jobName) {
        return Optional.ofNullable(jobStates.get(jobName))
                .map(JobStateAtTime::getState);
    }

    public Optional<JobStateAtTime> getJobStateAtTime(String jobName) {
        return Optional.ofNullable(jobStates.get(jobName));
    }

    public void setJobStateAtTime(String jobName, JobState state, long time) {
        jobStates.put(jobName,
                new JobStateAtTime(state, time, grid.getNodeName()));
    }
}
