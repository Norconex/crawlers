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
package com.norconex.grid.core.compute;

import static java.util.Optional.ofNullable;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.grid.core.impl.compute.TaskProgress;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public abstract class BaseGridTask implements GridTask {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final ExecutionMode executionMode;

    public BaseGridTask(@NonNull String id, ExecutionMode executionMode) {
        this.id = id;
        this.executionMode =
                ofNullable(executionMode).orElse(ExecutionMode.ALL_NODES);
    }

    public abstract static class SingleNodeTask extends BaseGridTask {
        private static final long serialVersionUID = 1L;

        protected SingleNodeTask(String id) {
            super(id, ExecutionMode.SINGLE_NODE);
        }
    }

    public abstract static class SingleNodeOnceTask extends BaseGridTask {
        private static final long serialVersionUID = 1L;

        protected SingleNodeOnceTask(String id) {
            super(id, ExecutionMode.SINGLE_NODE);
        }

        @Override
        public boolean isOnce() {
            return true;
        }
    }

    public abstract static class AllNodesTask extends BaseGridTask {
        private static final long serialVersionUID = 1L;

        protected AllNodesTask(String id) {
            super(id, ExecutionMode.ALL_NODES);
        }
    }

    public abstract static class AllNodesOnceTask extends BaseGridTask {
        private static final long serialVersionUID = 1L;

        protected AllNodesOnceTask(String id) {
            super(id, ExecutionMode.ALL_NODES);
        }

        @Override
        public boolean isOnce() {
            return true;
        }
    }

    @Override
    public boolean isOnce() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default aggregation implementation uses basic heuristics.
     * </p>
     * <p>
     * If a single result is
     * received, it is assumed to be from a single-node task and returns that
     * result.
     * </p>
     * <p>
     * If multiple results are received, a check is made that at least one
     * task is COMPLETED and returns the first one found. Else, returns
     * the status for any one of the failed tasks.
     * </p>
     * <p>
     * No results will result is a new status with FAILED state.
     * </p>
     * <p>
     * Implementors are encouraged to override this method to return something
     * more appropriate if need be (especially if you expect a result to be
     * returned with the status).
     * </p>
     * @param results the results to aggregate.
     * @return aggregated result
     */
    @Override
    public TaskStatus aggregate(List<TaskProgress> results) {
        if (CollectionUtils.isEmpty(results)) {
            return new TaskStatus(TaskState.FAILED, null,
                    "Task execution returned no status.");
        }
        if (results.size() < 2) {
            return results.get(0).getStatus();
        }
        TaskStatus status = null;
        for (TaskProgress progress : results) {
            status = progress.getStatus();
            if (status != null && status.getState() == TaskState.COMPLETED) {
                break;
            }
        }
        if (status == null || status.getState() == null) {
            return new TaskStatus(TaskState.FAILED, null,
                    "Task execution returned no valid status.");
        }
        return status;
    }

    /**
     * {@inheritDoc}
     * Default implementation for this base class ignores stop requests.
     */
    @Override
    public void stop() {
        LOG.debug("Task {} ignored request to stop.", getId());
    }
}
