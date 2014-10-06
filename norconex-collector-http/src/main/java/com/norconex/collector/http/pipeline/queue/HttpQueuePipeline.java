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
package com.norconex.collector.http.pipeline.queue;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.BasePipelineContext;
import com.norconex.collector.core.pipeline.queue.QueueReferenceStage;
import com.norconex.collector.core.pipeline.queue.ReferenceFiltersStage;
import com.norconex.collector.core.pipeline.queue.ReferenceFiltersStageUtil;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any 
 * document or document header download is
 * Instances are only valid for the scope of a single URL.  
 * @author Pascal Essiembre
 */
public final class HttpQueuePipeline
        extends Pipeline<BasePipelineContext> {
        //extends AbstractQueuePipeline<BasePipelineContext> {

    private static final Logger LOG = 
            LogManager.getLogger(HttpQueuePipeline.class);
    
//    private final boolean includeSitemapStage;
    
    public HttpQueuePipeline() {
        this(false);
    }
    public HttpQueuePipeline(boolean includeSitemapStage) {
        super();
//        this.includeSitemapStage = includeSitemapStage;
        addStage(new DepthValidationStage());
        addStage(new ReferenceFiltersStage());
        addStage(new RobotsTxtFiltersStage());
        addStage(new URLNormalizerStage());
        if (!includeSitemapStage) {
            addStage(new SitemapStage());
        }
        addStage(new QueueReferenceStage());
    }
    
//    @Override
//    protected void addPipelineStages() {
//        addStage(new DepthValidationStage());
//        addStage(new ReferenceFiltersStage());
//        addStage(new RobotsTxtFiltersStage());
//        addStage(new URLNormalizerStage());
//        if (!includeSitemapStage) {
//            addStage(new SitemapStage());
//        }
//        addStage(new QueueReferenceStage());
//    }

    
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
            implements IPipelineStage<BasePipelineContext> {
        @Override
        public boolean execute(BasePipelineContext context) {
            HttpQueuePipelineContext ctx = (HttpQueuePipelineContext) context;
            if (ctx.getConfig().getMaxDepth() != -1 
                    && ctx.getCrawlData().getDepth() 
                            > ctx.getConfig().getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process (" 
                            + ctx.getCrawlData().getDepth() + "): " 
                            + ctx.getCrawlData().getReference());
                }
                ctx.getCrawlData().setState(HttpCrawlState.TOO_DEEP);
                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_TOO_DEEP, 
                        ctx.getCrawlData(), ctx.getCrawlData().getDepth());
                return false;
            }
            return true;
        }
    }
    
