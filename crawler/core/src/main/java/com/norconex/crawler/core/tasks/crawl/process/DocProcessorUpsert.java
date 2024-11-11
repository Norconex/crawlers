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
package com.norconex.crawler.core.tasks.crawl.process;

import org.apache.commons.io.input.NullInputStream;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.crawl.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;
import com.norconex.importer.response.ImporterResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class DocProcessorUpsert {

    private DocProcessorUpsert() {
    }

    static void execute(DocProcessorContext ctx) {
        if (importDocument(ctx)) {
            processImportResponse(ctx);
        }
    }

    private static boolean importDocument(DocProcessorContext ctx) {
        // The importer pipeline also takes care of fetching
        //TODO shall fetching be handled by core, and we just pass
        // fetched doc to importer pipeline?
        var docRecord = ctx.doc().getDocContext();

        LOG.debug("Processing reference: {}", ctx.doc().getReference());

        var response = ctx
                .crawlerContext()
                .getDocPipelines()
                .getImporterPipeline()
                .apply(new ImporterPipelineContext(ctx.crawlerContext(),
                        ctx.doc()));
        ctx.importerResponse(response);

        // no response means rejected even if it should not be the
        // way to do it
        if (response == null) {
            if ((docRecord.getState() != null)
                    && docRecord.getState().isNewOrModified()) {
                docRecord.setState(DocResolutionStatus.REJECTED);
            }
            DocProcessorFinalize.execute(ctx);
            return false;
        }
        return true;
    }

    // commit is upsert this method is recursively invoked for children
    private static void processImportResponse(DocProcessorContext ctx) {

        if (!commitOrRejectDocument(ctx)) {
            DocProcessorFinalize.execute(ctx);
            return;
        }

        var children = ctx.importerResponse().getNestedResponses();
        for (ImporterResponse childResponse : children) {

            //TODO have a createEmbeddedDoc method instead?
            // TODO have a docInfoFactory instead and arguments
            /// dictate whether it is a child, embedded, or top level
            var childDocRec = ctx
                    .crawlerContext()
                    .newDocContext(ctx.docContext());
            childDocRec.setReference(childResponse.getReference());
            var childCachedDocRec = ctx
                    .crawlerContext()
                    .getDocProcessingLedger()
                    .getCached(childResponse.getReference())
                    .orElse(null);

            // Here we create a CrawlDoc since the document from the response
            // is (or can be) just a Doc, which does not hold all required
            // properties for crawling.
            //TODO refactor Doc vs CrawlDoc to have only one instance
            // so we do not have to create such copy?
            var childResponseDoc = childResponse.getDoc();
            var childCrawlDoc = new CrawlDoc(
                    childDocRec, childCachedDocRec,
                    childResponseDoc == null
                            ? CachedInputStream.cache(new NullInputStream(0))
                            : childResponseDoc.getInputStream());
            if (childResponseDoc != null) {
                childCrawlDoc.getMetadata().putAll(
                        childResponseDoc.getMetadata());
            }

            var childCtx = new DocProcessorContext()
                    .crawlerContext(ctx.crawlerContext())
                    .orphan(ctx.orphan())
                    .doc(childCrawlDoc)
                    .docContext(childDocRec)
                    .importerResponse(childResponse);

            processImportResponse(childCtx);
        }
    }

    private static boolean commitOrRejectDocument(DocProcessorContext ctx) {
        var docRecord = ctx.doc().getDocContext();
        var response = ctx.importerResponse();

        // ok, there's a resonse, but is it good?
        var msg = response.toString();
        if (!response.getNestedResponses().isEmpty()) {
            msg += "(" + response.getNestedResponses().size()
                    + " nested responses.)";
        }

        if (response.isSuccess()) {
            ctx.crawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.DOCUMENT_IMPORTED)
                            .source(ctx.crawlerContext())
                            .docContext(docRecord)
                            .subject(response)
                            .message(msg)
                            .build());
            ctx.crawlerContext().getDocPipelines().getCommitterPipeline()
                    .accept(
                            new CommitterPipelineContext(ctx.crawlerContext(),
                                    ctx.doc()));
            return true;
        }

        docRecord.setState(DocResolutionStatus.REJECTED);
        ctx.crawlerContext().fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_IMPORT)
                        .source(ctx.crawlerContext())
                        .docContext(docRecord)
                        .subject(response)
                        .message(msg)
                        .build());
        LOG.debug(
                "Importing unsuccessful for \"{}\": {}",
                docRecord.getReference(),
                response.getDescription());

        return false;

        //TODO Fire an event here? If we get here, the importer did
        //not kick in,
        //so do not fire REJECTED_IMPORT (like it used to).
        //Errors should have fired
        //something already so do not fire two REJECTED... but
        //what if a previous issue did not fire a REJECTED_*?
        //This should not happen, but keep an eye on that.
        //OR do we want to always fire REJECTED_IMPORT on import failure
        //(in addition to whatever) and maybe a new REJECTED_COLLECTOR
        //when it did not reach the importer module?

    }
}
