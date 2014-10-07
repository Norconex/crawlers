/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.pipeline.importer;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * @author Pascal Essiembre
 * 
 */
/* default */class DocumentFiltersStage extends AbstractImporterStage {

    private static final Logger LOG = LogManager
            .getLogger(DocumentFiltersStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        IHttpDocumentFilter[] filters = ctx.getConfig()
                .getHttpDocumentfilters();
        if (filters == null) {
            return true;
        }

        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IHttpDocumentFilter filter : filters) {
            boolean accepted = filter.acceptDocument(ctx.getDocument());

            // Deal with includes
            if (isIncludeFilter(filter)) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (accepted) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "ACCEPTED document. URL=%s Filter=%s", ctx
                                    .getCrawlData().getReference(), filter));
                }
            } else {
                ctx.getCrawler().fireCrawlerEvent(
                        CrawlerEvent.REJECTED_FILTER, 
                        ctx.getCrawlData(), filter);
                ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                return false;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.getCrawler().fireCrawlerEvent(
                    CrawlerEvent.REJECTED_FILTER, 
                    ctx.getCrawlData(), null);
            ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
            return false;
        }
        return true;
    }

    private boolean isIncludeFilter(IHttpDocumentFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }
}