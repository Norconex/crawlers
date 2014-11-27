/* Copyright 2010-2014 Norconex Inc.
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

