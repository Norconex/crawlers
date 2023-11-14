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

import static com.norconex.crawler.core.crawler.CrawlerEvent.REJECTED_FILTER;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.filter.OnMatch;
import com.norconex.crawler.core.filter.OnMatchFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Reference-filtering stage utility methods.
 */
@Slf4j
public final class ReferenceFiltersStageUtil {

    private ReferenceFiltersStageUtil() {}

    // return true if reference is rejected
    public static boolean resolveReferenceFilters(
            @NonNull List<ReferenceFilter> filters,
            DocRecordPipelineContext ctx, String type) {
        var msg = StringUtils.trimToEmpty(type);
        if (StringUtils.isNotBlank(msg)) {
            msg = " (" + msg + ")";
        }

        String ref = ctx.getDocRecord().getReference();
        var hasIncludes = false;
        var atLeastOneIncludeMatch = false;
        for (ReferenceFilter filter : filters) {
            var accepted = filter.acceptReference(ref);

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
                LOG.debug("REJECTED reference{}: {}"
                        + " Filter={}", msg, ref, filter);
                fireDocumentRejected(filter, ctx);
                return true;
            }
            LOG.debug("ACCEPTED reference{}: {}"
                    + " Filter={}", msg, ref, filter);
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            LOG.debug("""
                REJECTED document reference{}\
                . No include filters matched. Reference={}\
                 Filter=[one or more filter 'onMatch'\s\
                attribute is set to 'include', but none of them were\s\
                matched]""", msg, ref);
            fireDocumentRejected(
                    "No \"include\" reference filters matched.", ctx);
            return true;
        }
        return false;
    }

    private static void fireDocumentRejected(
            Object subject, DocRecordPipelineContext ctx) {
        ctx.fire(CrawlerEvent.builder()
                .name(REJECTED_FILTER)
                .source(ctx.getCrawler())
                .crawlDocRecord(ctx.getDocRecord())
                .subject(subject)
                .build());
    }

    private static boolean isIncludeFilter(ReferenceFilter filter) {
        return filter instanceof OnMatchFilter f
                && OnMatch.INCLUDE == f.getOnMatch();
    }
}
