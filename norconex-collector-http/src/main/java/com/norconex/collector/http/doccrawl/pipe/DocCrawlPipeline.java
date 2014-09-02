/* Copyright 2010-2013 Norconex Inc.
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
package com.norconex.collector.http.doccrawl.pipe;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.doccrawl.store.IDocCrawlStore;
import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.core.pipeline.Pipeline;
import com.norconex.collector.http.crawler.IHttpCrawlerEventListener;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any 
 * document or document header download is
 * Instances are only valid for the scope of a single URL.  
 * @author Pascal Essiembre
 */
public final class DocCrawlPipeline 
        extends Pipeline<DocCrawlPipelineContext> {

    private static final Logger LOG = 
            LogManager.getLogger(DocCrawlPipeline.class);
    
    public DocCrawlPipeline() {
        this(false);
    }
    public DocCrawlPipeline(boolean isSitemapReference) {
        super();
        
        addStage(new DepthValidationStage());
        addStage(new URLFiltersStage());
        addStage(new RobotsTxtFiltersStage());
        addStage(new URLNormalizerStage());
        if (!isSitemapReference) {
            addStage(new SitemapStage());
        }
        addStage(new StoreNextURLStage());
        
        
    }
//    public boolean processURL() {
//        return processURL(defaultSteps);
//    }
//    private boolean processSitemapURL() {
//        return processURL(sitemapSteps);
//    }

//    public interface IURLProcessingStep {
//        // Returns true to continue to next step
//        // Returns false to abort, this URL is rejected.
//        boolean processURL();
//    }


    //--- URL Depth ------------------------------------------------------------
    private class DepthValidationStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(DocCrawlPipelineContext ctx) {
            if (ctx.getConfig().getMaxDepth() != -1 
                    && ctx.getDocCrawl().getDepth() 
                            > ctx.getConfig().getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process (" 
                            + ctx.getDocCrawl().getDepth() + "): " 
                            + ctx.getDocCrawl().getReference());
                }
                ctx.getDocCrawl().setState(HttpDocCrawlState.TOO_DEEP);
                return false;
            }
            return true;
        }
    }
    
    //--- URL Filters ----------------------------------------------------------
    private class URLFiltersStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(DocCrawlPipelineContext ctx) {
            if (isURLRejected(ctx.getConfig().getURLFilters(), null, ctx)) {
                ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                return false;
            }
            return true;
        }
    }
    
    //--- Robots.txt Filters ---------------------------------------------------
    private class RobotsTxtFiltersStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(DocCrawlPipelineContext ctx) {
            if (!ctx.getConfig().isIgnoreRobotsTxt()) {
                RobotsTxt robotsTxt = ctx.getRobotsTxt();
                if (isURLRejected(robotsTxt.getFilters(), robotsTxt, ctx)) {
                    ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }

    //--- Sitemap URL Extraction -----------------------------------------------
    private class SitemapStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(final DocCrawlPipelineContext ctx) {
            if (ctx.getConfig().isIgnoreSitemap() 
                    || ctx.getCrawler().getSitemapResolver() == null) {
                return true;
            }
            String urlRoot = ctx.getDocCrawl().getUrlRoot();
            String[] robotsTxtLocations = null;
            if (ctx.getRobotsTxt() != null) {
                robotsTxtLocations = ctx.getRobotsTxt().getSitemapLocations();
            }
            SitemapURLAdder urlStore = new SitemapURLAdder() {
                private static final long serialVersionUID = 
                        7618470895330355434L;
                @Override
                public void add(HttpDocCrawl reference) {
                    DocCrawlPipelineContext context = 
                            new DocCrawlPipelineContext(
                                    ctx.getCrawler(), 
                                    ctx.getDocCrawlStore(), 
                                    reference);
                    new DocCrawlPipeline(true).process(context);
                }
            };
            ctx.getSitemapResolver().resolveSitemaps(
                    ctx.getHttpClient(), urlRoot, 
                    robotsTxtLocations, urlStore);
            return true;
        }
    }
    

    
    //--- URL Normalizer -------------------------------------------------------
    private class URLNormalizerStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(final DocCrawlPipelineContext ctx) {
            if (ctx.getConfig().getUrlNormalizer() != null) {
                String url = ctx.getConfig().getUrlNormalizer().normalizeURL(
                        ctx.getDocCrawl().getReference());
                if (url == null) {
                    ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                    return false;
                }
                ctx.getDocCrawl().setReference(url);
            }
            return true;
        }
    }

    //--- Store Next URLs to process -------------------------------------------
    private class StoreNextURLStage
            implements IPipelineStage<DocCrawlPipelineContext> {
        @Override
        public boolean process(final DocCrawlPipelineContext ctx) {
//            if (robotsMeta != null && robotsMeta.isNofollow()) {
//                return true;
//            }
            String url = ctx.getDocCrawl().getReference();
            if (StringUtils.isBlank(url)) {
                return true;
            }
            IDocCrawlStore refStore = ctx.getDocCrawlStore();
            
            if (refStore.isActive(url)) {
                debug("Already being processed: %s", url);
            } else if (refStore.isQueued(url)) {
                debug("Already queued: %s", url);
            } else if (refStore.isProcessed(url)) {
                debug("Already processed: %s", url);
            } else {
                refStore.queue(new HttpDocCrawl(
                        url, ctx.getDocCrawl().getDepth()));
                debug("Queued for processing: %s", url);
            }
            return true;
        }
    }
    
    private static void debug(String message, Object... values) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format(message, values));
        }
    }    

    //=== Utility methods ======================================================
    private boolean isURLRejected(IURLFilter[] filters, RobotsTxt robots, 
            DocCrawlPipelineContext ctx) {
        if (filters == null) {
            return false;
        }
        String type = "";
        if (robots != null) {
            type = " (robots.txt)";
        }
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IURLFilter filter : filters) {
            boolean accepted = filter.acceptURL(
                    ctx.getDocCrawl().getReference());
            
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
                    LOG.debug("ACCEPTED document URL" + type 
                            + ". URL=" + ctx.getDocCrawl().getReference()
                            + " Filter=" + filter);
                }
            } else {
                fireDocumentRejected(filter, robots, type, ctx);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            fireDocumentRejected(
                    null, null, " (no include filters matched)", ctx);
            return true;
        }
        return false;
    }
    
    private void fireDocumentRejected(IURLFilter filter, RobotsTxt robots, 
            String type, DocCrawlPipelineContext ctx) {
        
        IHttpCrawlerEventListener[] listeners =
                ctx.getConfig().getCrawlerListeners();
        if (listeners == null) {
            return;
        }
        for (IHttpCrawlerEventListener listener : listeners) {
            if (robots != null) {
                listener.documentRobotsTxtRejected(ctx.getCrawler(), 
                        ctx.getDocCrawl().getReference(), filter, robots);
            } else {
                listener.documentURLRejected(ctx.getCrawler(), 
                        ctx.getDocCrawl().getReference(), filter);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("REJECTED document URL" + type + ". URL=" 
                  + ctx.getDocCrawl().getReference() 
                  + " Filter=[one or more filter 'onMatch' "
                  + "attribute is set to 'include', but none of them were "
                  + "matched]");
        }
    }
    
    @Override
    public boolean process(DocCrawlPipelineContext context)
            throws CollectorException {
        try {
            if (super.process(context)) {
                // the state is set to new/modified/unmodified by checksummers
//                context.getDocCrawl().setState(HttpDocCrawlState.OK);
                return true;
            }
            return false;
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            context.getDocCrawl().setState(HttpDocCrawlState.ERROR);
            LOG.error("Could not process URL: " 
                    + context.getDocCrawl().getReference(), e);
            return false;
        } finally {
            //--- Mark URL as Processed ----------------------------------------
            if (!context.getDocCrawl().getState().isGoodState()) {
                if (context.getDocCrawl().getState() == null) {
                    context.getDocCrawl().setState(
                            HttpDocCrawlState.BAD_STATUS);
                }
                context.getDocCrawlStore().processed(context.getDocCrawl());
//                status.logInfo(httpDocReference);
            }
        }
    }

    private boolean isIncludeFilter(IURLFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }
}

