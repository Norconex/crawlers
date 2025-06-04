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
import static java.io.InputStream.nullInputStream;
import static java.util.Optional.ofNullable;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategy;

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

        var docRecord = ctx.docContext();
        if (ctx.doc() == null) {
            ctx.doc(new CrawlDoc(
                    docRecord, CachedInputStream.cache(nullInputStream())));
        }
        var doc = ctx.doc();
        var cachedDocRecord = doc.getCachedDocContext();

        //--- Ensure we have a state -------------------------------------------
        if (docRecord.getState() == null) {
            LOG.warn(
                    "Reference status is unknown for \"{}\". "
                            + "This should not happen. Assuming bad status.",
                    docRecord.getReference());
            docRecord.setState(CrawlDocStatus.BAD_STATUS);
        }

        try {

            // important to call this before copying properties further down
            //TODO revisit all the before/after on crawlerImpl
            ofNullable(ctx
                    .crawlContext()
                    .getCallbacks()
                    .getBeforeDocumentFinalizing())
                            .ifPresent(bdf -> bdf.accept(ctx.crawlContext(),
                                    doc));

            //--- If doc crawl was incomplete, set missing info from cache -----
            // If document is not new or modified, it did not go through
            // the entire crawl life cycle for a document so maybe not all info
            // could be gathered for a reference.  Since we do not want to lose
            // previous information when the crawl was effective/good
            // we copy it all that is non-null from cache.
            if (!docRecord.getState().isNewOrModified()
                    && cachedDocRecord != null) {
                //TODO maybe new CrawlData instances should be initialized with
                // some of cache data available instead?
                BeanUtil.copyPropertiesOverNulls(docRecord, cachedDocRecord);
            }

            dealWithBadState(ctx);

        } catch (Exception e) {
            LOG.error(
                    "Could not finalize processing of: {} ({})",
                    docRecord.getReference(), e.getMessage(), e);
        }

        //--- Mark reference as Processed --------------------------------------
        try {
            ctx.crawlContext()
                    .getDocLedger()
                    .processed(docRecord);

            markReferenceVariationsAsProcessed(ctx);

        } catch (Exception e) {
            LOG.error(
                    "Could not mark reference as processed: {} ({})",
                    docRecord.getReference(), e.getMessage(), e);
        } finally {
            ofNullable(ctx
                    .crawlContext()
                    .getCallbacks()
                    .getAfterDocumentFinalizing()).ifPresent(
                            adf -> adf.accept(ctx.crawlContext(), doc));
        }

        try {
            doc.getInputStream().dispose();
        } catch (Exception e) {
            LOG.error("Could not dispose of resources.", e);
        }
    }

    // passing CrawlDoc here because sometimes it can be null in context
    // and we do not want to set one on context.
    private static void dealWithBadState(ProcessContext ctx) {
        var doc = ctx.doc();
        var docContext = ctx.docContext();

        //--- Deal with bad states (if not already deleted) ----------------
        if (!docContext.getState().isGoodState()
                && !docContext.getState().isOneOf(CrawlDocStatus.DELETED)) {

            var cachedDocContext =
                    ofNullable(doc.getCachedDocContext()).orElse(null);
            if (cachedDocContext != null
                    && cachedDocContext.getState() == null) {
                LOG.warn("""
                        Got a cached document from previous run with no \
                        crawl state associated. This should not happen. \
                        Will treat it as ERROR. Doc info: {}""",
                        docContext);
                cachedDocContext.setState(CrawlDocStatus.ERROR);
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
                    ctx.crawlContext()
                            .getCrawlConfig()
                            .getSpoiledReferenceStrategizer())
                    .map(srs -> srs.resolveSpoiledReferenceStrategy(
                            ctx.docContext().getReference(),
                            ctx.docContext().getState()))
                    .orElse(DEFAULT_FALLBACK_STRATEGY);

            if (strategy == SpoiledReferenceStrategy.IGNORE) {
                LOG.debug(
                        "Ignoring spoiled reference: {}",
                        docContext.getReference());
            } else if (strategy == SpoiledReferenceStrategy.DELETE) {
                // Delete if previous state exists and is not already
                // marked as deleted.
                if (cachedDocContext != null
                        && !cachedDocContext.getState().isOneOf(
                                CrawlDocStatus.DELETED)) {
                    ProcessDelete.execute(ctx);
                }
            } else // GRACE_ONCE:
            // Delete if previous state exists and is a bad state,
            // but not already marked as deleted.
            if (cachedDocContext != null
                    && !cachedDocContext.getState().isOneOf(
                            CrawlDocStatus.DELETED)) {
                if (!cachedDocContext.getState().isGoodState()) {
                    ProcessDelete.execute(ctx);
                } else {
                    LOG.debug("""
                            This spoiled reference is\s\
                            being graced once (will be deleted\s\
                            next time if still spoiled): {}""",
                            docContext.getReference());
                }
            }
        }
    }

    private static void markReferenceVariationsAsProcessed(
            ProcessContext ctx) {
        // Mark original URL as processed
        var originalRef = ctx.docContext().getOriginalReference();
        var finalRef = ctx.docContext().getReference();
        if (StringUtils.isNotBlank(originalRef)
                && ObjectUtils.notEqual(originalRef, finalRef)) {

            var originalDocRec = ctx.docContext().withReference(originalRef);
            originalDocRec.setOriginalReference(null);
            ctx.crawlContext()
                    .getDocLedger()
                    .processed(originalDocRec);
        }
    }

}
