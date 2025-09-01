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

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for pipeline execution.
 */
public interface PipelineManager {

    /**
     * Executes a pipeline.
     * @param pipeline the pipeline to execute
     * @return a future containing the pipeline execution result
     */
    CompletableFuture<PipelineResult> executePipeline(Pipeline pipeline);

    /**
     * Stops a pipeline execution.
     * @param pipelineId id of the pipeline to stop
     * @return a future triggered when stopped
     */
    CompletableFuture<Void> stopPipeline(String pipelineId);

}
