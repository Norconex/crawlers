/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.queue;

import java.util.List;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.core.pipeline.queue.DepthValidationStage;
import com.norconex.crawler.core.pipeline.queue.QueuePipeline;
import com.norconex.crawler.core.pipeline.queue.QueueReferenceStage;
import com.norconex.crawler.core.pipeline.queue.ReferenceFiltersStage;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any
 * document or document header is downloaded.
 * Instances are only valid for the scope of a single URL.
 */
public final class WebQueuePipeline implements QueuePipeline {

    private static final Predicates<DocRecordPipelineContext> STAGES =
            new Predicates<>(List.of(
                    new DepthValidationStage(),
                    new ReferenceFiltersStage(),
                    new RobotsTxtFiltersStage(),
                    new URLNormalizerStage(),
                    new SitemapResolutionStage(),
                    new QueueReferenceStage()
            ));

    @Override
    public void accept(DocRecordPipelineContext ctx) {
        STAGES.test(ctx);
    }
}

