/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.queueread;

import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN;
import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_END;
import static java.util.Optional.ofNullable;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.grid.core.pipeline.BaseGridPipelineTask;
import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.importer.doc.DocContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main crawler task, getting references from the crawl queue, fetch them,
 * and process them until the queue is empty.
 */
@Slf4j
@RequiredArgsConstructor
public class CrawlProcessQueueTask
        extends BaseGridPipelineTask<CrawlerContext> {

    /**
     * What to do with ALL current entries in the queue.
     */
    public enum ProcessQueueAction {
        CRAWL_ALL, DELETE_ALL
    }

    //    public enum ProcessDocsAs {
    //        REGULAR, ORPHANS
    //    }

    private final ProcessQueueAction queueAction;
    //    private final ProcessDocsAs processAs;

    @Override
    public void execute(CrawlerContext ctx) {
        //        if (ctx.isStopping()) {
        if (isStopRequested()) {
            return;
        }
        LOG.info("Processing crawler queue...");
        try {
            ofNullable(ctx.getCallbacks().getBeforeCrawlTask())
                    .ifPresent(cb -> cb.accept(ctx));
            ConcurrentUtil.get(ConcurrentUtil.run(
                    index -> () -> processQueue(ctx, index),
                    ctx.getConfiguration().getNumThreads()));
        } finally {
            ofNullable(ctx.getCallbacks().getAfterCrawlTask())
                    .ifPresent(cb -> cb.accept(ctx));
        }
    }

    // just invoked in its own thread
    void processQueue(CrawlerContext ctx, int threadIndex) {
        LOG.debug("Crawler thread #{} starting...", threadIndex);
        Thread.currentThread().setName(ctx.getId() + "#" + threadIndex);
        LogUtil.setMdcCrawlerId(ctx.getId());

        try {
            var activityChecker = new CrawlActivityChecker(
                    ctx, queueAction == ProcessQueueAction.DELETE_ALL);
            ctx.fire(CRAWLER_RUN_THREAD_BEGIN, Thread.currentThread());
            // TODO shall we check for "stopped" and other states?
            // abort now if we've reach configured max documents or
            // other ending conditions
            //            // At this point all threads/nodes shall reach the same
            //            // conclusion and break, effectively ending crawling.
            while (!isStopRequested()
                    && !activityChecker.isMaxDocsApplicableAndReached()
                    && processNextInQueue(ctx, activityChecker))
                ;
        } catch (Exception e) {
            //TODO also check here for configured exceptions that should
            // end the crawl?
            LOG.error("Problem in thread execution.", e);
        } finally {
            ctx.fire(CRAWLER_RUN_THREAD_END, Thread.currentThread());
        }
    }

    // true to continue and false to abort/break
    private boolean processNextInQueue(
            CrawlerContext crawlCtx,
            CrawlActivityChecker activityChecker) {
        var docProcessCtx = new DocProcessorContext().crawlerContext(crawlCtx);
        //                .orphan(orphan);
        try {
            var docContext = crawlCtx
                    .getDocProcessingLedger()
                    .pollQueue()
                    .orElse(null);
            LOG.trace("Pulled next reference from Queue: {}", docContext);

            docProcessCtx.docContext(docContext);
            if (docProcessCtx.docContext() == null) {
                //TODO ensure this can't create infinite loop if for weird
                // reasons the crawler is always reported active.
                // or make sure it does not happen
                return activityChecker.isActive();
            }

            docProcessCtx.doc(createDocFromCache(
                    crawlCtx, docProcessCtx.docContext()));

            // Before document processing
            ofNullable(crawlCtx
                    .getCallbacks()
                    .getBeforeDocumentProcessing()).ifPresent(
                            bdp -> bdp.accept(crawlCtx, docProcessCtx.doc()));

            if (activityChecker.isDeleting()) {
                DocProcessorDelete.execute(docProcessCtx);
            } else {
                DocProcessorUpsert.execute(docProcessCtx);
            }

            // After document processing
            ofNullable(crawlCtx.getCallbacks()
                    .getAfterDocumentProcessing())
                            .ifPresent(adp -> adp.accept(
                                    crawlCtx,
                                    docProcessCtx.doc()));
            return true;

        } catch (Exception e) {
            if (handleExceptionAndCheckIfStopCrawler(crawlCtx, docProcessCtx,
                    e)) {
                crawlCtx.stopCrawlerCommand();
                return false;
            }
        } finally {
            DocProcessorFinalize.execute(docProcessCtx);
        }
        return true;
    }

    // if not incremental, won't even attempt to go to cache
    private CrawlDoc createDocFromCache(
            CrawlerContext crawlCtx, DocContext docRec) {

        //TODO instead or in addition to get doc from cache as a separate
        // instance, populate the main one with relevant bits from the cache.

        // put timer for whole thread or closer to just importer
        // or have importer offer its own timer, in addition.
        // var elapsedTime = Timer.timeWatch(() ->
        var cachedDocRec = crawlCtx.isIncrementing() ? crawlCtx
                .getDocProcessingLedger()
                .getCached(docRec.getReference())
                .orElse(null)
                : null;

        //TODO have doc properties and metadata be the same? or somewhat in sync?
        // or a separate hidden Map in addition to user-provided map, that gets
        // merged in the end?
        // Or make it a bean that auto create metadata using reflection
        // (or Lombok Fields)
        // (except for reference)?
        // Relevant to DocContext as well
        var doc = new CrawlDoc(
                docRec,
                cachedDocRec,
                crawlCtx.getStreamFactory().newInputStream(),
                false); //TODO handle orphan properly !!!!!!!!!!!!
        //                orphan);
        doc.getMetadata().set(
                CrawlDocMetadata.IS_DOC_NEW, cachedDocRec == null);
        return doc;
    }

    // true to stop crawler
    private boolean handleExceptionAndCheckIfStopCrawler(
            CrawlerContext crawlCtx,
            DocProcessorContext docProcessCtx, Exception e) {

        //TODO check nested exception for a match.

        var stopTheCrawler = true;
        // if an exception was thrown and there is no CrawlDocRecord we
        // stop the crawler since it means we can't no longer read for the
        // queue, and we can no longer fetch a next document, possibly leading
        // to an infinite loop if it keeps trying and failing.
        var rec = docProcessCtx.docContext();
        if (rec == null) {
            LOG.error("An unrecoverable error was detected. The crawler will "
                    + "stop.",
                    e);
            crawlCtx.getEventManager().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.CRAWLER_ERROR)
                            .source(crawlCtx)
                            .docContext(rec)
                            .exception(e)
                            .build());
            return stopTheCrawler;
        }

        rec.setState(DocResolutionStatus.ERROR);
        if (LOG.isDebugEnabled()) {
            LOG.info("Could not process document: {} ({})",
                    docProcessCtx.docContext().getReference(), e.getMessage(),
                    e);
        } else {
            LOG.info("Could not process document: {} ({})",
                    docProcessCtx.docContext().getReference(), e.getMessage());
        }
        crawlCtx.getEventManager().fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_ERROR)
                        .source(crawlCtx)
                        .docContext(rec)
                        .exception(e)
                        .build());
        DocProcessorFinalize.execute(docProcessCtx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses =
                docProcessCtx.crawlerContext().getConfiguration()
                        .getStopOnExceptions();
        if (CollectionUtils.isNotEmpty(exceptionClasses)) {
            for (Class<? extends Exception> c : exceptionClasses) {
                if (c.isAssignableFrom(e.getClass())) {
                    LOG.error("Encountered a crawler-stopping exception as "
                            + "per configuration.",
                            e);
                    return stopTheCrawler;
                }
            }
        }
        LOG.error("""
                Encountered the following crawler exception and attempting\s\
                to ignore it. To force the crawler to stop upon encountering\s\
                this exception, use the "stopOnExceptions" feature\s\
                of your crawler config.""", e);
        return !stopTheCrawler;
    }

}
