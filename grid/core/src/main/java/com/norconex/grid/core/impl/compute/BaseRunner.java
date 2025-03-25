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

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;

import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class BaseRunner {

    protected final CoreGrid grid;
    protected final boolean runOnce;

    protected BaseRunner(CoreGrid grid, boolean runOnce) {
        this.grid = grid;
        this.runOnce = runOnce;
    }

    public final GridJobState execute(String jobName, Runnable runnable)
            throws GridException {

        //TODO in grid transaction so nothing happens between checking
        // status and deciding what to do with it?
        //TODO options to retry on failure

        var jobState = grid.storageHelper()
                .getJobState(jobName)
                .orElse(GridJobState.IDLE);

        if (runOnce && jobState.hasRan()) {
            LOG.warn("Ignoring request to run job \"{}\" ONCE as it already "
                    + "ran in this crawl session with status: \"{}\".",
                    jobName, jobState);
            return jobState;
        }

        // We do not want a coordinator to run twice the same job
        if (grid.isCoordinator() && !jobState.isRunning()) {
            jobState = runAsCoordinator(jobName, runnable);
        } else {
            jobState = runAsWorker(jobName, runnable);
        }

        if (jobState == GridJobState.FAILED) {
            LOG.error("Job failed: {}", jobName);
        }

        return jobState;
    }

    protected abstract GridJobState runAsCoordinator(
            String jobName, Runnable runnable);

    protected abstract GridJobState runAsWorker(String jobName, Runnable runnable);
}
