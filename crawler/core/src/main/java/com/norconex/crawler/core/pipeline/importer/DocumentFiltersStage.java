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
package com.norconex.crawler.core.pipeline.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlState;
import com.norconex.crawler.core.filter.IDocumentFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.OnMatchFilter;

/**
 *
 */
public class DocumentFiltersStage
        implements IPipelineStage<ImporterPipelineContext> {

    private static final Logger LOG =
            LoggerFactory.getLogger(DocumentFiltersStage.class);

    @Override
    public boolean execute(ImporterPipelineContext ctx) {
        var filters = ctx.getConfig().getDocumentFilters();
        if (filters == null) {
            return true;
        }

        var hasIncludes = false;
        var atLeastOneIncludeMatch = false;
        for (IDocumentFilter filter : filters) {
            var accepted = filter.acceptDocument(ctx.getDocument());

            // Deal with includes
            if (isIncludeFilter(filter)) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (!accepted) {
                ctx.fire(CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_FILTER)
                        .source(ctx.getCrawler())
                        .crawlDocRecord(ctx.getDocRecord())
                        .subject(filter)
                        .build());
                ctx.getDocRecord().setState(CrawlState.REJECTED);
                return false;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format(
                        "ACCEPTED document. Reference=%s Filter=%s", ctx
                            .getDocRecord().getReference(), filter));
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_FILTER)
                    .source(ctx.getCrawler())
                    .crawlDocRecord(ctx.getDocRecord())
                    .subject(filters)
                    .message("No \"include\" document filters matched.")
                    .build());
            ctx.getDocRecord().setState(CrawlState.REJECTED);
            return false;
        }
        return true;
    }

    private boolean isIncludeFilter(IDocumentFilter filter) {
        if (filter instanceof OnMatchFilter f) {
            var onMatch = OnMatch.includeIfNull(f.getOnMatch());
            return OnMatch.INCLUDE == onMatch;
        }
        return false;
    }
}