/* Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.pipelines.OnMatchFiltersResolver;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DocumentFiltersStage
        implements Predicate<ImporterPipelineContext> {

    @Override
    public boolean test(ImporterPipelineContext ctx) {
        var filters =
                ctx.getCrawlContext().getCrawlConfig().getDocumentFilters();
        var doc = ctx.getDoc();

        return OnMatchFiltersResolver
                .<CrawlDoc, DocumentFilter>builder()
                .subject(doc)
                .filters(filters)
                .predicate((s, f) -> f.acceptDocument(s))
                .onRejected((f, msg) -> {
                    LOG.debug("REJECTED document. Reference: {} Filter={}",
                            doc.getDocContext().getReference(), f);
                    ctx.getCrawlContext().fire(
                            CrawlerEvent.builder()
                                    .name(CrawlerEvent.REJECTED_FILTER)
                                    .source(ctx.getCrawlContext())
                                    .docContext(doc.getDocContext())
                                    .subject(f)
                                    .message(msg)
                                    .build());
                    doc.getDocContext().setState(CrawlDocStatus.REJECTED);
                })
                .build()
                .isAccepted();
    }
}