//    //--- URL Filters ----------------------------------------------------------
//    private class URLFiltersStage
//            implements IPipelineStage<HttpQueuePipelineContext> {
//        @Override
//        public boolean execute(HttpQueuePipelineContext ctx) {
//            if (isURLRejected(ctx.getConfig().getURLFilters(), null, ctx)) {
//                ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
//                return false;
//            }
//            return true;
//        }
//    }
    
    //--- Robots.txt Filters ---------------------------------------------------
    private class RobotsTxtFiltersStage
            implements IPipelineStage<BasePipelineContext> {
        @Override
        public boolean execute(BasePipelineContext context) {
            HttpQueuePipelineContext ctx = (HttpQueuePipelineContext) context;
            if (!ctx.getConfig().isIgnoreRobotsTxt()) {
                RobotsTxt robotsTxt = ctx.getRobotsTxt();
                if (ReferenceFiltersStageUtil.resolveReferenceFilters(
                        robotsTxt.getFilters(), ctx, "robots.txt")) {
                    ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                    ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_ROBOTS_TXT, 
                            ctx.getCrawlData(), robotsTxt);
                    return false;
                }
            }
            return true;
        }
    }

    //--- Sitemap URL Extraction -----------------------------------------------
    private class SitemapStage
            implements IPipelineStage<BasePipelineContext> {
        @Override
        public boolean execute(final BasePipelineContext context) {
            final HttpQueuePipelineContext ctx = 
                    (HttpQueuePipelineContext) context;
            if (ctx.getConfig().isIgnoreSitemap() 
                    || ctx.getCrawler().getSitemapResolver() == null) {
                return true;
            }
            String urlRoot = ctx.getCrawlData().getUrlRoot();
            String[] robotsTxtLocations = null;
            if (ctx.getRobotsTxt() != null) {
                robotsTxtLocations = ctx.getRobotsTxt().getSitemapLocations();
            }
            SitemapURLAdder urlStore = new SitemapURLAdder() {
                private static final long serialVersionUID = 
                        7618470895330355434L;
                @Override
                public void add(HttpCrawlData reference) {
                    HttpQueuePipelineContext context = 
                            new HttpQueuePipelineContext(
                                    ctx.getCrawler(), 
                                    ctx.getCrawlDataStore(), 
                                    reference);
                    new HttpQueuePipeline(true).execute(context);
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
            implements IPipelineStage<BasePipelineContext> {
        @Override
        public boolean execute(final BasePipelineContext context) {
            HttpQueuePipelineContext ctx = (HttpQueuePipelineContext) context;
            if (ctx.getConfig().getUrlNormalizer() != null) {
                String url = ctx.getConfig().getUrlNormalizer().normalizeURL(
                        ctx.getCrawlData().getReference());
                if (url == null) {
                    ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                    return false;
                }
                ctx.getCrawlData().setReference(url);
            }
            return true;
        }
    }

//    //--- Store Next URLs to process -------------------------------------------
//    private class StoreNextURLStage
//            implements IPipelineStage<QueuePipelineContext> {
//        @Override
//        public boolean execute(final QueuePipelineContext context) {
//            HttpQueuePipelineContext ctx = (HttpQueuePipelineContext) context;
//            String url = ctx.getCrawlData().getReference();
//            if (StringUtils.isBlank(url)) {
//                return true;
//            }
//            ICrawlDataStore refStore = ctx.getCrawlDataStore();
//            
//            if (refStore.isActive(url)) {
//                debug("Already being processed: %s", url);
//            } else if (refStore.isQueued(url)) {
//                debug("Already queued: %s", url);
//            } else if (refStore.isProcessed(url)) {
//                debug("Already processed: %s", url);
//            } else {
//                refStore.queue(new HttpCrawlData(
//                        url, ctx.getCrawlData().getDepth()));
//                debug("Queued for processing: %s", url);
//            }
//            return true;
//        }
//    }
//    
//    private static void debug(String message, Object... values) {
//        if (LOG.isDebugEnabled()) {
//            LOG.debug(String.format(message, values));
//        }
//    }    
//
//    //=== Utility methods ======================================================
//    private boolean isURLRejected(IReferenceFilter[] filters, RobotsTxt robots, 
//            HttpQueuePipelineContext ctx) {
//        if (filters == null) {
//            return false;
//        }
//        String type = "";
//        if (robots != null) {
//            type = " (robots.txt)";
//        }
//        boolean hasIncludes = false;
//        boolean atLeastOneIncludeMatch = false;
//        for (IReferenceFilter filter : filters) {
//            boolean accepted = filter.acceptReference(
//                    ctx.getCrawlData().getReference());
//            
//            // Deal with includes
//            if (isIncludeFilter(filter)) {
//                hasIncludes = true;
//                if (accepted) {
//                    atLeastOneIncludeMatch = true;
//                }
//                continue;
//            }
//
//            // Deal with exclude and non-OnMatch filters
//            if (accepted) {
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("ACCEPTED document URL" + type 
//                            + ". URL=" + ctx.getCrawlData().getReference()
//                            + " Filter=" + filter);
//                }
//            } else {
//                fireDocumentRejected(filter, robots, type, ctx);
//                return true;
//            }
//        }
//        if (hasIncludes && !atLeastOneIncludeMatch) {
//            fireDocumentRejected(
//                    null, null, " (no include filters matched)", ctx);
//            return true;
//        }
//        return false;
//    }
//    
//    private void fireDocumentRejected(IReferenceFilter filter, RobotsTxt robots, 
//            String type, HttpQueuePipelineContext ctx) {
//
//        ctx.getCrawler().fireCrawlerEvent(
//                HttpCrawlerEvent.REJECTED_FILTER, ctx.getCrawlData(), filter);
//
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("REJECTED document URL" + type + ". URL=" 
//                  + ctx.getCrawlData().getReference() 
//                  + " Filter=[one or more filter 'onMatch' "
//                  + "attribute is set to 'include', but none of them were "
//                  + "matched]");
//        }
//    }
//    
//    @Override
//    public boolean execute(HttpQueuePipelineContext context)
//            throws CollectorException {
//        try {
//            if (super.execute(context)) {
//                // the state is set to new/modified/unmodified by checksummers
////                context.getDocCrawl().setState(HttpCrawlState.OK);
//                return true;
//            }
//            return false;
//        } catch (Exception e) {
//            //TODO do we really want to catch anything other than 
//            // HTTPFetchException?  In case we want special treatment to the 
//            // class?
//            context.getCrawlData().setState(HttpCrawlState.ERROR);
//            
//            context.getCrawler().fireCrawlerEvent(
//                    HttpCrawlerEvent.REJECTED_ERROR, 
//                    context.getCrawlData(), e);
//            
//            LOG.error("Could not process URL: " 
//                    + context.getCrawlData().getReference(), e);
//            return false;
//        } finally {
//            //--- Mark URL as Processed ----------------------------------------
//            if (!context.getCrawlData().getState().isGoodState()) {
//                if (context.getCrawlData().getState() == null) {
//                    context.getCrawlData().setState(
//                            CrawlState.BAD_STATUS);
//                }
//                context.getCrawlDataStore().processed(context.getCrawlData());
////                status.logInfo(httpDocReference);
//            }
//        }
//    }
//
//    private boolean isIncludeFilter(IReferenceFilter filter) {
//        return filter instanceof IOnMatchFilter
//                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
//    }
}

