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
package com.norconex.crawler.core.cluster.pipeline;

import java.util.Objects;

/**
 * MXBean implementation delegating to PipelineManager#getPipelineProgress.
 */
public class PipelineProgressBean implements PipelineProgressMXBean {

    private final PipelineManager pipelineManager;
    private final String pipelineId;

    public PipelineProgressBean(PipelineManager pipelineManager, String pipelineId) {
        this.pipelineManager = Objects.requireNonNull(pipelineManager);
        this.pipelineId = Objects.requireNonNull(pipelineId);
    }

    private PipelineProgress snapshot() {
        try {
            return pipelineManager.getPipelineProgress(pipelineId);
        } catch (Exception e) {
            return PipelineProgress.builder().build();
        }
    }

    @Override
    public String getStatus() {
        var p = snapshot();
        return p.getStatus() != null ? p.getStatus().name() : null;
        }

    @Override
    public String getCurrentStepId() {
        return snapshot().getCurrentStepId();
    }

    @Override
    public int getCurrentStepIndex() {
        return snapshot().getCurrentStepIndex();
    }

    @Override
    public int getStepCount() {
        return snapshot().getStepCount();
    }

    @Override
    public float getStepProgress() {
        return snapshot().getStepProgress();
    }

    @Override
    public String getStepMessage() {
        return snapshot().getStepMessage();
    }
}
