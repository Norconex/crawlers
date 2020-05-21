/* Copyright 2015-2020 Norconex Inc.
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

import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.DocMetadata;

/**
 * <p>Fetches (i.e. download for processing) a document and/or its metadata
 * (HTTP response headers) depending on supplied {@link HttpMethod}.</p>
 * @author Pascal Essiembre
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
/*default*/ class HttpFetchStage extends AbstractHttpMethodStage {

    public HttpFetchStage(HttpMethod method) {
        super(method);
    }

    @Override
    public boolean executeStage(
            HttpImporterPipelineContext ctx, HttpMethod method) {

        HttpDocInfo docInfo = ctx.getDocInfo();
        HttpFetchClient fetcher = ctx.getHttpFetchClient();
        IHttpFetchResponse response = fetcher.fetch(ctx.getDocument(), method);

        // If GET set the crawl date
        if (method == HttpMethod.GET) {
            docInfo.setCrawlDate(ZonedDateTime.now());
        }

        //--- Add collector-specific metadata ---
        Properties meta = ctx.getDocument().getMetadata();
        meta.set(DocMetadata.CONTENT_TYPE, docInfo.getContentType());
        meta.set(DocMetadata.CONTENT_ENCODING, docInfo.getContentEncoding());

        //-- Deal with redirects ---
        String redirectURL = response.getRedirectTarget();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        //TODO really do here??  or just do it if different than response?
        docInfo.setState(state);
        if (CrawlState.UNMODIFIED.equals(state)) {
            ctx.fireCrawlerEvent(CrawlerEvent.REJECTED_UNMODIFIED,
                    docInfo, response);
            return false;
        }
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_FETCHED,
                    docInfo, response);
            return true;
        }
        String eventType = null;
        if (state.isOneOf(CrawlState.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }
        ctx.fireCrawlerEvent(eventType, docInfo, response);
        return false;
    }
}