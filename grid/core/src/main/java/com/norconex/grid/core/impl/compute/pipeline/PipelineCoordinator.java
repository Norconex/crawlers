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
package com.norconex.grid.core.impl.compute.pipeline;

import static java.util.Optional.ofNullable;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.CoreCompute;
import com.norconex.grid.core.impl.compute.WorkDispatcher;
import com.norconex.grid.core.impl.compute.Worker;
import com.norconex.grid.core.impl.compute.task.TaskCoordinator;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator {

    private final CoreGrid grid;
    private final WorkDispatcher dispatcher;
    private final Worker localWorker;

    private final GridMap<Integer> pipeActiveStages;

    public PipelineCoordinator(CoreCompute compute) {
        grid = compute.getGrid();
        localWorker = compute.getLocalWorker();
        dispatcher = compute.getDispatcher();
        pipeActiveStages = grid.getStorage().getMap(
                "pipeline_stage", Integer.class);
    }

    public void executePipeline(GridPipeline pipeline) throws Exception {
        var ctx = new PipeExecutionContext();
        ctx.setPipeline(pipeline);

        if (!grid.isCoordinator()) {
            LOG.debug("Non-coordinator node, waiting for "
                    + "coordinator for instructions.");
            while (!localWorker.isPipelineDone(pipeline.getId())
                    && !ctx.isStopRequested()) {
                Thread.sleep(TaskCoordinator.POLLING_INTERVAL_MS);
                //TODO have configurable timeout here?
                //TODO check for expiry here, either pipeline expiry
                // or based on active task.
            }
            return;
        }

        try {
            ensureValidePipeline(pipeline);

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
        } finally {
            dispatcher.setPipelineDoneOnNodes(pipeline.getId());
        }
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

    public void stopPipeline(String pipelineId) throws Exception {
        dispatcher.stopPipeline(pipelineId);
    }

    // -1 if no active stage
    public int getActiveStageIndex(String pipelineId) {
        return ofNullable(pipeActiveStages.get(pipelineId)).orElse(-1);
    }

    private void monitorStopRequest(PipeExecutionContext ctx) {
        var stopMonitorScheduler = Executors.newScheduledThreadPool(1);
        stopMonitorScheduler.scheduleAtFixedRate(
                () -> {
                    if (!ctx.isStopRequested()) {
                        ctx.setStopRequested(
                                localWorker.isPipelineStopRequested(
                                        ctx.getPipeline().getId()));
                        if (ctx.isStopRequested()) {
                            try {
                                dispatcher.stopTaskOnNodes(
                                        ctx.getActiveTask().getId());
                            } catch (Exception e) {
                                throw new GridException("Could not dispatch "
                                        + "stop request for pipeline "
                                        + ctx.getPipeline().getId(), e);
                            }
                        }
                    }
                },
                0L, 1L, TimeUnit.SECONDS);
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

    private void ensureValidePipeline(GridPipeline pipeline) {
        if (pipeline.getStages().isEmpty()) {
            throw new GridException("Pipeline stage list must not be empty.");
        }
    }

}
