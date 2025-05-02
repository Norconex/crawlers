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
package com.norconex.grid.local;

import static java.util.Optional.ofNullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.compute.pipeline.PipeExecutionContext;
import com.norconex.grid.core.impl.compute.pipeline.PipeUtil;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalPipelineCoordinator {
    private final GridMap<Integer> pipeActiveStages;
    private final Set<String> stopRequested = new CopyOnWriteArraySet<>();
    private final LocalGrid grid;

    public LocalPipelineCoordinator(LocalGrid grid) {
        this.grid = grid;
        pipeActiveStages = grid.getStorage().getMap(
                "pipeline-stage", Integer.class);
    }

    public void executePipeline(GridPipeline pipeline) {
        ensureValidePipeline(pipeline);

        var ctx = new PipeExecutionContext();
        ctx.setPipeline(pipeline);

        monitorStopRequest(ctx);

        ctx.setStartIndex(getStartingStageIndex(pipeline));
        for (var i = 0; i < pipeline.getStages().size(); i++) {
            ctx.setCurrentIndex(i);
            ctx.setActiveStage(
                    pipeline.getStages().get(ctx.getCurrentIndex()));
            ctx.setActiveTask(ctx.getActiveStage().getTask());
            executeStage(ctx);
        }
        // marking as complete
        pipeActiveStages.put(pipeline.getId(), -1);
    }

    public int getActiveStage(String pipelineId) {
        return ofNullable(pipeActiveStages.get(pipelineId)).orElse(-1);
    }

    public void stopPipeline(String pipelineId) {
        stopRequested.add(pipelineId);
    }

    private void monitorStopRequest(PipeExecutionContext ctx) {
        var stopMonitorScheduler = Executors.newScheduledThreadPool(1);
        stopMonitorScheduler.scheduleAtFixedRate(
                () -> {
                    if (!ctx.isStopRequested()) {
                        ctx.setStopRequested(stopRequested.remove(
                                ctx.getPipeline().getId()));
                        if (ctx.isStopRequested()) {
                            // the next stage directives will also prevent from
                            // running the next stage.
                            ctx.getActiveTask().stop();
                        }
                    }
                },
                0L, 1L, TimeUnit.SECONDS);
    }

    private void executeStage(PipeExecutionContext ctx) {
        var directives = PipeUtil.getStageDirectives(
                grid.getGridContext(), ctx);
        if (directives.isMarkActive()) {
            pipeActiveStages.put(
                    ctx.getPipeline().getId(), ctx.getCurrentIndex());
        }
        if (!directives.isSkip()) {
            try {
                var status = grid.getCompute().executeTask(ctx.getActiveTask());
                if (status.getState() != TaskState.COMPLETED) {
                    ctx.setFailedIndex(ctx.getCurrentIndex());
                    LOG.error("Pipeline {} stage index {} failed with "
                            + "status: {}",
                            ctx.getPipeline().getId(),
                            ctx.getCurrentIndex(),
                            status);
                }
            } catch (Exception e) {
                ctx.setFailedIndex(ctx.getCurrentIndex());
                LOG.error("Pipeline {} error running stage index {} for "
                        + "task {}",
                        ctx.getPipeline().getId(),
                        ctx.getCurrentIndex(),
                        ctx.getActiveTask().getId(), e);
            }
        }
    }

    private void ensureValidePipeline(GridPipeline pipeline) {
        if (pipeline.getStages().isEmpty()) {
            throw new GridException("Pipeline stage list must not be empty.");
        }
    }

    private int getStartingStageIndex(GridPipeline pipeline) {
        // We could be resuming or be a newly elected coordinator. Make
        // sure to start from the right stage
        int startIdx = ofNullable(
                pipeActiveStages.get(pipeline.getId())).orElse(0);
        if (startIdx > 0) {
            LOG.info("Unterminated pipeline execution detected for {}. "
                    + "Attempting to resume.", pipeline.getId());
        }
        // We consider -1 as a completed pipeline
        if (startIdx == -1) {
            LOG.info("Previous terminated execution detected for pipeline {}. "
                    + "Starting it again.", pipeline.getId());
            startIdx = 0;
        }
        return startIdx;
    }

}
