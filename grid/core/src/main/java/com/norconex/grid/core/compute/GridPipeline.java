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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;

/**
 * A pipeline.
 */
@Getter
public class GridPipeline {

    private final String pipelineId;
    private final List<Stage> stages;

    public GridPipeline(
            @NonNull String pipelineId,
            @NonNull List<Stage> stages) {
        this.pipelineId = pipelineId;
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
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
                pipelineId, Stream.of(task).map(Stage::new).toList());
    }

    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static class Stage {

        /**
         * Conditionally execute this stage.
         */
        @With
        private Predicate<Object> onlyIf;

        /**
         * Always run even if a previous stage returned false to end the pipeline.
         * If just joining a grid, stages before the current one are a usually
         * not run, but those will this property set to <code>true</code> will.
         */
        @With
        private boolean always;

        /**
         * Code meant to run on one or more server nodes (when in a clustered
         * environment).
         */
        @NonNull
        private final GridTask task;
    }

    //    public void stop() {
    //        // TODO Auto-generated method stub
    //
    //    }
}
