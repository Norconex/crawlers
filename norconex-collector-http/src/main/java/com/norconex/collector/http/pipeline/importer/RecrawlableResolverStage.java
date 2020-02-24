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

import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.recrawl.PreviousCrawlData;

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

        HttpDocInfo cachedData = ctx.getCachedDocInfo();
        if (cachedData == null) {
            // this document was not previously crawled so process it.
            return true;
        }

        HttpDocInfo currentData = ctx.getDocInfo();

        PreviousCrawlData prevData = new PreviousCrawlData();
        prevData.setReference(cachedData.getReference());
        prevData.setContentType(cachedData.getContentType());
        prevData.setCrawlDate(cachedData.getCrawlDate());
        prevData.setSitemapChangeFreq(currentData.getSitemapChangeFreq());
        prevData.setSitemapLastMod(currentData.getSitemapLastMod());
        prevData.setSitemapPriority(currentData.getSitemapPriority());

        boolean isRecrawlable = rr.isRecrawlable(prevData);
        if (!isRecrawlable) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(cachedData.getReference()
                        + " is not ready to be recrawled, skipping it.");
            }
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_PREMATURE, currentData, rr);
            currentData.setState(HttpCrawlState.PREMATURE);
        }
        return isRecrawlable;
    }
}