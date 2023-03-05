/* Copyright 2015-2023 Norconex Inc.
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

import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.crawler.WebCrawlerConfig.HttpMethodSupport;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.importer.doc.DocMetadata;

import lombok.NonNull;

/**
 * <p>Fetches (i.e. download for processing) a document and/or its metadata
 * (HTTP response headers) depending on supplied {@link HttpMethod}.</p>
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
class HttpFetchStage extends AbstractWebImporterStage {

    public HttpFetchStage(@NonNull HttpMethod method) {
        super(method);
    }

    /**
     * Only does something if appropriate. For instance,
     * if a separate HTTP HEAD request was NOT required to be performed,
     * this method will never get invoked for a HEAD method.
     * @param ctx pipeline context
     * @return <code>true</code> if we continue processing.
     */
    @Override
    boolean executeStage(WebImporterPipelineContext ctx) {
        // If stage is for a method that was disabled, skip
        if (!ctx.isHttpMethodEnabled(getHttpMethod())) {
            return true;
        }

        var docRecord = ctx.getDocRecord();
        var fetcher = (HttpFetcher) ctx.getCrawler().getFetcher();
        HttpFetchResponse response;
        try {
            response = fetcher.fetch(
                    new HttpFetchRequest(ctx.getDocument(), getHttpMethod()));
        } catch (FetchException e) {
            throw new CrawlerException("Could not fetch URL: "
                    + ctx.getDocRecord().getReference(), e);
        }
        var originalCrawlDocState = docRecord.getState();

        docRecord.setCrawlDate(ZonedDateTime.now());

        //--- Add collector-specific metadata ---
        var meta = ctx.getDocument().getMetadata();
        meta.set(DocMetadata.CONTENT_TYPE, docRecord.getContentType());
        meta.set(DocMetadata.CONTENT_ENCODING, docRecord.getContentEncoding());
        meta.set(WebDocMetadata.ORIGINAL_REFERENCE,
                docRecord.getOriginalReference());

        //-- Deal with redirects ---
        var redirectURL = response.getRedirectTarget();

        if (StringUtils.isNotBlank(redirectURL)) {
            WebImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        var state = response.getCrawlDocState();
        //TODO really do here??  or just do it if different than response?
        docRecord.setState(state);
        if (CrawlDocState.UNMODIFIED.equals(state)) {
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_UNMODIFIED)
                    .source(ctx.getCrawler())
                    .subject(response)
                    .crawlDocRecord(docRecord)
                    .build());
            return false;
        }
        if (state.isGoodState()) {
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.DOCUMENT_FETCHED)
                    .source(ctx.getCrawler())
                    .subject(response)
                    .crawlDocRecord(docRecord)
                    .build());
            return true;
        }

        String eventType = null;
        if (state.isOneOf(CrawlDocState.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }

        ctx.fire(CrawlerEvent.builder()
                .name(eventType)
                .source(ctx.getCrawler())
                .subject(response)
                .crawlDocRecord(docRecord)
                .build());

        // At this stage, the URL is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on http fetch methods supported.
        return continueOnBadState(ctx, getHttpMethod(), originalCrawlDocState);
    }

    private boolean continueOnBadState(
            WebImporterPipelineContext ctx,
            HttpMethod method,
            CrawlDocState originalCrawlDocState) {
        // Note: a disabled method will never get here,
        // and when both are enabled, GET always comes after HEAD.
        var headSupport = ctx.getConfig().getFetchHttpHead();
        var getSupport = ctx.getConfig().getFetchHttpGet();

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
        }
        if (HttpMethod.GET.is(method)) {
            // if method is required, we end it here.
            if (HttpMethodSupport.REQUIRED.is(getSupport)) {
                return false;
            }
            // if method is optional and HEAD was enabled and successful,
            // we continue
            return HttpMethodSupport.OPTIONAL.is(getSupport)
                    && HttpMethodSupport.isEnabled(headSupport)
                    && originalCrawlDocState.isGoodState();
        }

        // If a custom implementation introduces another http method,
        // we do not know the intent so end here.
        return false;
    }

    /**
     * Whether a separate HTTP HEAD request was requested (configured)
     * and was performed already.
     * @param ctx pipeline context
     * @return <code>true</code> if method is GET and HTTP HEAD was performed
     */
    protected boolean wasHttpHeadPerformed(WebImporterPipelineContext ctx) {
        // If GET and fetching HEAD was requested, we ran filters already, skip.
        return getHttpMethod() == HttpMethod.GET
                &&  HttpMethodSupport.isEnabled(
                        ctx.getConfig().getFetchHttpHead());
    }
}