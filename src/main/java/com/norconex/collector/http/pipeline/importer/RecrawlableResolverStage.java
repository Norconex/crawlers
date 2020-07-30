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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;

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
        IRecrawlableResolver rr = ctx.getConfig().getRecrawlableResolver();
        if (rr == null) {
            // no resolver means we process it.
            return true;
        }

        HttpDocInfo cachedInfo = ctx.getCachedDocInfo();
        if (cachedInfo == null) {
            // this document was not previously crawled so process it.
            return true;
        }

        HttpDocInfo currentData = ctx.getDocInfo();

        boolean isRecrawlable = rr.isRecrawlable(cachedInfo);
        if (!isRecrawlable) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} is not ready to be recrawled, skipping it.",
                        cachedInfo.getReference());
            }
            ctx.fireCrawlerEvent(
                    CrawlerEvent.REJECTED_PREMATURE, currentData, rr);
            currentData.setState(CrawlState.PREMATURE);
        }
        return isRecrawlable;
    }
}