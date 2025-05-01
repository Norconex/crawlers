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
package com.norconex.grid.core.impl.compute.work;

import static java.util.Optional.ofNullable;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridPipeline.Stage;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.CoreCompute;
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
                "pipeline-stage", Integer.class);
    }

    public void executePipeline(GridPipeline pipeline) throws Exception {
        var ctx = new PipeExecutionContext();
        ctx.pipeline = pipeline;

        if (!grid.isCoordinator()) {
            LOG.debug("Non-coordinator node, waiting for "
                    + "coordinator for instructions.");
            while (!localWorker.isPipelineDone(pipeline.getId())
                    && !ctx.stopRequested) {
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

            ctx.startIndex = getStartingStageIndex(pipeline);
            for (var i = 0; i < pipeline.getStages().size(); i++) {
                ctx.currentIndex = i;
                ctx.activeStage = pipeline.getStages().get(ctx.currentIndex);
                ctx.activeTask = ctx.activeStage.getTask();
                executeStage(ctx);
            }
            // marking as complete
            pipeActiveStages.put(pipeline.getId(), -1);
        } finally {
            dispatcher.setPipelineDoneOnNodes(pipeline.getId());
        }
    }

    private void executeStage(PipeExecutionContext ctx) {
        var directives = getStageDirectives(ctx);
        if (directives.markActive) {
            pipeActiveStages.put(ctx.pipeline.getId(), ctx.currentIndex);
        }
        if (!directives.skip) {
            try {
                var status = grid.getCompute().executeTask(ctx.activeTask);
                if (status.getState() != TaskState.COMPLETED) {
                    ctx.failedIndex = ctx.currentIndex;
                    LOG.error("Pipeline {} stage index {} failed with "
                            + "status: {}",
                            ctx.pipeline.getId(),
                            ctx.currentIndex,
                            status);
                }
            } catch (Exception e) {
                ctx.failedIndex = ctx.currentIndex;
                LOG.error("Pipeline {} error running stage index {} for "
                        + "task {}",
                        ctx.pipeline.getId(),
                        ctx.currentIndex,
                        ctx.activeTask.getId(), e);
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

    private static class PipeExecutionContext {
        private GridPipeline pipeline;
        private Stage activeStage;
        private GridTask activeTask;
        private boolean stopRequested;
        private int currentIndex;
        private int startIndex;
        private int failedIndex = -1;
    }

    private void monitorStopRequest(PipeExecutionContext ctx) {
        var stopMonitorScheduler = Executors.newScheduledThreadPool(1);
        stopMonitorScheduler.scheduleAtFixedRate(
                () -> {
                    if (!ctx.stopRequested) {
                        ctx.stopRequested = localWorker.isPipelineStopRequested(
                                ctx.pipeline.getId());
                        if (ctx.stopRequested) {
                            try {
                                dispatcher.stopTaskOnNodes(
                                        ctx.activeTask.getId());
                            } catch (Exception e) {
                                throw new GridException("Could not dispatch "
                                        + "stop request for pipeline "
                                        + ctx.pipeline.getId(), e);
                            }
                        }
                    }
                },
                0L, 1L, TimeUnit.SECONDS);
    }

    private static class StageDirectives {
        private boolean skip;
        private boolean markActive;
    }

    private StageDirectives getStageDirectives(PipeExecutionContext ctx) {

        var directives = new StageDirectives();
        if (ctx.stopRequested) {
            if (ctx.activeStage.isAlways()) {
                LOG.info("""
                        Pipeline {} has received request to stop but stage \
                        index {} for task {} is marked as "always" run. \
                        Running  it without affecting which stage is the \
                        'active' one.""",
                        ctx.pipeline.getId(),
                        ctx.currentIndex,
                        ctx.activeTask.getId());
                return directives;
            }
            LOG.info("""
                    Pipeline {} stage index {} for task {} is skipped as \
                    pipeline is being stopped.""",
                    ctx.pipeline.getId(),
                    ctx.currentIndex,
                    ctx.activeTask.getId());
            directives.skip = true;
            return directives;
        }

        if (ctx.currentIndex < ctx.startIndex) {
            if (ctx.activeStage.isAlways()) {
                LOG.info("""
                        Pipeline {} stage index {} for task {} already ran, \
                        but marked as "always" run. Running it \
                        without affecting which stage is the 'active' one.""",
                        ctx.pipeline.getId(),
                        ctx.currentIndex,
                        ctx.activeTask.getId());
                return directives;
            }
            LOG.info("""
                        Pipeline {} stage index {} for task {} already ran. \
                        Skipping it.""",
                    ctx.pipeline.getId(),
                    ctx.currentIndex,
                    ctx.activeTask.getId());
            directives.skip = true;
            return directives;
        }

        if (ctx.failedIndex > -1) {
            if (ctx.activeStage.isAlways()) {
                LOG.info("""
                        Pipeline {} stage index {} failed but stage index {} \
                        for task {} is marked as "always" run. Running it \
                        without affecting which stage is the 'active' one.""",
                        ctx.pipeline.getId(),
                        ctx.failedIndex,
                        ctx.currentIndex,
                        ctx.activeTask.getId());
                return directives;
            }
            LOG.info("""
                        Pipeline {} stage index {} failed. Skipping stage \
                        index {} for task {}.""",
                    ctx.pipeline.getId(),
                    ctx.failedIndex,
                    ctx.currentIndex,
                    ctx.activeTask.getId());
            directives.skip = true;
            return directives;
        }

        if (ctx.activeStage.getOnlyIf() != null
                && !ctx.activeStage.getOnlyIf().test(grid.getGridContext())) {
            LOG.info("""
                    Pipeline {} stage index {} for task {} did not meet \
                    "onlyIf" condition. Skipping it.""",
                    ctx.pipeline.getId(),
                    ctx.currentIndex,
                    ctx.activeTask.getId());
            directives.markActive = true;
            directives.skip = true;
            return directives;
        }
        directives.markActive = true;
        return directives;
        //TODO Check if stage is already completed on top of it?
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
