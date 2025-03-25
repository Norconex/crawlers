/* Copyright 2024 Norconex Inc.
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
import java.util.Optional;
import java.util.concurrent.Future;

import lombok.NonNull;

public interface GridPipeline {
    /**
     * Runs a pipeline in multiple stages. Each stage holds a task to be
     * executed on one or all nodes. Any node joining will join at the currently
     * executing stage or wait for the next one eligible for joining.
     * Stages are run at the same time. A node completing a stage earlier
     * than others will wait for others to return.
     * Any of the stages returning <code>false</code> will stop the pipeline
     * for all nodes.
     * @param <T> type of context argument passed to each stages
     * @param pipelineName pipeline name
     * @param pipelineStages pipeline stages
     * @param context context argument passed to each stages
     * @return <code>true</code> if all stages ran
     */
    <T> Future<Boolean> run(
            @NonNull String pipelineName,
            @NonNull List<? extends GridPipelineStage<T>> pipelineStages,
            T context);

    /**
     * Returns the active stage name.
     * @param pipelineName pipeline name
     * @return the active stage name
     */
    Optional<String> getActiveStageName(@NonNull String pipelineName);

    /**
     * Returns the active stage name.
     * @param pipelineName pipeline name
     * @return the active stage name
     */
    GridPipelineState getState(@NonNull String pipelineName);

}
