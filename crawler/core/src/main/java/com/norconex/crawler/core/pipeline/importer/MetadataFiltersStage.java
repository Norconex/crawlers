/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.importer;

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.pipeline.DocumentPipelineUtil;

import lombok.NonNull;

/**
 * Filters documents based on fetched metadata. These filters are invoked
 * only once (even if both metadata and document directives are enabled).
 */
public class MetadataFiltersStage extends AbstractImporterStage {

    public MetadataFiltersStage(@NonNull FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        if (ctx.wasMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        if (DocumentPipelineUtil.isRejectedByMetadataFilters(ctx)) {
            ctx.getDocRecord().setState(CrawlDocState.REJECTED);
            return false;
        }
        return true;
    }
}
