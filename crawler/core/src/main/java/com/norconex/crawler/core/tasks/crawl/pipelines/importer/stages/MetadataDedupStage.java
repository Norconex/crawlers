/* Copyright 2021-2024 Norconex Inc.
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

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same metadata checksum.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates. Invoked only
 * once (even if both metadata and document directives are enabled).
 */
@Slf4j
public class MetadataDedupStage extends AbstractImporterStage {

    public MetadataDedupStage(@NonNull FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())
                || ctx.isMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        var docContext = ctx.getDoc().getDocContext();
        var dedupService = ctx
                .getTaskContext()
                .getDedupService();

        var duplRef = dedupService.findOrTrackMetadata(docContext);

        if (duplRef.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "REJECTED duplicate metadata checkum found for: {}",
                        docContext.getReference());
            }
            docContext.setState(CrawlDocState.REJECTED);
            ctx.getTaskContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_DUPLICATE)
                            .source(ctx.getTaskContext())
                            .subject(duplRef.get())
                            .docContext(docContext)
                            .message(
                                    "A document with the same metadata checksum "
                                            + "was already processed: "
                                            + duplRef.get())
                            .build());
            return false;
        }
        return true;
    }
}