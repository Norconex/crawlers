/* Copyright 2015-2019 Norconex Inc.
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

import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.reference.CrawlState;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.util.RedirectStrategyWrapper;
import com.norconex.collector.http.reference.HttpCrawlReference;
import com.norconex.collector.http.reference.HttpCrawlState;

/**
 * <p>Fetches (i.e. download for processing) a document.</p>
 * <p>Prior to 2.3.0, the code for this class was part of
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.3.0
 */
/*default*/ class DocumentFetcherStage extends AbstractImporterStage {

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        HttpCrawlReference crawlRef = ctx.getCrawlReference();

        IHttpFetchResponse response =
                ctx.getHttpFetchClient().fetchDocument(ctx.getDocument());

        crawlRef.setCrawlDate(new Date());

        HttpImporterPipelineUtil.enhanceHTTPHeaders(
                ctx.getDocument().getMetadata());
        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        crawlRef.setContentType(ctx.getDocument().getContentType());

        //-- Deal with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        crawlRef.setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(HttpCrawlerEvent.DOCUMENT_FETCHED,
                    crawlRef, response);
        } else {
            String eventType = null;
            if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                eventType = HttpCrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = HttpCrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, crawlRef, response);
            return false;
        }
        return true;
    }
}