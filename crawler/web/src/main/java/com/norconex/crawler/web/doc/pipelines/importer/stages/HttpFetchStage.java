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
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchUtil;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.web.doc.WebCrawlEntry;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.pipelines.importer.WebImporterPipelineUtil;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.WebFetchRequest;
import com.norconex.crawler.web.fetch.WebFetchResponse;
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
    protected boolean executeStage(ImporterPipelineContext pipeCtx) {
        // If stage is for a method that was disabled, skip
        if (!pipeCtx.isFetchDirectiveEnabled(getFetchDirective())) {
            return true;
        }

        var docContext = pipeCtx.getDocContext();
        var webEntry = (WebCrawlEntry) docContext.getCurrentCrawlEntry();
        var doc = docContext.getDoc();
        var crawlSession = pipeCtx.getCrawlSession();
        var crawlContext = crawlSession.getCrawlContext();
        var fetcher = crawlContext.getFetcher();

        var httpMethod = FetchDirective.METADATA.is(getFetchDirective())
                ? HttpMethod.HEAD
                : HttpMethod.GET;

        WebFetchResponse response;
        try {
            var request = new WebFetchRequest(doc, httpMethod);
            request.setCrawlDocContext(pipeCtx.getDocContext());
            response = (WebFetchResponse) fetcher.fetch(request);
        } catch (FetchException e) {
            throw new CrawlerException("Could not fetch URL: "
                    + docContext.getReference(), e);
        }
        var originalOutcome = webEntry.getProcessingOutcome();
        var previousEntry =
                docContext.getPreviousCrawlEntry() instanceof WebCrawlEntry e
                        ? e
                        : null;

        webEntry.setProcessedAt(ZonedDateTime.now());
        webEntry.setLastModified(webEntry.getLastModified() != null
                ? webEntry.getLastModified()
                : previousEntry != null
                        ? previousEntry.getLastModified()
                : null);
        webEntry.setContentType(doc.getContentType() != null
                ? doc.getContentType()
                : previousEntry != null
                        ? previousEntry.getContentType()
                : null);
        webEntry.setCharset(doc.getCharset() != null
                ? doc.getCharset()
                : previousEntry != null
                        ? previousEntry.getCharset()
                : null);

        //--- Add collector-specific metadata ---
        var meta = doc.getMetadata();
        meta.set(DocMetaConstants.CONTENT_TYPE, doc.getContentType());
        meta.set(DocMetaConstants.CONTENT_ENCODING, doc.getCharset());
        meta.set(
                WebDocMetadata.ORIGINAL_REFERENCE,
                webEntry.getReferenceTrail().isEmpty() ? null
                        : webEntry.getReferenceTrail().get(0));

        // Store HTTP status code/phrase for event listeners
        webEntry.setHttpStatusCode(response.getStatusCode());
        webEntry.setHttpReasonPhrase(response.getReasonPhrase());

        //-- Deal with redirects ---
        var redirectURL = response.getRedirectTarget();

        if (StringUtils.isNotBlank(redirectURL)) {
            WebImporterPipelineUtil.queueRedirectURL(
                    pipeCtx, response, redirectURL);
            return false;
        }

        var outcome = response.getProcessingOutcome();
        webEntry.setProcessingOutcome(outcome);

        if (ProcessingOutcome.UNMODIFIED.equals(outcome)) {
            crawlSession.fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_UNMODIFIED)
                            .source(crawlSession)
                            .crawlSession(crawlSession)
                            .crawlEntry(webEntry)
                            .build());
            return false;
        }
        if (outcome != null && outcome.isGoodState()) {
            crawlSession.fire(CrawlerEvent.builder()
                    .name(FetchDirective.METADATA.is(
                            getFetchDirective())
                                    ? CrawlerEvent.DOCUMENT_METADATA_FETCHED
                                    : CrawlerEvent.DOCUMENT_FETCHED)
                    .source(crawlSession)
                    .crawlSession(crawlSession)
                    .crawlEntry(webEntry)
                    .build());
            return true;
        }

        String eventType;
        if (outcome != null && outcome.isOneOf(ProcessingOutcome.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }

        crawlSession.fire(
                CrawlerEvent.builder()
                        .name(eventType)
                        .source(crawlSession)
                        .crawlSession(crawlSession)
                        .crawlEntry(webEntry)
                        .build());

        // At this stage, the URL is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on the fetch directives supported.
        return FetchUtil.shouldContinueOnBadStatus(
                crawlContext, originalOutcome,
                getFetchDirective());
    }
}
