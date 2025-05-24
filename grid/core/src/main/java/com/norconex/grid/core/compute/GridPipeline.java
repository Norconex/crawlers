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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * A pipeline executed on a grid. For the pipeline to be resumable,
 * the same stages must be present on each execution.
 */
@Getter
@ToString
public class GridPipeline {

    private final String id;
    private final List<Stage> stages;

    public GridPipeline(
            @NonNull String pipelineId,
            @NonNull List<Stage> stages) {
        id = pipelineId;
        this.stages = Collections.unmodifiableList(stages.stream()
                .map(Stage::new) // defensive copy
                .toList());
    }

    public static GridPipeline of(
            @NonNull String pipelineId,
            @NonNull Stage... stages) {
        return new GridPipeline(pipelineId, List.of(stages));
    }

    public static GridPipeline of(
            @NonNull String pipelineId,
            @NonNull GridTask... task) {
        return new GridPipeline(
                pipelineId,
                Stream.of(task).map(Stage::new).toList());
    }

    //TODO, somehow have stages receive the response from the previous stage
    // if possible given we can resume. Then maybe the task would be
    // obtained via a function instead.

}
