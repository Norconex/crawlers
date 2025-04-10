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

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.compute.GridComputeTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LocalGridCompute implements GridCompute {

    private final LocalGrid grid;
    private static final Map<String, GridComputeTask<?>> activeTasks =
            new ConcurrentHashMap<>();

    @Override
    public <T> GridComputeResult<T> runOnOne(
            String taskName, GridComputeTask<T> task) throws GridException {
        return run(taskName, task, false);
    }

    @Override
    public <T> GridComputeResult<T> runOnOneOnce(
            String taskName, GridComputeTask<T> task) throws GridException {
        return run(taskName, task, true);
    }

    @Override
    public <T> GridComputeResult<T> runOnAll(
            String taskName, GridComputeTask<T> task) throws GridException {
        return run(taskName, task, false);
    }

    @Override
    public <T> GridComputeResult<T> runOnAllOnce(
            String taskName, GridComputeTask<T> task) throws GridException {
        return run(taskName, task, true);
    }

    private <T> GridComputeResult<T> run(
            String taskName, GridComputeTask<T> task, boolean once) {
        // Check if ok to run
        if (activeTasks.containsKey(taskName)) {
            throw new IllegalStateException(
                    "Job \"%s\" is already running.".formatted(taskName));
        }
        if (once) {
            var prevOrCurrentState = grid.computeStateStorage()
                    .getComputeState(taskName).orElse(GridComputeState.IDLE);
            if (prevOrCurrentState.hasRan()) {
                LOG.warn("""
                    Ignoring request to run job "{}" ONCE as it \
                    already ran in this crawl session with \
                    status: "{}".""",
                        taskName, prevOrCurrentState);
                return new GridComputeResult<T>().setState(prevOrCurrentState);
            }
        }

        var result = new GridComputeResult<T>();

        try {
            activeTasks.put(taskName, task);
            grid.computeStateStorage().setComputeStateAtTime(
                    taskName, GridComputeState.RUNNING);
            result.setState(GridComputeState.RUNNING);
            result.setValue(task.execute());
            result.setState(GridComputeState.COMPLETED);
            grid.computeStateStorage().setComputeStateAtTime(
                    taskName, GridComputeState.COMPLETED);
            return result;
        } catch (Exception e) {
            LOG.error("Job {} failed.", taskName, e);
            grid.computeStateStorage().setComputeStateAtTime(
                    taskName, GridComputeState.FAILED);
            result.setState(GridComputeState.FAILED);
            result.setExceptionStack(ExceptionUtil.getExceptionMessageList(e));
            return result;
        } finally {
            activeTasks.remove(taskName);
        }
    }

    @Override
    public void stop(String taskName) {
        for (Entry<String, GridComputeTask<?>> entry : activeTasks.entrySet()) {
            if ((taskName == null || entry.getKey().equals(taskName))) {
                entry.getValue().stop();
            }
        }
    }
}
