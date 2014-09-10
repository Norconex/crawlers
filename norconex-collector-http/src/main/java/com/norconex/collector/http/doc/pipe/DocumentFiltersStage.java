/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.crawler.event.DocCrawlEvent;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * @author Pascal Essiembre
 * 
 */
/* default */class DocumentFiltersStage implements
        IPipelineStage<DocumentPipelineContext> {

    private static final Logger LOG = LogManager
            .getLogger(DocumentFiltersStage.class);

    @Override
    public boolean execute(DocumentPipelineContext ctx) {
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
                                    .getDocCrawl().getReference(), filter));
                }
            } else {
                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        DocCrawlEvent.REJECTED_FILTER, 
                        ctx.getDocCrawl(), filter));
                ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                return false;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                    DocCrawlEvent.REJECTED_FILTER, 
                    ctx.getDocCrawl(), null));
            ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
            return false;
        }
        return true;
    }

    private boolean isIncludeFilter(IHttpDocumentFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }
}