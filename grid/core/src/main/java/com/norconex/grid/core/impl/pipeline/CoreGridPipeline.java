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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.jgroups.Address;

import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.pipeline.GridPipelineStage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a series of jobs on a grid, one after the other.
 */
@Slf4j
public class CoreGridPipeline extends BaseGridPipeline<CoreGrid> {

    public CoreGridPipeline(CoreGrid grid) {
        super(grid);
    }

    @Override
    public <T> Future<Boolean> run(
            @NonNull String pipelineName,
            @NonNull List<? extends GridPipelineStage<T>> pipelineStages,
            T context) {

        var runner = new GridPipelineRunner<>(
                this, pipelineName, pipelineStages);
        var pipeStopListener = new PipelineStopListener(runner);
        getGrid().addListener(pipeStopListener);
        return ((CompletableFuture<Boolean>) runner.run(context))
                .whenCompleteAsync((resp, ex) -> getGrid()
                        .removeListener(pipeStopListener));
    }

    @Override
    public void requestStop(String pipelineName) {
        getGrid().send(new StopPipelineMessage(pipelineName));
    }

    @RequiredArgsConstructor
    private class PipelineStopListener implements MessageListener {
        private final GridPipelineRunner<?> pipelineRunner;

        @Override
        public void onMessage(Object payload, Address from) {
            var pipeName = pipelineRunner.getPipelineName();
            StopPipelineMessage.onReceive(payload, pipeName, msg -> {
                LOG.info("Pipeline \"{}\" received a stop request "
                        + "during stage \"{}\".",
                        pipeName,
                        getActiveStageName(pipeName));
                pipelineRunner.stopRequested();
            });
        }
    }
}
