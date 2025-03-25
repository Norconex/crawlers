/* Copyright 2024-2025 Norconex Inc.
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
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CoreGridCompute implements GridCompute {

    private final CoreGrid grid;

    @Override
    public GridJobState runOnOne(String jobName, Runnable runnable)
            throws GridException {
        return new RunOnOne(grid, false).execute(jobName, runnable);
    }

    @Override
    public GridJobState runOnOneOnce(String jobName, Runnable runnable)
            throws GridException {
        return new RunOnOne(grid, true).execute(jobName, runnable);
    }

    @Override
    public GridJobState runOnAll(String jobName, Runnable runnable)
            throws GridException {
        return new RunOnAll(grid, false).execute(jobName, runnable);
    }

    @Override
    public GridJobState runOnAllOnce(String jobName, Runnable runnable)
            throws GridException {
        return new RunOnAll(grid, true).execute(jobName, runnable);
    }
}
