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

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.pipeline.PipelineCoordinator;
import com.norconex.grid.core.impl.compute.task.TaskCoordinator;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreCompute implements GridCompute {

    @Getter
    private final CoreGrid grid;
    @Getter
    private final WorkDispatcher dispatcher;
    @Getter
    private final Worker localWorker;

    private final TaskCoordinator taskCoord;
    private final PipelineCoordinator pipeCoord;

    public CoreCompute(CoreGrid grid) {
        this.grid = grid;
        localWorker = new Worker(grid);
        dispatcher = new WorkDispatcher(grid, localWorker);
        taskCoord = new TaskCoordinator(this);
        pipeCoord = new PipelineCoordinator(this);
    }

    @Override
    public TaskExecutionResult executeTask(@NonNull GridTask task) {
        try {
            var status = taskCoord.executeTask(task);
            if (status == null || status.getState() == null) {
                return new TaskExecutionResult(TaskState.FAILED, null,
                        "Could not determine state of executed task.");
            }
            return status;
        } catch (Exception e) {
            LOG.error("Exception occured executing task {}", task.getId(), e);
            return new TaskExecutionResult(TaskState.FAILED, null,
                    "Exception occured executing task " + task.getId() + ": "
                            + ExceptionUtil.getFormattedMessages(e));
        }
    }

    @Override
    public void stopTask(String taskId) {
        try {
            taskCoord.stopTask(taskId);
        } catch (Exception e) {
            throw new GridException(
                    "Error while requesting task to stop: " + taskId);
        }
    }

    @Override
    public TaskExecutionResult executePipeline(@NonNull GridPipeline pipeline) {
        try {
            return pipeCoord.executePipeline(pipeline);
        } catch (Exception e) {
            LOG.error("Exception occured executing pipeline {}",
                    pipeline.getId(), e);
            throw new GridException(
                    "Exception occured executing pipeline " + pipeline.getId(),
                    e);
        }
    }

    @Override
    public void stopPipeline(String pipelineId) {
        try {
            pipeCoord.stopPipeline(pipelineId);
        } catch (Exception e) {
            throw new GridException(
                    "Error while requesting pipeline to stop: " + pipelineId);
        }
    }

    // -1 if none active
    @Override
    public int getActivePipelineStageIndex(String pipelineId) {
        return pipeCoord.getActiveStageIndex(pipelineId);
    }

    //    public void close() {
    //        if (dispatcher != null)
    //            try {
    //                dispatcher.close();
    //            } catch (IOException e) {
    //                // TODO Auto-generated catch block
    //                e.printStackTrace();
    //            }
    //    }
}
