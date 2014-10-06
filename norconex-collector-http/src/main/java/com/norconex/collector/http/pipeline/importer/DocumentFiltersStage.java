/**
 * 
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