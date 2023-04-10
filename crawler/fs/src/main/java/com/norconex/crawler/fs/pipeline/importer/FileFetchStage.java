/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.fs.pipeline.importer;

import java.time.ZonedDateTime;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.pipeline.DocumentPipelineUtil;
import com.norconex.crawler.core.pipeline.importer.AbstractImporterStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.fs.doc.FsDocRecord;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.importer.doc.DocMetadata;

import lombok.NonNull;

/**
 * <p>Fetches (i.e. download/copy for processing) a document and/or its
 * metadata (e.g., file properties) depending on supplied
 * {@link FetchDirective}.</p>
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
class FileFetchStage extends AbstractImporterStage {

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

        var docRecord = (FsDocRecord) ctx.getDocRecord();
        var fetcher = (FileFetcher) ctx.getCrawler().getFetcher();
        FileFetchResponse response;
        try {
            response = fetcher.fetch(new FileFetchRequest(
                    ctx.getDocument(), getFetchDirective()));
        } catch (FetchException e) {
            throw new CrawlerException("Could not fetch file: "
                    + ctx.getDocRecord().getReference(), e);
        }
        var originalCrawlDocState = docRecord.getState();

        docRecord.setCrawlDate(ZonedDateTime.now());
        docRecord.setFile(response.isFile());
        docRecord.setFolder(response.isFolder());

        //--- Add collector-specific metadata ---
        var meta = ctx.getDocument().getMetadata();
        meta.set(DocMetadata.CONTENT_TYPE, docRecord.getContentType());
        meta.set(DocMetadata.CONTENT_ENCODING, docRecord.getContentEncoding());

        var state = response.getCrawlDocState();
        //TODO really do here??  or just do it if different than response?
        docRecord.setState(state);
//        if (CrawlDocState.UNMODIFIED.equals(state)) {
//            ctx.fire(CrawlerEvent.builder()
//                    .name(CrawlerEvent.REJECTED_UNMODIFIED)
//                    .source(ctx.getCrawler())
//                    .subject(response)
//                    .crawlDocRecord(docRecord)
//                    .build());
//            return false;
//        }
        if (state.isGoodState()) {
            ctx.fire(CrawlerEvent.builder()
                    .name(FetchDirective.METADATA.is(getFetchDirective())
                            ? CrawlerEvent.DOCUMENT_METADATA_FETCHED
                            : CrawlerEvent.DOCUMENT_FETCHED)
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

        // At this stage, the ref is either unsupported or with a bad status.
        // In either case, whether we break the pipeline or not (returning
        // false or true) depends on http fetch methods supported.
        return DocumentPipelineUtil.shouldAbortOnBadStatus(
                ctx, originalCrawlDocState, getFetchDirective());
    }

//    private boolean continueOnBadState(
//            FsImporterPipelineContext ctx,
//            FetchDirective directive,
//            CrawlDocState originalCrawlDocState) {
//        // Note: a disabled directive will never get here,
//        // and when both are enabled, DOCUMENT always comes after METADATA.
//        var metaSupport = ctx.getConfig().getMetadataFetchSupport();
//        var docSupport = ctx.getConfig().getDocumentFetchSupport();
//
//        //--- HEAD ---
//        if (FetchDirective.METADATA.is(directive)) {
//            // if directive is required, we end it here.
//            if (FetchDirectiveSupport.REQUIRED.is(metaSupport)) {
//                return false;
//            }
//            // if head is optional and there is a GET, we continue
//            return FetchDirectiveSupport.OPTIONAL.is(metaSupport)
//                    && FetchDirectiveSupport.isEnabled(docSupport);
//
//        //--- GET ---
//        }
//        if (FetchDirective.DOCUMENT.is(directive)) {
//            // if directive is required, we end it here.
//            if (FetchDirectiveSupport.REQUIRED.is(docSupport)) {
//                return false;
//            }
//            // if directive is optional and HEAD was enabled and successful,
//            // we continue
//            return FetchDirectiveSupport.OPTIONAL.is(docSupport)
//                    && FetchDirectiveSupport.isEnabled(metaSupport)
//                    && originalCrawlDocState.isGoodState();
//        }
//
//        // If a custom implementation introduces another http directive,
//        // we do not know the intent so end here.
//        return false;
//    }

//    /**
//     * Whether a separate HTTP HEAD request was requested (configured)
//     * and was performed already.
//     * @param ctx pipeline context
//     * @return <code>true</code> if method is GET and HTTP HEAD was performed
//     */
//    protected boolean wasHttpHeadPerformed(WebImporterPipelineContext ctx) {
//        // If GET and fetching HEAD was requested, we ran filters already, skip.
//        return getHttpMethod() == HttpMethod.GET
//                &&  HttpMethodSupport.isEnabled(
//                        ctx.getConfig().getFetchHttpHead());
//    }
}