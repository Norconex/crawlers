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
package com.norconex.crawler.core.tasks.crawl.pipelines.importer.stages;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.tasks.crawl.operations.filter.MetadataFilter;
import com.norconex.crawler.core.tasks.crawl.pipelines.OnMatchFiltersResolver;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Filters documents based on fetched metadata. These filters are invoked
 * only once (even if both metadata and document directives are enabled).
 */
@Slf4j
public class MetadataFiltersStage extends AbstractImporterStage {

    public MetadataFiltersStage(@NonNull FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {

        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())
                || ctx.isMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        return OnMatchFiltersResolver
                .<Properties, MetadataFilter>builder()
                .subject(ctx.getDoc().getMetadata())
                .filters(ctx.getCrawlerContext().getConfiguration()
                        .getMetadataFilters())
                .predicate((s, f) -> f
                        .acceptMetadata(ctx.getDoc().getReference(), s))
                .onRejected((f, msg) -> {
                    LOG.debug("REJECTED metadata. Reference: {} Filter={}",
                            ctx.getDoc().getDocContext().getReference(), f);
                    ctx.getCrawlerContext().fire(
                            CrawlerEvent.builder()
                                    .name(CrawlerEvent.REJECTED_FILTER)
                                    .source(ctx.getCrawlerContext())
                                    .docContext(ctx.getDoc().getDocContext())
                                    .subject(f)
                                    .message(msg)
                                    .build());
                    ctx.getDoc().getDocContext()
                            .setState(DocResolutionStatus.REJECTED);
                })
                .build()
                .isAccepted();
    }
}
