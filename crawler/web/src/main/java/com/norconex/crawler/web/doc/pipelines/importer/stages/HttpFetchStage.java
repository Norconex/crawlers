/* Copyright 2015-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer.stages;

import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchUtil;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.pipelines.importer.WebImporterPipelineUtil;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.importer.doc.DocMetaConstants;

import lombok.NonNull;

/**
 * <p>Fetches (i.e. download for processing) a document and/or its metadata
 * (HTTP response headers) depending on supplied {@link HttpMethod}.</p>
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
public class HttpFetchStage extends AbstractImporterStage {

    public HttpFetchStage(@NonNull FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    /**
     * Only does something if appropriate. For instance,
     * if a separate HTTP HEAD request was NOT required to be performed,
     * this method will never get invoked for a HEAD method.
     * @param ctx pipeline context
     * @return <code>true</code> if we continue processing.
     */
    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        // If stage is for a method that was disabled, skip
        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())) {
            return true;
        }

        var docRecord = ctx.getDoc().getDocContext();
        var fetcher = (HttpFetcher) ctx.getCrawlerContext().getFetcher();

        var httpMethod = FetchDirective.METADATA.is(getFetchDirective())
                ? HttpMethod.HEAD
                : HttpMethod.GET;

        HttpFetchResponse response;
        try {
            response = fetcher.fetch(
                    new HttpFetchRequest(ctx.getDoc(), httpMethod));
        } catch (FetchException e) {
            throw new CrawlerException(
                    "Could not fetch URL: "
                            + ctx.getDoc().getDocContext().getReference(),
                    e);
        }
        var originalCrawlDocState = docRecord.getState();

        docRecord.setCrawlDate(ZonedDateTime.now());

        //--- Add collector-specific metadata ---
        var meta = ctx.getDoc().getMetadata();
        meta.set(DocMetaConstants.CONTENT_TYPE, docRecord.getContentType());
        meta.set(DocMetaConstants.CONTENT_ENCODING, docRecord.getCharset());
        meta.set(
                WebDocMetadata.ORIGINAL_REFERENCE,
                docRecord.getOriginalReference());

        //-- Deal with redirects ---
        var redirectURL = response.getRedirectTarget();

        if (StringUtils.isNotBlank(redirectURL)) {
            WebImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        var state = response.getResolutionStatus();
        //TODO really do here??  or just do it if different than response?
        docRecord.setState(state);
        if (CrawlDocStatus.UNMODIFIED.equals(state)) {
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_UNMODIFIED)
                            .source(ctx.getCrawlerContext())
                            .subject(response)
                            .docContext(docRecord)
                            .build());
            return false;
        }
        if (state.isGoodState()) {
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(
                                    FetchDirective.METADATA
                                            .is(getFetchDirective())
                                                    ? CrawlerEvent.DOCUMENT_METADATA_FETCHED
                                                    : CrawlerEvent.DOCUMENT_FETCHED)
                            .source(ctx.getCrawlerContext())
                            .subject(response)
                            .docContext(docRecord)
                            .build());
            return true;
        }

        String eventType = null;
        if (state.isOneOf(CrawlDocStatus.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }

        ctx.getCrawlerContext().fire(
                CrawlerEvent.builder()
                        .name(eventType)
                        .source(ctx.getCrawlerContext())
                        .subject(response)
                        .docContext(docRecord)
                        .build());

        // At this stage, the URL is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on the fetch directives supported.
        return FetchUtil.shouldContinueOnBadStatus(
                ctx.getCrawlerContext(), originalCrawlDocState,
                getFetchDirective());
    }
}
