/* Copyright 2010-2020 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.pipeline.DocInfoPipelineContext;
import com.norconex.collector.core.pipeline.queue.QueueReferenceStage;
import com.norconex.collector.core.pipeline.queue.ReferenceFiltersStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any
 * document or document header is downloaded.
 * Instances are only valid for the scope of a single URL.
 * @author Pascal Essiembre
 */
public final class HttpQueuePipeline
        extends Pipeline<DocInfoPipelineContext> {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpQueuePipeline.class);

    public HttpQueuePipeline() {
        super();
        addStage(new DepthValidationStage());
        addStage(new ReferenceFiltersStage());
        addStage(new RobotsTxtFiltersStage());
        addStage(new URLNormalizerStage());
        addStage(new SitemapStage());
        addStage(new QueueReferenceStage());
    }

    //--- URL Depth ------------------------------------------------------------
    private static class DepthValidationStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(HttpQueuePipelineContext ctx) {
            if (ctx.getConfig().getMaxDepth() != -1
                    && ctx.getDocInfo().getDepth()
                            > ctx.getConfig().getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process ({}): {}",
                            ctx.getDocInfo().getDepth(),
                            ctx.getDocInfo().getReference());
                }
                ctx.getDocInfo().setState(HttpCrawlState.TOO_DEEP);
                ctx.fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_TOO_DEEP,
                        ctx.getDocInfo(), ctx.getDocInfo().getDepth());
                return false;
            }
            return true;
        }
    }

    /*default*/ static RobotsTxt getRobotsTxt(HttpQueuePipelineContext ctx) {
        if (!ctx.getConfig().isIgnoreRobotsTxt()) {
            return ctx.getConfig().getRobotsTxtProvider().getRobotsTxt(
                    ctx.getCrawler().getHttpFetchClient(),
                    ctx.getDocInfo().getReference());
        }
        return null;
    }

    //--- Sitemap URL Extraction -----------------------------------------------
    private static class SitemapStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(final HttpQueuePipelineContext ctx) {
            if (ctx.getConfig().isIgnoreSitemap()
                    || ctx.getSitemapResolver() == null) {
                return true;
            }
            String urlRoot = ctx.getDocInfo().getUrlRoot();
            List<String> robotsTxtLocations = new ArrayList<>();
            RobotsTxt robotsTxt = getRobotsTxt(ctx);
            if (robotsTxt != null) {
                robotsTxtLocations.addAll(robotsTxt.getSitemapLocations());
            }
            final ISitemapResolver sitemapResolver = ctx.getSitemapResolver();

//            SitemapURLAdder urlAdder = new SitemapURLAdder() {
//                @Override
//                public void add(HttpCrawlReference reference) {
//                    HttpQueuePipelineContext context =
//                            new HttpQueuePipelineContext(
//                                    ctx.getCrawler(), reference);
//                    new HttpQueuePipeline().execute(context);
//                }
//            };
            sitemapResolver.resolveSitemaps(
                    ctx.getCrawler().getHttpFetchClient(), urlRoot,
                    robotsTxtLocations, (ref) -> {
                        HttpQueuePipelineContext context =
                                new HttpQueuePipelineContext(
                                        ctx.getCrawler(), ref);
                        new HttpQueuePipeline().execute(context);
                    }, false);
            return true;
        }
    }

    //--- URL Normalizer -------------------------------------------------------
    private static class URLNormalizerStage extends AbstractQueueStage {
        @Override
        public boolean executeStage(HttpQueuePipelineContext ctx) {
            if (ctx.getConfig().getUrlNormalizer() != null) {
                String url = ctx.getConfig().getUrlNormalizer().normalizeURL(
                        ctx.getDocInfo().getReference());
                if (url == null) {
                    ctx.getDocInfo().setState(HttpCrawlState.REJECTED);
                    return false;
                }
                ctx.getDocInfo().setReference(url);
            }
            return true;
        }
    }

}

