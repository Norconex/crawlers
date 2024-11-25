/* Copyright 2022-2024 Norconex Inc.
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

import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN;
import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_END;
import static java.util.Optional.ofNullable;

import java.util.concurrent.CountDownLatch;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.importer.doc.DocContext;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Process a single reference.
 */
@Slf4j
@Builder
public class DocProcessor implements Runnable {

    private final CountDownLatch latch;
    private final int threadIndex;
    private final CrawlerContext crawlerContext;
    private final boolean deleting;
    private final boolean orphan;

    @Override
    public void run() {
        LogUtil.setMdcCrawlerId(crawlerContext.getId());
        Thread.currentThread()
                .setName(crawlerContext.getId() + "#" + threadIndex);
        LOG.debug("Crawler thread #{} started.", threadIndex);
        try {
            var activityChecker =
                    new ActivityChecker(crawlerContext, deleting);
            crawlerContext.fire(CRAWLER_RUN_THREAD_BEGIN,
                    Thread.currentThread());
            while (!crawlerContext.getState().isStopRequested()) {
                if (!processNextReference(activityChecker)) {
                    // At this point all threads/nodes shall reach the same
                    // conclusion and break, effectively ending crawling.
                    break;
                }
            }
        } catch (Exception e) {
            LOG.error("Problem in thread execution.", e);
        } finally {
            latch.countDown();
            crawlerContext.fire(CRAWLER_RUN_THREAD_END, Thread.currentThread());
        }
    }

    // return true to continue and false to abort/break
    private boolean processNextReference(ActivityChecker activityChecker) {
        var ctx = new DocProcessorContext()
                .crawlerContext(crawlerContext)
                .orphan(orphan);
        try {
            var docContext = crawlerContext
                    .getDocProcessingLedger()
                    .pollQueue()
                    .orElse(null);
            LOG.trace("Pulled next reference from Queue: {}", docContext);

            ctx.docContext(docContext);
            if (ctx.docContext() == null) {
                return activityChecker.isActive();
            }

            ctx.doc(createDocWithDocRecordFromCache(ctx.docContext()));

            // Before document processing
            ofNullable(crawlerContext
                    .getCallbacks()
                    .getBeforeDocumentProcessing())
                            .ifPresent(bdp -> bdp.accept(
                                    crawlerContext, ctx.doc()));

            if (deleting) {
                DocProcessorDelete.execute(ctx);
            } else {
                DocProcessorUpsert.execute(ctx);
            }

            // After document processing
            ofNullable(crawlerContext.getCallbacks()
                    .getAfterDocumentProcessing())
                            .ifPresent(adp -> adp.accept(
                                    crawlerContext,
                                    ctx.doc()));
            return true;

        } catch (Exception e) {
            if (handleExceptionAndCheckIfStopCrawler(ctx, e)) {
                return false;
            }
        } finally {
            DocProcessorFinalize.execute(ctx);
        }
        return true;
    }

    //--- DocRecord & Doc init. methods ----------------------------------------

    private CrawlDoc createDocWithDocRecordFromCache(DocContext docRec) {
        // put timer for whole thread or closer to just importer
        // or have importer offer its own timer, in addition.
        // var elapsedTime = Timer.timeWatch(() ->
        var cachedDocRec = crawlerContext
                .getDocProcessingLedger()
                .getCached(docRec.getReference())
                .orElse(null);

        var doc = new CrawlDoc(
                docRec,
                cachedDocRec,
                crawlerContext.getStreamFactory().newInputStream(),
                orphan);
        doc.getMetadata().set(
                CrawlDocMetadata.IS_CRAWL_NEW, cachedDocRec == null);
        return doc;
    }

    // true to stop crawler
    private boolean handleExceptionAndCheckIfStopCrawler(
            DocProcessorContext ctx, Exception e) {
        var stopTheCrawler = true;
        // if an exception was thrown and there is no CrawlDocRecord we
        // stop the crawler since it means we can't no longer read for the
        // queue, and we can no longer fetch a next document, possibly leading
        // to an infinite loop if it keeps trying and failing.
        var rec = ctx.docContext();
        if (rec == null) {
            LOG.error("An unrecoverable error was detected. The crawler will "
                    + "stop.",
                    e);
            crawlerContext.getEventManager().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.CRAWLER_ERROR)
                            .source(crawlerContext)
                            .docContext(rec)
                            .exception(e)
                            .build());
            return stopTheCrawler;
        }

        rec.setState(DocResolutionStatus.ERROR);
        if (LOG.isDebugEnabled()) {
            LOG.info("Could not process document: {} ({})",
                    ctx.docContext().getReference(), e.getMessage(), e);
        } else {
            LOG.info("Could not process document: {} ({})",
                    ctx.docContext().getReference(), e.getMessage());
        }
        crawlerContext.getEventManager().fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_ERROR)
                        .source(crawlerContext)
                        .docContext(rec)
                        .exception(e)
                        .build());
        DocProcessorFinalize.execute(ctx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses =
                ctx.crawlerContext().getConfiguration().getStopOnExceptions();
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
