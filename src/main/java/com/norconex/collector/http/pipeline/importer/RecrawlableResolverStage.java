/* Copyright 2016-2020 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;

/**
 * <p>Determines whether to recrawl a document or not.</p>
 * @author Pascal Essiembre
 * @since 2.5.0
 */
/*default*/ class RecrawlableResolverStage extends AbstractImporterStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(RecrawlableResolverStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        var rr = ctx.getConfig().getRecrawlableResolver();
        if (rr == null) {
            // no resolver means we process it.
            return true;
        }

        var cachedInfo = ctx.getCachedDocInfo();
        if (cachedInfo == null) {
            // this document was not previously crawled so process it.
            return true;
        }

        var currentData = ctx.getDocInfo();

        var isRecrawlable = rr.isRecrawlable(cachedInfo);

        if (!isRecrawlable) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} is not ready to be recrawled, skipping it.",
                        cachedInfo.getReference());
            }
            ctx.fire(CrawlerEvent.REJECTED_PREMATURE, b -> b
                    .crawlDocInfo(currentData)
                    .subject(rr));
            currentData.setState(CrawlState.PREMATURE);

            // If the URL was redirected (as per cache) and the redirect URL
            // target has not been processed already (still in cache),
            // re-queue the redirect URL target or it may be wrongfully
            // considered orphan if not referenced somewhere else during the
            // crawl.
            if (StringUtils.isNotBlank(cachedInfo.getRedirectTarget())) {
                ctx.getDocInfoService()
                        .getCached(cachedInfo.getRedirectTarget())
                        .ifPresent(targetDocInfo -> {
                            var newContext = new HttpQueuePipelineContext(
                                    ctx.getCrawler(),
                                    (HttpDocInfo) targetDocInfo);
                            new HttpQueuePipeline().execute(newContext);
                        });
            }
        }
        return isRecrawlable;
    }
}