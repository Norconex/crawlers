/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import static com.norconex.collector.core.crawler.CrawlerEvent.REJECTED_FILTER;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.core.filter.IMetadataFilter;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Perform filtering of documents based on document metadata.
 * The metadata typically consists of HTTP response headers and
 * collector-generated metadata.
 *
 * This stage is only executing filters once, from a HEAD request if
 * configured to perform a separate HEAD request, or otherwise from a GET
 * request.
 *
 * @author Pascal Essiembre
 * @since 3.0.0 (merge of former metadata HEAD and GET filter stages)
 */
class MetadataFiltersStage extends AbstractHttpMethodStage {
    private static final Logger LOG =
            LoggerFactory.getLogger(MetadataFiltersStage.class);

    public MetadataFiltersStage(HttpMethod method) {
        super(method);
    }
    @Override
    public boolean executeStage(
            HttpImporterPipelineContext ctx, HttpMethod method) {

        if (wasHttpHeadPerformed(ctx)) {
            return true;
        }

        if (isRejected(ctx)) {
            ctx.getDocInfo().setState(CrawlState.REJECTED);
            return false;
        }
        return true;
    }

    private boolean isRejected(ImporterPipelineContext ctx) {
        List<IMetadataFilter> filters = ctx.getConfig().getMetadataFilters();
        if (filters == null) {
            return false;
        }
        Properties metadata = ctx.getDocument().getMetadata();
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IMetadataFilter filter : filters) {
            boolean accepted = filter.acceptMetadata(
                    ctx.getDocInfo().getReference(), metadata);
            boolean isInclude = filter instanceof IOnMatchFilter
                   && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }
            if (accepted) {
                LOG.debug("ACCEPTED document metadata. Reference={} Filter={}",
                        ctx.getDocInfo().getReference(), filter);
            } else {
                ctx.fireCrawlerEvent(
                        REJECTED_FILTER, ctx.getDocInfo(), filter);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.fireCrawlerEvent(REJECTED_FILTER, ctx.getDocInfo(),
                    "No \"include\" metadata filters matched.");
            return true;
        }
        return false;
    }
}