/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.commands.crawl.task.pipelines.committer;

import java.util.function.Consumer;
import java.util.function.Function;

import com.norconex.commons.lang.function.Predicates;

import lombok.Builder;
import lombok.Getter;

public class CommitterPipeline implements Consumer<CommitterPipelineContext> {

    private final Predicates<CommitterPipelineContext> stages;
    @Getter
    private final Function<CommitterPipelineContext,
            ? extends CommitterPipelineContext> contextAdapter;

    @Builder
    private CommitterPipeline(
            Predicates<CommitterPipelineContext> stages,
            Function<CommitterPipelineContext,
                    ? extends CommitterPipelineContext> contextAdapter) {
        this.stages = stages;
        this.contextAdapter = contextAdapter;
    }

    @Override
    public void accept(CommitterPipelineContext context) {
        var ctx = contextAdapter != null
                ? contextAdapter.apply(context)
                : context;
        stages.test(ctx);
    }
}
