/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.queue;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.norconex.commons.lang.function.Predicates;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueuePipeline implements Consumer<QueuePipelineContext> {

    private final Predicates<QueuePipelineContext> stages;
    @Getter
    private final Function<QueuePipelineContext,
            ? extends QueuePipelineContext> contextAdapter;

    @Builder
    private QueuePipeline(
            @Singular
            @NonNull List<Predicate<QueuePipelineContext>> stages,
            Function<QueuePipelineContext,
                    ? extends QueuePipelineContext> contextAdapter) {
        this.stages = new Predicates<>(stages);
        this.contextAdapter = contextAdapter;
    }

    @Override
    public void accept(QueuePipelineContext context) {
        var ctx = contextAdapter != null
                ? contextAdapter.apply(context)
                : context;
        stages.test(ctx);
    }
}
