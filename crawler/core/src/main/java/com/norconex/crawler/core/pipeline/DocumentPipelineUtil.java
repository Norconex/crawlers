/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.OnMatch;
import com.norconex.crawler.core.filter.OnMatchFilter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods for document pipeline.
 */
@Slf4j
public final class DocumentPipelineUtil {

    private DocumentPipelineUtil() {}

    public static boolean isRejectedByMetadataFilters(
            DocumentPipelineContext ctx) {
        var filters = ctx.getConfig().getMetadataFilters();
        if (filters.isEmpty()) {
            return false;
        }
        var metadata = ctx.getDocument().getMetadata();
        var hasIncludes = false;
        var atLeastOneIncludeMatch = false;
        for (MetadataFilter filter : filters) {
            var accepted = filter.acceptMetadata(
                    ctx.getDocRecord().getReference(), metadata);
            var isInclude = filter instanceof OnMatchFilter onMatchFilter
                   && OnMatch.INCLUDE == onMatchFilter.getOnMatch();
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }
            if (!accepted) {
                ctx.fire(CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_FILTER)
                        .source(ctx.getCrawler())
                        .crawlDocRecord(ctx.getDocRecord())
                        .subject(filter)
                        .build());
                return true;
            }
            LOG.debug("ACCEPTED document metadata. Reference={} Filter={}",
                    ctx.getDocRecord().getReference(), filter);
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_FILTER)
                    .source(ctx.getCrawler())
                    .crawlDocRecord(ctx.getDocRecord())
                    .subject(filters)
                    .message("No \"include\" metadata filters matched.")
                    .build());
            return true;
        }
        return false;
    }

    public static boolean shouldAbortOnBadStatus(
            @NonNull DocumentPipelineContext ctx,
            CrawlDocState originalCrawlDocState,
            @NonNull FetchDirective fetchDirective) {
        // Note: a disabled directive should never get here,
        // and when both are enabled, DOCUMENT always comes after METADATA.
        var metaSupport = ctx.getConfig().getMetadataFetchSupport();
        var docSupport = ctx.getConfig().getDocumentFetchSupport();

        //--- HEAD ---
        if (FetchDirective.METADATA.is(fetchDirective)) {
            // if directive is required, we end it here.
            if (FetchDirectiveSupport.REQUIRED.is(metaSupport)) {
                return false;
            }
            // if head is optional and there is a GET, we continue
            return FetchDirectiveSupport.OPTIONAL.is(metaSupport)
                    && FetchDirectiveSupport.isEnabled(docSupport);

        //--- GET ---
        }
        if (FetchDirective.DOCUMENT.is(fetchDirective)) {
            // if directive is required, we end it here.
            if (FetchDirectiveSupport.REQUIRED.is(docSupport)) {
                return false;
            }
            // if directive is optional and HEAD was enabled and successful,
            // we continue
            return FetchDirectiveSupport.OPTIONAL.is(docSupport)
                    && FetchDirectiveSupport.isEnabled(metaSupport)
                    && CrawlDocState.isGoodState(originalCrawlDocState);
        }

        // At this point it would imply the directive for which we are asking
        // is disabled. It should not be possible to get a bad status
        // if disabled, so something is wrong, and we do not continue.
        return false;
    }
}
