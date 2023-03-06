/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.importer;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>Determines whether to recrawl a document or not.</p>
 * <p>Only does so if the document is not an orphan.</p>
 * @since 2.5.0
 */
@Slf4j
class RecrawlableResolverStage extends AbstractWebImporterStage {

    @Override
    boolean executeStage(WebImporterPipelineContext ctx) {
        // skip if doc is an orphan
        if (ctx.getDocument().isOrphan()) {
//        if (ctx.isOrphan()) {
            return true;
        }

        var rr = Web.config(ctx).getRecrawlableResolver();
        if (rr == null) {
            // no resolver means we process it.
            return true;
        }

        var cachedInfo = Web.context(ctx).getCachedDocRecord();
        if (cachedInfo == null) {
            // this document was not previously crawled so process it.
            return true;
        }

        var currentData = ctx.getDocRecord();

        var isRecrawlable = rr.isRecrawlable(cachedInfo);
        if (!isRecrawlable) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} is not ready to be recrawled, skipping it.",
                        cachedInfo.getReference());
            }
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_PREMATURE)
                    .source(ctx.getCrawler())
                    .subject(rr)
                    .crawlDocRecord(ctx.getDocRecord())
                    .build());
            currentData.setState(CrawlDocState.PREMATURE);
        }
        return isRecrawlable;
    }
}