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
package com.norconex.grid.core.impl.pipeline;

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.pipeline.GridPipelineStage;
import com.norconex.grid.core.pipeline.GridPipelineState;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a series of jobs on a grid, one after the other.
 */
@Slf4j
@RequiredArgsConstructor
public class CoreGridPipeline implements GridPipeline {

    private static final String STAGE_KEY_PREFIX = "PipelineStage-";
    private static final String STATE_KEY_PREFIX = "PipelineState-";

    private final CoreGrid grid;

    @Override
    public <T> Future<Boolean> run(@NonNull String pipelineName,
            @NonNull List<? extends GridPipelineStage<T>> pipelineStages,
            T context) {
        return new GridPipelineRunner<>(
                this, pipelineName, pipelineStages).run(context);
    }

    @Override
    public Optional<String> getActiveStageName(@NonNull String pipelineName) {
        return ofNullable(grid
                .storage()
                .getGlobals()
                .get(STAGE_KEY_PREFIX + pipelineName));
    }

    @Override
    public GridPipelineState getState(@NonNull String pipelineName) {
        return GridPipelineState.of(grid
                .storage()
                .getGlobals()
                .get(STATE_KEY_PREFIX + pipelineName));
    }

    CoreGrid getGrid() {
        return grid;
    }

    void setActiveStageName(@NonNull String pipelineName, String name) {
        grid.storage().getGlobals().put(
                STAGE_KEY_PREFIX + pipelineName, name);
    }

    void setState(@NonNull String pipelineName, GridPipelineState state) {
        grid.storage().getGlobals().put(
                STATE_KEY_PREFIX + pipelineName, state.name());
    }

    @Override
    public Future<Void> stop(String pipelineName) {
        return CompletableFuture.runAsync(
                () -> grid.send(new StopPipelineMessage(pipelineName)));
    }

}
