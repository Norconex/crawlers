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

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
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

        CrawlDoc doc = ctx.getDocument();

//        HttpDocInfo crawlRef = ctx.getDocInfo();

        //IHttpMetadataFetcher headersFetcher = ctx.getHttpHeadersFetcher();
        HttpFetchClient fetcher = ctx.getHttpFetchClient();

//        Properties metadata = ctx.getMetadata();
//        Properties headers = new Properties(metadata.isCaseInsensitiveKeys());

        IHttpFetchResponse response = fetcher.fetch(doc, HttpMethod.HEAD);

//        metadata.putAll(headers);


        //TODO REALLY NEEDED OR DONE BY FETCHER NOW?
        HttpImporterPipelineUtil.enhanceHTTPHeaders(doc.getMetadata());
        HttpImporterPipelineUtil.applyMetadataToDocument(doc);

        //-- Deal with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        doc.getDocInfo().setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_METADATA_FETCHED,
                    doc.getDocInfo(), response);
        } else {
            String eventType = null;
            if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                eventType = CrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = CrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, doc.getDocInfo(), response);
            return false;
        }
        return true;
    }
}