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
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.CoreCompute;
import com.norconex.grid.core.impl.compute.WorkDispatcher;
import com.norconex.grid.core.impl.compute.Worker;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator {

    private final CoreGrid grid;
    private final WorkDispatcher dispatcher;
    private final Worker localWorker;

    //TODO change this to store the index but also the previous stage result?
    private final GridMap<Integer> pipeActiveStage;
    private final GridMap<String> pipeLastStageResult;

    public PipelineCoordinator(CoreCompute compute) {
        grid = compute.getGrid();
        localWorker = compute.getLocalWorker();
        dispatcher = compute.getDispatcher();
        pipeActiveStage = grid.getStorage().getMap(
                "pipelineActiveStage", Integer.class);
        pipeLastStageResult = grid.getStorage().getMap(
                "pipelineLastResult", String.class);
    }

    public TaskExecutionResult executePipeline(GridPipeline pipeline)
            throws Exception {
        var pipeCtx = new PipeExecutionContext();
        pipeCtx.setPipeline(pipeline);

        if (!grid.isCoordinator()) {
            LOG.debug("Non-coordinator node, waiting for "
                    + "coordinator for instructions.");
            TaskExecutionResult result = null;

            while ((result = localWorker.getPipelineDone(
                    pipeline.getId())) == null) {
                Thread.sleep(grid.getConnectorConfig().getHeartbeatInterval()
                        .toMillis());
                //TODO check for expiry here? either pipeline expiry
                // or based on active task.
            }
            return result;
        }

        try {
            ensureValidePipeline(pipeline);
            monitorStopRequest(pipeCtx);

            pipeCtx.setStartIndex(getStartingStageIndex(pipeline));
            pipeCtx.setLastStageResult(
                    (TaskExecutionResult) SerialUtil.fromBase64String(
                            pipeLastStageResult.get(pipeline.getId())));
            for (var i = 0; i < pipeline.getStages().size(); i++) {
                pipeCtx.setCurrentIndex(i);
                pipeCtx.setActiveStage(pipeline.getStages()
                        .get(pipeCtx.getCurrentIndex()));
                pipeCtx.setActiveTask(pipeCtx
                        .getActiveStage()
                        .getTaskProvider()
                        .get(grid, pipeCtx.getLastStageResult()));
                executeStage(pipeCtx);
            }
            // marking as complete
            pipeActiveStage.put(pipeline.getId(), -1);
            pipeLastStageResult.delete(pipeline.getId());
        } finally {
            dispatcher.setPipelineDoneOnNodes(
                    pipeline.getId(), pipeCtx.getLastStageResult());
        }
        return pipeCtx.getLastStageResult();
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
                            + "result: {}",
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

    public void stopPipeline(String pipelineId) throws Exception {
        dispatcher.stopPipeline(pipelineId);
    }

    // -1 if no active stage
    public int getActiveStageIndex(String pipelineId) {
        return ofNullable(pipeActiveStage.get(pipelineId)).orElse(-1);
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

    private void ensureValidePipeline(GridPipeline pipeline) {
        if (pipeline.getStages().isEmpty()) {
            throw new GridException("Pipeline stage list must not be empty.");
        }
    }

}
