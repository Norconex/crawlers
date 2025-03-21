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
package com.norconex.crawler.core.grid.pipeline;

import java.util.function.Predicate;

import com.norconex.crawler.core.grid.compute.GridCompute.RunOn;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;

/**
 * A pipeline stage.
 *
 * @param <T> type of optional argument passed to all stages of a pipeline
 */
@Builder
@Getter
public class GridPipelineStage<T> {

    /**
     * Must be unique across a pipeline.
     */
    @NonNull
    private final String name;
    @Default
    @NonNull
    private RunOn runOn = RunOn.ALL;

    /**
     * Conditionally execute this stage.
     */
    private Predicate<T> onlyIf;

    /**
     * Always run even if a previous stage returned false to end the pipeline.
     * If just joining a grid, stages before the current one are not run,
     * even if this property is <code>true</code>.
     */
    private boolean always;

    //TODO add onlyIf (condition) and always (run no matter what -- shutdown)

    /**
     * Code meant to run on one or more server nodes (when in a clustered
     * environment).
     */
    @NonNull
    private final GridPipelineTask<T> task;
}
