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
package com.norconex.grid.core.impl.compute.pipeline;

import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.Stage;
import com.norconex.grid.core.compute.TaskExecutionResult;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Context class for the pipeline coordinator.
 */
@Data
@Accessors(chain = true)
public class PipeExecutionContext {
    private GridPipeline pipeline;
    private Stage activeStage;
    /**
     * Returned value from previous stage task. Is {@code null} when
     * running the first stage or the returned value from the previous
     * stage is {@code null}.
     */
    private TaskExecutionResult lastStageResult;
    private GridTask activeTask;
    private boolean stopRequested;
    private int currentIndex;
    private int startIndex;
    private int failedIndex = -1;
}
