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
package com.norconex.crawler.core.grid.compute;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultGridCompute implements GridCompute {

    private final Grid grid;

    @Override
    public <T> Future<T> runOnOne(String jobName, Callable<T> callable)
            throws GridException {
        return new RunOnOne(grid, false).<T>execute(jobName, callable);
    }

    @Override
    public <T> Future<T> runOnOneOnce(String jobName, Callable<T> callable)
            throws GridException {
        return new RunOnOne(grid, true).<T>execute(jobName, callable);
    }

    @Override
    public <T> Future<T> runOnAll(String jobName, Callable<T> callable)
            throws GridException {
        return new RunOnAll(grid, false).execute(jobName, callable);
    }

    @Override
    public <T> Future<T> runOnAllOnce(String jobName, Callable<T> callable)
            throws GridException {
        return new RunOnAll(grid, true).execute(jobName, callable);
    }

    @Override
    public <T> Future<T> runOnAllSynchronized(
            String jobName, Callable<T> callable) throws GridException {
        return new RunSynchronized(grid).execute(jobName, callable);
    }
}
