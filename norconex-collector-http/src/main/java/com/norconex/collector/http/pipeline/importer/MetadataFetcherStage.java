/* Copyright 2016-2018 Norconex Inc.
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

import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.HttpFetcherExecutor;
import com.norconex.collector.http.fetch.util.RedirectStrategyWrapper;

/**
 * <p>Fetches a document metadata (i.e. HTTP headers).</p>
 * <p>Prior to 2.6.0, the code for this class was part of
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.6.0
 */
/*default*/ class MetadataFetcherStage extends AbstractImporterStage {

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        if (!ctx.isHttpHeadFetchEnabled()) {
            return true;
        }

        HttpCrawlData crawlData = ctx.getCrawlData();

        //IHttpMetadataFetcher headersFetcher = ctx.getHttpHeadersFetcher();
        HttpFetcherExecutor fetcher = ctx.getHttpFetcherExecutor();

        HttpMetadata metadata = ctx.getMetadata();
//        Properties headers = new Properties(metadata.isCaseInsensitiveKeys());

        HttpFetchResponse response = fetcher.fetchHeaders(
                crawlData.getReference(), metadata);

//        metadata.putAll(headers);

        HttpImporterPipelineUtil.enhanceHTTPHeaders(metadata);
        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        //-- Deal with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        crawlData.setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(HttpCrawlerEvent.DOCUMENT_METADATA_FETCHED,
                    crawlData, response);
        } else {
            String eventType = null;
            if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                eventType = HttpCrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = HttpCrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, crawlData, response);
            return false;
        }
        return true;
    }
}