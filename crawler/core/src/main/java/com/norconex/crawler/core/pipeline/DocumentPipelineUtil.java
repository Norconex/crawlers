/* Copyright 2020-2022 Norconex Inc.
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

import java.util.List;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.OnMatchFilter;

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
            var isInclude = filter instanceof OnMatchFilter
                   && OnMatch.INCLUDE == ((OnMatchFilter) filter).getOnMatch();
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
}
