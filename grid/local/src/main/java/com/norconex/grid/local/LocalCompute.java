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

import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.TaskExecutionResult;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalCompute implements GridCompute {

    private final LocalTaskCoordinator taskCoord;
    private final LocalPipelineCoordinator pipeCoord;

    public LocalCompute(LocalGrid grid) {
        taskCoord = new LocalTaskCoordinator(grid);
        pipeCoord = new LocalPipelineCoordinator(grid);
    }

    @Override
    public TaskExecutionResult executeTask(@NonNull GridTask task) {
        return taskCoord.executeTask(task);
    }

    @Override
    public void stopTask(String taskId) {
        taskCoord.stopTask(taskId);
    }

    @Override
    public TaskExecutionResult executePipeline(@NonNull GridPipeline pipeline) {
        return pipeCoord.executePipeline(pipeline);
    }

    @Override
    public int getPipelineActiveStageIndex(@NonNull String pipelineId) {
        return pipeCoord.getActiveStage(pipelineId);
    }

    @Override
    public void stopPipeline(@NonNull String pipelineId) {
        pipeCoord.stopPipeline(pipelineId);
    }
}
