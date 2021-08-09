/* Copyright 2015-2021 Norconex Inc.
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
import com.norconex.collector.http.crawler.HttpCrawlerConfig.HttpMethodSupport;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.doc.HttpDocMetadata;
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
        CrawlState originalCrawlState = docInfo.getState();

        docInfo.setCrawlDate(ZonedDateTime.now());

        //--- Add collector-specific metadata ---
        Properties meta = ctx.getDocument().getMetadata();
        meta.set(DocMetadata.CONTENT_TYPE, docInfo.getContentType());
        meta.set(DocMetadata.CONTENT_ENCODING, docInfo.getContentEncoding());
        meta.set(HttpDocMetadata.ORIGINAL_REFERENCE,
                docInfo.getOriginalReference());

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
            ctx.fire(CrawlerEvent.REJECTED_UNMODIFIED,
                    b -> b.crawlDocInfo(docInfo).subject(response));
            return false;
        }
        if (state.isGoodState()) {
            ctx.fire(CrawlerEvent.DOCUMENT_FETCHED,
                    b -> b.crawlDocInfo(docInfo).subject(response));
            return true;
        }

        String eventType = null;
        if (state.isOneOf(CrawlState.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }
        ctx.fire(eventType, b -> b.crawlDocInfo(docInfo).subject(response));

        // At this stage, the URL is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on http fetch methods supported.
        return continueOnBadState(ctx, method, originalCrawlState);
    }

    private boolean continueOnBadState(
            HttpImporterPipelineContext ctx,
            HttpMethod method,
            CrawlState originalCrawlState) {
        // Note: a disabled method will never get here,
        // and when both are enabled, GET always comes after HEAD.
        HttpMethodSupport headSupport = ctx.getConfig().getFetchHttpHead();
        HttpMethodSupport getSupport = ctx.getConfig().getFetchHttpGet();

        //--- HEAD ---
        if (HttpMethod.HEAD.is(method)) {
            // if method is required, we end it here.
            if (HttpMethodSupport.REQUIRED.is(headSupport)) {
                return false;
            }
            // if head is optional and there is a GET, we continue
            return HttpMethodSupport.OPTIONAL.is(headSupport)
                    && HttpMethodSupport.isEnabled(getSupport);

        //--- GET ---
        } else if (HttpMethod.GET.is(method)) {
            // if method is required, we end it here.
            if (HttpMethodSupport.REQUIRED.is(getSupport)) {
                return false;
            }
            // if method is optional and HEAD was enabled and successful,
            // we continue
            return HttpMethodSupport.OPTIONAL.is(getSupport)
                    && HttpMethodSupport.isEnabled(headSupport)
                    && originalCrawlState.isGoodState();
        }

        // If a custom implementation introduces another http method,
        // we do not know the intent so end here.
        return false;
    }
}