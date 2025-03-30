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

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.pipeline.GridPipelineStage;
import com.norconex.grid.core.pipeline.GridPipelineState;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class GridPipelineRunner<T> {

    private final List<GridPipelineStage<T>> stages =
            new ArrayList<>();
    private final String pipelineName;
    private final CoreGridPipeline pipeline;

    public GridPipelineRunner(
            CoreGridPipeline pipeline,
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
        // handle stopping here?  listen for stop event or force it on context?
        // else, let stages do it (or not).
        return ConcurrentUtil.call(() -> {
            var keepGoing = true;
            // In case we are just joining, get "remaining" stages
            for (var stage : getRemainingStages()) {
                pipeline.setState(pipelineName, GridPipelineState.ACTIVE);
                if ((keepGoing || stage.isAlways())
                        && (stage.getOnlyIf() == null
                                || stage.getOnlyIf().test(context))) {
                    keepGoing = runStage(stage, context) && keepGoing;
                } else {
                    LOG.info("Skipping stage \"{}\".", stage.getName());
                }
            }
            pipeline.setState(pipelineName, GridPipelineState.ENDED);
            return keepGoing;
        });
    }

    private boolean runStage(GridPipelineStage<T> stage, T context) {
        LOG.info("Pipeline now at stage \"{}\"", stage.getName());
        pipeline.setActiveStageName(pipelineName, stage.getName());

        var jobState = pipeline.getGrid().compute().runOn(
                stage.getRunOn(),
                stage.getName(),
                () -> stage.getTask().execute(context));

        if (jobState == GridJobState.FAILED) {
            LOG.info("Pipeline stage \"{}\" failed. Aborting.",
                    stage.getName());
            return false;
        }

        //            if (!ctx.isStopping()) {
        //                setStage(ctx, entry.getKey());
        //                entry.getValue().accept(this, ctx);
        //            }
        return true;
    }

    private List<GridPipelineStage<T>> getRemainingStages() {
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
        return stages.stream()
                .dropWhile(stage -> !Objects.equals(stageName, stage.getName()))
                .toList();
    }
}
