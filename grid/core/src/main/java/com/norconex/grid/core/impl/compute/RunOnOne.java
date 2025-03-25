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
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RunOnOne extends BaseRunner {

    public RunOnOne(CoreGrid grid, boolean runOnce) {
        super(grid, runOnce);
    }

    @Override
    public GridJobState runAsCoordinator(String jobName, Runnable runnable)
            throws GridException {

        //TODO in grid transaction so nothing happens between checking
        // status and deciding what to do with it?
        //TODO options to retry on failure

        return new JobExecutor(grid, jobName, true).execute(runnable);
    }

    @Override
    protected GridJobState runAsWorker(String jobName, Runnable runnable) {
        try {
            return new JobListener(grid, jobName, null)
                    .startListening()
                    .waitForCompletion();
        } catch (InterruptedException e) {
            LOG.warn("Job {} interrupted. Marking it as failed.",
                    jobName, ConcurrentUtil.wrapAsCompletionException(e));
            Thread.currentThread().interrupt();
            return GridJobState.FAILED;
        }
    }

}
