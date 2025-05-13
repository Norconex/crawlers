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
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.compute.pipeline.PipeExecutionContext;
import com.norconex.grid.core.impl.compute.pipeline.PipeUtil;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalPipelineCoordinator {
    private final GridMap<Integer> pipeActiveStage;
    private final GridMap<String> pipeLastStageResult;
    private final Set<String> stopRequested = new CopyOnWriteArraySet<>();
    private final LocalGrid grid;

    public LocalPipelineCoordinator(LocalGrid grid) {
        this.grid = grid;
        pipeActiveStage = grid.getStorage().getMap(
                "pipeline-stage", Integer.class);
        pipeLastStageResult = grid.getStorage().getMap(
                "pipelineLastResult", String.class);
    }

    public TaskExecutionResult executePipeline(GridPipeline pipeline) {
        ensureValidePipeline(pipeline);

        var pipeCtx = new PipeExecutionContext();
        pipeCtx.setPipeline(pipeline);

        monitorStopRequest(pipeCtx);

        pipeCtx.setStartIndex(getStartingStageIndex(pipeline));
        for (var i = 0; i < pipeline.getStages().size(); i++) {
            pipeCtx.setCurrentIndex(i);
            pipeCtx.setActiveStage(
                    pipeline.getStages().get(pipeCtx.getCurrentIndex()));
            pipeCtx.setActiveTask(pipeCtx
                    .getActiveStage()
                    .getTaskProvider()
                    .get(grid, pipeCtx.getLastStageResult()));
            executeStage(pipeCtx);
        }
        // marking as complete
        pipeActiveStage.put(pipeline.getId(), -1);
        pipeLastStageResult.delete(pipeline.getId());
        return pipeCtx.getLastStageResult();
    }

    public int getActiveStage(String pipelineId) {
        return ofNullable(pipeActiveStage.get(pipelineId)).orElse(-1);
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

    private TaskExecutionResult executeStage(PipeExecutionContext pipeCtx) {
        var directives = PipeUtil.getStageDirectives(pipeCtx);
        if (directives.isMarkActive()) {
            pipeActiveStage.put(
                    pipeCtx.getPipeline().getId(), pipeCtx.getCurrentIndex());
        }
        TaskExecutionResult result = null;
        if (!directives.isSkip()) {
            try {
                result = grid.getCompute().executeTask(pipeCtx.getActiveTask());
                if (result.getState() != TaskState.COMPLETED) {
                    pipeCtx.setFailedIndex(pipeCtx.getCurrentIndex());
                    LOG.error("Pipeline {} stage index {} failed with "
                            + "status: {}",
                            pipeCtx.getPipeline().getId(),
                            pipeCtx.getCurrentIndex(),
                            result);
                }
                // store task result in case we need it for resuming
                pipeCtx.setLastStageResult(result);
                pipeLastStageResult.put(
                        pipeCtx.getPipeline().getId(),
                        SerialUtil.toBase64String(result));
            } catch (Exception e) {
                pipeCtx.setFailedIndex(pipeCtx.getCurrentIndex());
                LOG.error("Pipeline {} error running stage index {} for "
                        + "task {}",
                        pipeCtx.getPipeline().getId(),
                        pipeCtx.getCurrentIndex(),
                        pipeCtx.getActiveTask().getId(), e);
            }
        }
        return result;
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
                pipeActiveStage.get(pipeline.getId())).orElse(0);
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
