/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.fs.doc.pipelines.importer.stages;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchUtil;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.fs.doc.FsCrawlEntry;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.importer.doc.DocMetaConstants;

import lombok.NonNull;

/**
 * <p>Fetches (i.e. download/copy for processing) a document and/or its
 * metadata (e.g., file properties) depending on supplied
 * {@link FetchDirective}.</p>
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
public class FileFetchStage extends AbstractImporterStage {

    public FileFetchStage(@NonNull FetchDirective directive) {
        super(directive);
    }

    /**
     * Only does something if appropriate. For instance,
     * if a separate HTTP HEAD request was NOT required to be performed,
     * this method will never get invoked for a HEAD method.
     * @param pipeCtx pipeline context
     * @return <code>true</code> if we continue processing.
     */
    @Override
    protected boolean executeStage(ImporterPipelineContext pipeCtx) {
        // If stage is for a directive that was disabled, skip
        if (!pipeCtx.isFetchDirectiveEnabled(getFetchDirective())) {
            return true;
        }

        var docContext = pipeCtx.getDocContext();
        var fsEntry = (FsCrawlEntry) docContext.getCurrentCrawlEntry();
        var doc = docContext.getDoc();
        var crawlSession = pipeCtx.getCrawlSession();
        var crawlContext = crawlSession.getCrawlContext();
        var fetcher = crawlContext.getFetcher();
        FileFetchResponse response;
        try {
            response = (FileFetchResponse) fetcher.fetch(
                    new FileFetchRequest(doc, getFetchDirective()));
        } catch (FetchException e) {
            throw new CrawlerException("Could not fetch file: "
                    + docContext.getReference(), e);
        }
        var originalOutcome = fsEntry.getProcessingOutcome();
        var previousEntry = docContext.getPreviousCrawlEntry();

        fsEntry.setProcessedAt(ZonedDateTime.now());
        fsEntry.setFile(response.isFile());
        fsEntry.setFolder(response.isFolder());

        var lastModifiedMillis = doc.getMetadata()
                .getLong(FsDocMetadata.LAST_MODIFIED);
        fsEntry.setLastModified(lastModifiedMillis != null
                ? ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(lastModifiedMillis),
                        ZoneOffset.UTC)
                : previousEntry != null
                        ? previousEntry.getLastModified()
                : null);
        fsEntry.setContentType(doc.getContentType() != null
                ? doc.getContentType()
                : previousEntry != null
                        ? previousEntry.getContentType()
                : null);
        fsEntry.setCharset(doc.getCharset() != null
                ? doc.getCharset()
                : previousEntry != null
                        ? previousEntry.getCharset()
                : null);

        //--- Add collector-specific metadata ---
        var meta = doc.getMetadata();
        meta.set(DocMetaConstants.CONTENT_TYPE, doc.getContentType());
        meta.set(DocMetaConstants.CONTENT_ENCODING, doc.getCharset());

        var outcome = response.getProcessingOutcome();
        fsEntry.setProcessingOutcome(outcome);

        if (outcome.isGoodState()) {
            crawlSession.fire(CrawlerEvent.builder()
                    .name(FetchDirective.METADATA.is(
                            getFetchDirective())
                                    ? CrawlerEvent.DOCUMENT_METADATA_FETCHED
                                    : CrawlerEvent.DOCUMENT_FETCHED)
                    .source(crawlSession)
                    .crawlSession(crawlSession)
                    .crawlEntry(fsEntry)
                    .build());
            return true;
        }

        String eventType;
        if (outcome.isOneOf(ProcessingOutcome.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
        }

        crawlSession.fire(
                CrawlerEvent.builder()
                        .name(eventType)
                        .source(crawlSession)
                        .crawlSession(crawlSession)
                        .crawlEntry(fsEntry)
                        .build());

        return FetchUtil.shouldContinueOnBadStatus(
                crawlContext, originalOutcome, getFetchDirective());
    }
}
