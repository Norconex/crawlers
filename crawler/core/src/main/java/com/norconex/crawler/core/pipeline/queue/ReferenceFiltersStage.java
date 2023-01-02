/* Copyright 2014-2022 Norconex Inc.
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

import com.norconex.crawler.core.doc.CrawlState;
import com.norconex.crawler.core.pipeline.DocInfoPipelineContext;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * Common pipeline stage for filtering references.
 */
public class ReferenceFiltersStage
        implements IPipelineStage<DocInfoPipelineContext> {

    private final String type;

    public ReferenceFiltersStage() {
        this(null);
    }
    public ReferenceFiltersStage(String type) {
        super();
        this.type = type;
    }

    @Override
    public boolean execute(DocInfoPipelineContext ctx) {
        if (ReferenceFiltersStageUtil.resolveReferenceFilters(
                ctx.getConfig().getReferenceFilters(), ctx, type)) {
            ctx.getDocRecord().setState(CrawlState.REJECTED);
            return false;
        }
        return true;
    }
}
