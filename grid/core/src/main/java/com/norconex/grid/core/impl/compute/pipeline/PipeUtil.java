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

import com.norconex.grid.core.GridContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PipeUtil {

    private PipeUtil() {
    }

    public static PipeStageDirectives getStageDirectives(
            GridContext gridContext, PipeExecutionContext pipeCtx) {

        var directives = new PipeStageDirectives();
        if (pipeCtx.isStopRequested()) {
            if (pipeCtx.getActiveStage().isAlways()) {
                LOG.info("""
                        Pipeline {} has received request to stop but stage \
                        index {} for task {} is marked as "always" run. \
                        Running  it without affecting which stage is the \
                        'active' one.""",
                        pipeCtx.getPipeline().getId(),
                        pipeCtx.getCurrentIndex(),
                        pipeCtx.getActiveTask().getId());
                return directives;
            }
            LOG.info("""
                    Pipeline {} stage index {} for task {} is skipped as \
                    pipeline is being stopped.""",
                    pipeCtx.getPipeline().getId(),
                    pipeCtx.getCurrentIndex(),
                    pipeCtx.getActiveTask().getId());
            directives.setSkip(true);
            return directives;
        }

        if (pipeCtx.getCurrentIndex() < pipeCtx.getStartIndex()) {
            if (pipeCtx.getActiveStage().isAlways()) {
                LOG.info("""
                        Pipeline {} stage index {} for task {} already ran, \
                        but marked as "always" run. Running it \
                        without affecting which stage is the 'active' one.""",
                        pipeCtx.getPipeline().getId(),
                        pipeCtx.getCurrentIndex(),
                        pipeCtx.getActiveTask().getId());
                return directives;
            }
            LOG.info("""
                        Pipeline {} stage index {} for task {} already ran. \
                        Skipping it.""",
                    pipeCtx.getPipeline().getId(),
                    pipeCtx.getCurrentIndex(),
                    pipeCtx.getActiveTask().getId());
            directives.setSkip(true);
            return directives;
        }

        if (pipeCtx.getFailedIndex() > -1) {
            if (pipeCtx.getActiveStage().isAlways()) {
                LOG.info("""
                        Pipeline {} stage index {} failed but stage index {} \
                        for task {} is marked as "always" run. Running it \
                        without affecting which stage is the 'active' one.""",
                        pipeCtx.getPipeline().getId(),
                        pipeCtx.getFailedIndex(),
                        pipeCtx.getCurrentIndex(),
                        pipeCtx.getActiveTask().getId());
                return directives;
            }
            LOG.info("""
                        Pipeline {} stage index {} failed. Skipping stage \
                        index {} for task {}.""",
                    pipeCtx.getPipeline().getId(),
                    pipeCtx.getFailedIndex(),
                    pipeCtx.getCurrentIndex(),
                    pipeCtx.getActiveTask().getId());
            directives.setSkip(true);
            return directives;
        }

        if (pipeCtx.getActiveStage().getOnlyIf() != null
                && !pipeCtx.getActiveStage().getOnlyIf().test(gridContext)) {
            LOG.info("""
                    Pipeline {} stage index {} for task {} did not meet \
                    "onlyIf" condition. Skipping it.""",
                    pipeCtx.getPipeline().getId(),
                    pipeCtx.getCurrentIndex(),
                    pipeCtx.getActiveTask().getId());
            directives.setMarkActive(true);
            directives.setSkip(true);
            return directives;
        }
        directives.setMarkActive(true);
        return directives;
        //TODO Check if stage is already completed on top of it?
    }
}
