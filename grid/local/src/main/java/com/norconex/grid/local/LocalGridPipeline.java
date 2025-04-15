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
package com.norconex.grid.local;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.norconex.grid.core.impl.pipeline.BaseGridPipeline;
import com.norconex.grid.core.impl.pipeline.GridPipelineRunner;
import com.norconex.grid.core.pipeline.GridPipelineStage;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a series of jobs on a grid, one after the other.
 */
@Slf4j
public class LocalGridPipeline extends BaseGridPipeline<LocalGrid> {

    private final Map<String, GridPipelineRunner<?>> activeRunners =
            new ConcurrentHashMap<>();

    public LocalGridPipeline(LocalGrid grid) {
        super(grid);
    }

    @Override
    public <T> Future<Boolean> run(
            @NonNull String pipelineName,
            @NonNull List<? extends GridPipelineStage<T>> pipelineStages,
            T context) {

        var runner = new GridPipelineRunner<>(
                this, pipelineName, pipelineStages);
        activeRunners.put(pipelineName, runner);
        return ((CompletableFuture<Boolean>) runner.run(context))
                .whenCompleteAsync(
                        (resp, ex) -> activeRunners.remove(pipelineName));

    }

    @Override
    public void stop(String pipelineName) {
        for (Entry<String, GridPipelineRunner<?>> entry : activeRunners
                .entrySet()) {
            if (pipelineName == null || entry.getKey().equals(pipelineName)) {
                entry.getValue().stopRequested();
            }
        }
    }

}
