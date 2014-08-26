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
package com.norconex.collector.http.ref.pipe;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.core.pipeline.Pipeline;
import com.norconex.collector.core.ref.store.IReferenceStore;
import com.norconex.collector.http.crawler.IHttpCrawlerEventListener;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.ref.HttpDocReference;
import com.norconex.collector.http.ref.HttpDocReferenceState;
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
public final class ReferencePipeline 
        extends Pipeline<ReferencePipelineContext> {

    private static final Logger LOG = 
            LogManager.getLogger(ReferencePipeline.class);
    
    public ReferencePipeline() {
        this(false);
    }
    public ReferencePipeline(boolean isSitemapReference) {
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
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(ReferencePipelineContext ctx) {
            if (ctx.getConfig().getMaxDepth() != -1 
                    && ctx.getReference().getDepth() 
                            > ctx.getConfig().getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process (" 
                            + ctx.getReference().getDepth() + "): " 
                            + ctx.getReference().getReference());
                }
                ctx.getReference().setState(HttpDocReferenceState.TOO_DEEP);
                return false;
            }
            return true;
        }
    }
    
    //--- URL Filters ----------------------------------------------------------
    private class URLFiltersStage
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(ReferencePipelineContext ctx) {
            if (isURLRejected(ctx.getConfig().getURLFilters(), null, ctx)) {
                ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                return false;
            }
            return true;
        }
    }
    
    //--- Robots.txt Filters ---------------------------------------------------
    private class RobotsTxtFiltersStage
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(ReferencePipelineContext ctx) {
            if (!ctx.getConfig().isIgnoreRobotsTxt()) {
                RobotsTxt robotsTxt = ctx.getRobotsTxt();
                if (isURLRejected(robotsTxt.getFilters(), robotsTxt, ctx)) {
                    ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }

    //--- Sitemap URL Extraction -----------------------------------------------
    private class SitemapStage
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(final ReferencePipelineContext ctx) {
            if (ctx.getConfig().isIgnoreSitemap() 
                    || ctx.getCrawler().getSitemapResolver() == null) {
                return true;
            }
            String urlRoot = ctx.getReference().getUrlRoot();
            String[] robotsTxtLocations = null;
            if (ctx.getRobotsTxt() != null) {
                robotsTxtLocations = ctx.getRobotsTxt().getSitemapLocations();
            }
            SitemapURLAdder urlStore = new SitemapURLAdder() {
                private static final long serialVersionUID = 
                        7618470895330355434L;
                @Override
                public void add(HttpDocReference reference) {
                    ReferencePipelineContext context = 
                            new ReferencePipelineContext(
                                    ctx.getCrawler(), 
                                    ctx.getReferenceStore(), 
                                    reference);
                    new ReferencePipeline(true).process(context);
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
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(final ReferencePipelineContext ctx) {
            if (ctx.getConfig().getUrlNormalizer() != null) {
                String url = ctx.getConfig().getUrlNormalizer().normalizeURL(
                        ctx.getReference().getReference());
                if (url == null) {
                    ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                    return false;
                }
                ctx.getReference().setReference(url);
            }
            return true;
        }
    }

    //--- Store Next URLs to process -------------------------------------------
    private class StoreNextURLStage
            implements IPipelineStage<ReferencePipelineContext> {
        @Override
        public boolean process(final ReferencePipelineContext ctx) {
//            if (robotsMeta != null && robotsMeta.isNofollow()) {
//                return true;
//            }
            String url = ctx.getReference().getReference();
            if (StringUtils.isBlank(url)) {
                return true;
            }
            IReferenceStore refStore = ctx.getReferenceStore();
            
            if (refStore.isActive(url)) {
                debug("Already being processed: %s", url);
            } else if (refStore.isQueued(url)) {
                debug("Already queued: %s", url);
            } else if (refStore.isProcessed(url)) {
                debug("Already processed: %s", url);
            } else {
                refStore.queue(new HttpDocReference(
                        url, ctx.getReference().getDepth()));
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
            ReferencePipelineContext ctx) {
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
                    ctx.getReference().getReference());
            
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
                            + ". URL=" + ctx.getReference().getReference()
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
            String type, ReferencePipelineContext ctx) {
        
        IHttpCrawlerEventListener[] listeners =
                ctx.getConfig().getCrawlerListeners();
        if (listeners == null) {
            return;
        }
        for (IHttpCrawlerEventListener listener : listeners) {
            if (robots != null) {
                listener.documentRobotsTxtRejected(ctx.getCrawler(), 
                        ctx.getReference().getReference(), filter, robots);
            } else {
                listener.documentURLRejected(ctx.getCrawler(), 
                        ctx.getReference().getReference(), filter);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("REJECTED document URL" + type + ". URL=" 
                  + ctx.getReference().getReference() 
                  + " Filter=[one or more filter 'onMatch' "
                  + "attribute is set to 'include', but none of them were "
                  + "matched]");
        }
    }
    
    @Override
    public boolean process(ReferencePipelineContext context)
            throws CollectorException {
        try {
            if (super.process(context)) {
                context.getReference().setState(HttpDocReferenceState.OK);
                return true;
            }
            return false;
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            context.getReference().setState(HttpDocReferenceState.ERROR);
            LOG.error("Could not process URL: " 
                    + context.getReference().getReference(), e);
            return false;
        } finally {
            //--- Mark URL as Processed ----------------------------------------
            if (!context.getReference().getState().equals(
                    HttpDocReferenceState.OK)) {
                if (context.getReference().getState() == null) {
                    context.getReference().setState(
                            HttpDocReferenceState.BAD_STATUS);
                }
                context.getReferenceStore().processed(context.getReference());
//                status.logInfo(httpDocReference);
            }
        }
    }

    private boolean isIncludeFilter(IURLFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }
}

