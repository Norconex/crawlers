/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.fs.pipelines.importer.stages;

import java.time.ZonedDateTime;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchUtil;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.crawler.fs.fetch.FileFetcher;
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
     * @param ctx pipeline context
     * @return <code>true</code> if we continue processing.
     */
    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        // If stage is for a directive that was disabled, skip
        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())) {
            return true;
        }

        var docRecord = (FsCrawlDocContext) ctx.getDoc().getDocContext();
        var fetcher = (FileFetcher) ctx.getCrawlerContext().getFetcher();
        FileFetchResponse response;
        try {
            response = fetcher.fetch(
                    new FileFetchRequest(
                            ctx.getDoc(), getFetchDirective()));
        } catch (FetchException e) {
            throw new CrawlerException(
                    "Could not fetch file: "
                            + ctx.getDoc().getDocContext().getReference(),
                    e);
        }
        var originalCrawlDocState = docRecord.getState();

        docRecord.setCrawlDate(ZonedDateTime.now());
        docRecord.setFile(response.isFile());
        docRecord.setFolder(response.isFolder());

        //--- Add collector-specific metadata ---
        var meta = ctx.getDoc().getMetadata();
        meta.set(DocMetaConstants.CONTENT_TYPE, docRecord.getContentType());
        meta.set(DocMetaConstants.CONTENT_ENCODING, docRecord.getCharset());

        var state = response.getResolutionStatus();
        //TODO really do here??  or just do it if different than response?
        docRecord.setState(state);

        if (state.isGoodState()) {
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(
                                    FetchDirective.METADATA.is(
                                            getFetchDirective())
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

        // At this stage, the ref is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on http fetch methods supported.
        return FetchUtil.shouldContinueOnBadStatus(
                ctx.getCrawlerContext(), originalCrawlDocState,
                getFetchDirective());
    }
}
