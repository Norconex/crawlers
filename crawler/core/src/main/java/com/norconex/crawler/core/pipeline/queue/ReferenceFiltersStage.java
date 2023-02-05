/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.queue;

import java.util.function.Predicate;

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;

/**
 * Common pipeline stage for filtering references.
 */
public class ReferenceFiltersStage
        implements Predicate<DocRecordPipelineContext> {

    private final String type;

    public ReferenceFiltersStage() {
        this(null);
    }
    public ReferenceFiltersStage(String type) {
        this.type = type;
    }

    @Override
    public boolean test(DocRecordPipelineContext ctx) {
        if (ReferenceFiltersStageUtil.resolveReferenceFilters(
                ctx.getConfig().getReferenceFilters(), ctx, type)) {
            ctx.getDocRecord().setState(CrawlDocState.REJECTED);
            return false;
        }
        return true;
    }
}
