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
package com.norconex.grid.core.pipeline;

import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.impl.CoreGridPipeline;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class GridPipelineRunner<T> {

    private final ListOrderedMap<String, GridPipelineStage<T>> stages =
            new ListOrderedMap<>();
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
        stages.stream()
                .forEach(stage -> this.stages.put(stage.getName(), stage));
    }

    public Future<Boolean> run(T context) {
        // handle stopping here?  listen for stop event or force it on context?
        // else, let stages do it (or not).
        return ConcurrentUtil.call(() -> {
            //            var pipeCtx = new PipelineContext<>(this);
            var firstStage = getFirstStage();

            var abort = false;
            var caughtUp = false;
            for (var stage : stages.valueList()) {
                // In case we are just joining, start at grid current stage.
                if (!caughtUp && firstStage != stage) { // != is OK here.
                    continue;
                }
                if (!caughtUp) {
                    caughtUp = true;
                    pipeline.setState(pipelineName, GridPipelineState.ACTIVE);
                }

                if ((!abort || stage.isAlways())
                        && (stage.getOnlyIf() == null
                                || stage.getOnlyIf().test(context))) {
                    LOG.info("Pipeline now at stage \"{}\"", stage.getName());
                    //TODO set in runOnOne?
                    pipeline.setActiveStageName(pipelineName, stage.getName());

                    var keepGoingFuture =
                            pipeline.getGrid().compute().runOn(
                                    stage.getRunOn(),
                                    stage.getName(),
                                    () -> stage.getTask().execute(context));

                    if (Boolean.FALSE.equals(keepGoingFuture.get())) {
                        LOG.info("Pipeline ended by stage \"{}\".",
                                stage.getName());
                        abort = true;
                    }

                    //            if (!ctx.isStopping()) {
                    //                setStage(ctx, entry.getKey());
                    //                entry.getValue().accept(this, ctx);
                    //            }

                }
            }
            //TODO set in runOnOne?
            pipeline.setState(pipelineName, GridPipelineState.ENDED);
            return !abort;
        });
    }

    private GridPipelineStage<? extends T> getFirstStage() {
        var pipeState = pipeline.getState(pipelineName);
        if (pipeState == GridPipelineState.IDLE) {
            LOG.info("Starting \"{}\" pipeline from begining.", pipelineName);
            return stages.getValue(0);
        }
        if (pipeState == GridPipelineState.ENDED) {
            LOG.info("Pipeline \"{}\" already ran, running it again.",
                    pipeline);
            return stages.getValue(0);
        }
        var stageName = pipeline.getActiveStageName(pipelineName)
                .orElseGet(stages::firstKey);
        LOG.info("Starting pipeline \"{}\" at stage \"{}\".",
                pipelineName, stageName);
        return stages.get(stageName);
    }

}
