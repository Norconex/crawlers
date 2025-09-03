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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static com.norconex.crawler.core.doc.operations.spoil.impl.GenericSpoiledReferenceStrategizerConfig.DEFAULT_FALLBACK_STRATEGY;
import static java.util.Optional.ofNullable;

import java.util.HashSet;
import java.util.Optional;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategy;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.ledger.ProcessingStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ProcessFinalize {

    private ProcessFinalize() {
    }

    static void execute(ProcessContext ctx) {

        // only finalize a record if not already finalized and if
        // there is a doc record (meaning a reference was loaded).
        if (ctx.finalized() || ctx.docContext() == null) {
            return;
        }

        ctx.finalized(true);

        var crawlCtx = ctx.crawlSession().getCrawlContext();
        var docCtx = ctx.docContext();
        var currentEntry = docCtx.getCurrentCrawlEntry();

        //        if (docCtx.getDoc() == null) {
        //            docCtx. doc(new Doc(docCtx.getReference());
        //        }
        //        var doc = ctx.doc();
        //        var cachedDocRecord = doc.getCachedDocContext();

        //--- Ensure we have a state -------------------------------------------
        if (currentEntry.getProcessingOutcome() == null) {
            LOG.warn("Processing outcome is unknown for \"{}\". "
                    + "This should not happen. Assuming bad status.",
                    docCtx.getReference());
            currentEntry.setProcessingOutcome(ProcessingOutcome.BAD_STATUS);
        }

        try {

            // important to call this before copying properties further down
            //TODO revisit all the before/after on crawlerImpl
            ofNullable(crawlCtx
                    .getCallbacks()
                    .getBeforeDocumentFinalizing())
                            .ifPresent(bdf -> bdf.accept(ctx.crawlSession(),
                                    docCtx.getDoc()));

            //--- If doc crawl was incomplete, set missing info from cache -----
            // If document is not new or modified, it did not go through
            // the entire crawl life cycle for a document so maybe not all info
            // could be gathered for a reference.  Since we do not want to lose
            // previous information when the crawl was effective/good
            // we copy it all that is non-null from cache.
            if (!docCtx.getCurrentCrawlEntry().getProcessingOutcome()
                    .isNewOrModified()
                    && docCtx.getPreviousCrawlEntry() != null) {
                //TODO maybe new CrawlData instances should be initialized with
                // some of cache data available instead?
                BeanUtil.copyPropertiesOverNulls(docCtx,
                        docCtx.getPreviousCrawlEntry());
            }

            dealWithBadState(ctx);

        } catch (Exception e) {
            LOG.error(
                    "Could not finalize processing of: {} ({})",
                    docCtx.getReference(), e.getMessage(), e);
        }

        //--- Mark reference as Processed --------------------------------------
        try {
            currentEntry.setProcessingStatus(ProcessingStatus.PROCESSED);
            crawlCtx.getCrawlEntryLedger().updateEntry(currentEntry);

            markReferenceVariationsAsProcessed(ctx);

        } catch (Exception e) {
            LOG.error(
                    "Could not mark reference as processed: {} ({})",
                    docCtx.getReference(), e.getMessage(), e);
        } finally {
            ofNullable(crawlCtx
                    .getCallbacks()
                    .getAfterDocumentFinalizing()).ifPresent(
                            adf -> adf.accept(ctx.crawlSession(),
                                    docCtx.getDoc()));
        }

        try {
            docCtx.getDoc().getInputStream().dispose();
        } catch (Exception e) {
            LOG.error("Could not dispose of resources.", e);
        }
    }

    // passing CrawlDoc here because sometimes it can be null in context
    // and we do not want to set one on context.
    private static void dealWithBadState(ProcessContext ctx) {
        var crawlCtx = ctx.crawlSession().getCrawlContext();
        var docCtx = ctx.docContext();
        docCtx.getDoc();
        var currentEntry = docCtx.getCurrentCrawlEntry();

        //--- Deal with bad states (if not already deleted) ----------------
        if (!currentEntry.getProcessingOutcome().isGoodState()
                && !currentEntry.getProcessingOutcome()
                        .isOneOf(ProcessingOutcome.DELETED)) {

            var previousEntry = docCtx.getPreviousCrawlEntry();
            if (previousEntry != null
                    && previousEntry.getProcessingOutcome() == null) {
                LOG.warn("""
                        Got a cached document from previous run with no \
                        crawl state associated. This should not happen. \
                        Will treat it as ERROR. Doc info: {}""",
                        docCtx);
                previousEntry.setProcessingOutcome(ProcessingOutcome.ERROR);
                return;
            }

            //TODO If duplicate, consider it as spoiled if a cache version
            // exists in a good state.
            // This involves elaborating the concept of duplicate
            // or "reference change" in this core project. Otherwise there
            // is the slim possibility right now that a Collector
            // implementation marking references as duplicate may
            // generate orphans (which may be caught later based
            // on how orphans are handled, but they should not be ever
            // considered orphans in the first place).
            // This could remove the need for the
            // markReferenceVariationsAsProcessed(...) method

            var strategy = Optional.ofNullable(
                    crawlCtx.getCrawlConfig()
                            .getSpoiledReferenceStrategizer())
                    .map(srs -> srs.resolveSpoiledReferenceStrategy(
                            docCtx.getReference(),
                            docCtx.getCurrentCrawlEntry()
                                    .getProcessingOutcome()))
                    .orElse(DEFAULT_FALLBACK_STRATEGY);

            if (strategy == SpoiledReferenceStrategy.IGNORE) {
                LOG.debug(
                        "Ignoring spoiled reference: {}",
                        docCtx.getReference());
            } else if (strategy == SpoiledReferenceStrategy.DELETE) {
                // Delete if previous state exists and is not already
                // marked as deleted.
                if (previousEntry != null
                        && !previousEntry.getProcessingOutcome().isOneOf(
                                ProcessingOutcome.DELETED)) {
                    ProcessDelete.execute(ctx);
                }
            } else // GRACE_ONCE:
            // Delete if previous state exists and is a bad state,
            // but not already marked as deleted.
            if (previousEntry != null
                    && !previousEntry.getProcessingOutcome().isOneOf(
                            ProcessingOutcome.DELETED)) {
                if (!previousEntry.getProcessingOutcome().isGoodState()) {
                    ProcessDelete.execute(ctx);
                } else {
                    LOG.debug("""
                            This spoiled reference is\s\
                            being graced once (will be deleted\s\
                            next time if still spoiled): {}""",
                            docCtx.getReference());
                }
            }
        }
    }

    private static void markReferenceVariationsAsProcessed(
            ProcessContext ctx) {
        // Mark reference trail as processed
        var crawlEntry = ctx.docContext().getCurrentCrawlEntry();
        new HashSet<>(
                ctx.docContext().getCurrentCrawlEntry().getReferenceTrail())
                        .forEach(ref -> {
                            var trailEntry = crawlEntry.withReference(ref);
                            trailEntry.setProcessingStatus(
                                    ProcessingStatus.PROCESSED);
                            ctx.crawlSession()
                                    .getCrawlContext()
                                    .getCrawlEntryLedger()
                                    .updateEntry(trailEntry);
                        });

        //        var originalRef = ctx.docContext().getOriginalReference();
        //        var finalRef = ctx.docContext().getReference();
        //        if (StringUtils.isNotBlank(originalRef)
        //                && ObjectUtils.notEqual(originalRef, finalRef)) {
        //
        //            var originalDocRec = ctx.docContext().withReference(originalRef);
        //            originalDocRec.setOriginalReference(null);
        //            ctx.crawlContext()
        //                    .getCrawlEntryLedger()
        //                    .processed(originalDocRec);
        //        }
    }

}
