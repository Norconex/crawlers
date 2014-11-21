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
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any 
 * document or document header is downloaded.
 * Instances are only valid for the scope of a single URL.  
 * @author Pascal Essiembre
 */
public final class HttpQueuePipeline
        extends Pipeline<BasePipelineContext> {

    private static final Logger LOG = 
            LogManager.getLogger(HttpQueuePipeline.class);
    
    public HttpQueuePipeline() {
        this(false);
    }
    public HttpQueuePipeline(boolean includeSitemapStage) {
        super();
        addStage(new DepthValidationStage());
        addStage(new ReferenceFiltersStage());
        addStage(new RobotsTxtFiltersStage());
        addStage(new URLNormalizerStage());
        if (includeSitemapStage) {
            addStage(new SitemapStage());
        }
        addStage(new QueueReferenceStage());
    }
    
    //--- URL Depth ------------------------------------------------------------
    private static class DepthValidationStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(HttpQueuePipelineContext ctx) {
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
    
    //--- Robots.txt Filters ---------------------------------------------------
    private static class RobotsTxtFiltersStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(HttpQueuePipelineContext ctx) {
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
    private static class SitemapStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(final HttpQueuePipelineContext ctx) {
            if (ctx.getConfig().isIgnoreSitemap() 
                    || ctx.getCrawler().getSitemapResolver() == null) {
                return true;
            }
            String urlRoot = ctx.getCrawlData().getUrlRoot();
            String[] robotsTxtLocations = null;
            if (ctx.getRobotsTxt() != null) {
                robotsTxtLocations = ctx.getRobotsTxt().getSitemapLocations();
            }
            final ISitemapResolver sitemapResolver = ctx.getSitemapResolver();
            
            SitemapURLAdder urlAdder = new SitemapURLAdder() {
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
            sitemapResolver.resolveSitemaps(
                    ctx.getHttpClient(), urlRoot, 
                    robotsTxtLocations, urlAdder);
            return true;
        }
    }

    //--- URL Normalizer -------------------------------------------------------
    private static class URLNormalizerStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(HttpQueuePipelineContext ctx) {
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

}

