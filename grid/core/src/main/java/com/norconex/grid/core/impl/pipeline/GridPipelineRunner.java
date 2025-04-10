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
package com.norconex.grid.core.impl.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.pipeline.GridPipelineStage;
import com.norconex.grid.core.pipeline.GridPipelineState;
import com.norconex.grid.core.pipeline.GridPipelineTask;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GridPipelineRunner<T> {

    private final List<GridPipelineStage<T>> stages =
            new ArrayList<>();
    @Getter
    private final String pipelineName;
    private final BaseGridPipeline<?> pipeline;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicReference<GridPipelineTask<T>> activeTask =
            new AtomicReference<>();

    public GridPipelineRunner(
            BaseGridPipeline<?> pipeline,
            @NonNull String pipelineName,
            @NonNull List<? extends GridPipelineStage<T>> stages) {
        this.pipeline = pipeline;
        this.pipelineName = pipelineName;
        if (stages.isEmpty()) {
            throw new GridException("Pipeline stage list must not be empty.");
        }
        if (stages.stream()
                .map(GridPipelineStage::getName)
                .distinct()
                .count() != stages.size()) {
            throw new IllegalArgumentException(
                    "Two or more grid pipeline stage share the same name.");
        }
        this.stages.addAll(stages);
    }

    public Future<Boolean> run(T context) {
        return ConcurrentUtil.call(() -> {
            var keepGoing = true;
            // In case we are just joining, get "remaining" stages
            for (var stage : getAdjustedStages()) {
                // pipeline state has to be set on the starting stage
                // since the possible former state is used to establish
                // the remaining stages.
                pipeline.setState(pipelineName, GridPipelineState.ACTIVE);
                if (canProceed(context, keepGoing, stage)) {
                    keepGoing = runStage(stage, context) && keepGoing;
                } else {
                    LOG.info("Skipping stage \"{}\".", stage.getName());
                }
            }
            pipeline.setState(pipelineName, GridPipelineState.ENDED);
            return keepGoing;
        });

    }

    private boolean canProceed(
            T context, boolean keepGoing, GridPipelineStage<T> stage) {
        return ((keepGoing && !stopRequested.get()) || stage.isAlways())
                && (stage.getOnlyIf() == null
                        || stage.getOnlyIf().test(context));
    }

    private boolean runStage(GridPipelineStage<T> stage, T context) {
        LOG.info("Pipeline now at stage \"{}\"", stage.getName());
        pipeline.setActiveStageName(pipelineName, stage.getName());
        activeTask.set(stage.getTask());
        var stageResult = pipeline.getGrid().compute().runOn(
                stage.getRunOn(),
                stage.getName(),
                // the runnable here should accept stoppable....?
                () -> activeTask.get().execute(context));
        if (stageResult.getState() == GridComputeState.FAILED) {
            LOG.info("Pipeline stage \"{}\" failed. Aborting.",
                    stage.getName());
            return false;
        }
        return true;
    }

    private List<GridPipelineStage<T>> getAdjustedStages() {
        var pipeState = pipeline.getState(pipelineName);
        if (pipeState == GridPipelineState.IDLE) {
            LOG.info("Starting \"{}\" pipeline from begining.", pipelineName);
            return stages;
        }
        if (pipeState == GridPipelineState.ENDED) {
            LOG.info("Pipeline \"{}\" already ran, running it again.",
                    pipeline);
            return stages;
        }

        var stageName = pipeline.getActiveStageName(pipelineName)
                .orElse(stages.get(0).getName());
        LOG.info("Starting pipeline \"{}\" at stage \"{}\".",
                pipelineName, stageName);

        List<GridPipelineStage<T>> adjustedStages = new ArrayList<>();
        var caughtUp = false;
        for (GridPipelineStage<T> stage : stages) {
            if (caughtUp || Objects.equals(stageName, stage.getName())) {
                caughtUp = true;
                adjustedStages.add(stage);
            }
            if (stage.isAlways()) {
                adjustedStages.add(stage);
            }
        }
        return adjustedStages;
        //        return stages.stream()
        //                .dropWhile(stage -> !Objects.equals(stageName, stage.getName()))
        //                .toList();
    }

    public void stopRequested() {
        stopRequested.set(true);
        activeTask.get().stop();
        pipeline.getActiveStageName(pipelineName)
                .ifPresent(pipeline.getGrid().compute()::stop);
    }
}
