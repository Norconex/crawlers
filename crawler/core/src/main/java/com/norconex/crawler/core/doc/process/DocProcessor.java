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
package com.norconex.crawler.core.doc.process;

import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN;
import static com.norconex.crawler.core.event.CrawlerEvent.CRAWLER_RUN_THREAD_END;
import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.importer.doc.DocContext;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Process a single reference.
 */
@Slf4j
@Builder
class DocProcessor implements Runnable {

    private final CountDownLatch latch;
    private final int threadIndex;
    private final CrawlerTaskContext crawler;
    private final boolean deleting;
    private final boolean orphan;
    private final DocsProcessor crawlRunner;

    private final TimeoutWatcher activeTimeoutWatcher = new TimeoutWatcher();
    //    private final TimeoutWatcher queueInitTimeoutWatcher = new TimeoutWatcher();

    @Override
    public void run() {
        LogUtil.setMdcCrawlerId(crawler.getId());
        Thread.currentThread().setName(crawler.getId() + "#" + threadIndex);
        LOG.debug("Crawler thread #{} started.", threadIndex);
        try {
            crawler.fire(CRAWLER_RUN_THREAD_BEGIN, Thread.currentThread());
            while (!crawler.getState().isStopped()
                    && !crawler.getState().isStopRequested()) {
                if (!processNextReference()) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.error("Problem in thread execution.", e);
        } finally {
            latch.countDown();
            crawler.fire(CRAWLER_RUN_THREAD_END, Thread.currentThread());
        }
    }

    // return true to continue and false to abort/break
    private boolean processNextReference() {
        var ctx = new DocProcessorContext()
                .crawler(crawler)
                .orphan(orphan);
        try {
            if (isMaxDocsReached()) {
                crawler.stop();
                return false;
            }

            var docBrief = crawler
                    .getDocProcessingLedger()
                    .pollQueue()
                    .orElse(null);
            LOG.trace("Pulled next reference from Queue: {}", docBrief);
            ctx.docContext(docBrief);
            if (ctx.docContext() == null) {
                return isCrawlerStillActive() || isQueueStillInitializing();
            }
            activeTimeoutWatcher.reset();

            ctx.doc(createDocWithDocRecordFromCache(ctx.docContext()));

            // Before document processing
            ofNullable(crawler.getCallbacks().getBeforeDocumentProcessing())
                    .ifPresent(bdp -> bdp.accept(crawler, ctx.doc()));

            if (deleting) {
                DocProcessorDelete.execute(ctx);
            } else {
                DocProcessorUpsert.execute(ctx);
            }

            // After document processing
            ofNullable(crawler.getCallbacks().getAfterDocumentProcessing())
                    .ifPresent(adp -> adp.accept(crawler, ctx.doc()));
        } catch (RuntimeException e) {
            if (handleExceptionAndCheckIfStopCrawler(ctx, e)) {
                crawler.stop();
                return false;
            }
        } finally {
            DocProcessorFinalize.execute(ctx);
        }
        return true;
    }

    //--- DocRecord & Doc init. methods ----------------------------------------

    private boolean isMaxDocsReached() {
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        // or the event listeners that does it with more flexibility
        // is enough?

        // If deleting we don't care about checking if max is reached,
        // we proceed.
        if (deleting) {
            return false;
        }
        return crawlRunner.isMaxDocsReached();
    }

    private boolean isCrawlerStillActive() {
        //        var activeEmpty =
        //                crawler.getDocProcessingLedger().isActiveEmpty();
        var queueEmpty =
                crawler.getDocProcessingLedger().isQueueEmpty();
        if (/*activeEmpty && */ queueEmpty) {
            LOG.trace("Queue is empty and no documents are currently"
                    + "being processed.");
            return false;
        }
        Sleeper.sleepMillis(1); // to avoid fast loops taking all CPU
        // If there are some activity left, it means the queue
        // can grow again, we stop processing this non-existing doc
        // and let parent wait an try again, for as long as the activity timeout
        // is not reached.
        if (activeTimeoutWatcher.isTimedOut(
                crawler.getConfiguration().getIdleTimeout())) {
            //            Documents still being processed by\s\
            //            other crawler threads: {}.
            LOG.warn("""
                    Crawler thread has been idle for more than {} and will\s\
                    be shut down. \s\
                    Crawler queue empty: {}.""",
                    DurationFormatter.FULL.format(
                            crawler.getConfiguration().getIdleTimeout()),
                    /*!activeEmpty,*/ queueEmpty);
            return false;
        }
        return true;
    }

    private boolean isQueueStillInitializing() {
        if (crawler.getDocPipelines().getQueuePipeline().isQueueInitialized()) {
            return false;
        }
        LOG.info("References are still being queued. "
                + "Waiting for new references...");
        Sleeper.sleepSeconds(5);
        return true;
    }

    private CrawlDoc createDocWithDocRecordFromCache(DocContext docRec) {
        // put timer for whole thread or closer to just importer
        // or have importer offer its own timer, in addition.
        // var elapsedTime = Timer.timeWatch(() ->

        //WAS:            processNextQueuedRecord(docRecord.get(), flags))
        //                    .toString();
        var cachedDocRec = crawler
                .getDocProcessingLedger()
                .getCached(docRec.getReference())
                .orElse(null);

        var doc = new CrawlDoc(
                docRec,
                cachedDocRec,
                crawler.getStreamFactory().newInputStream(),
                orphan);
        doc.getMetadata().set(
                CrawlDocMetadata.IS_CRAWL_NEW, cachedDocRec == null);
        return doc;
        //    LOG.debug("{} to process: {}", elapsedTime,
        //            docRecord.get().getReference());
        //            return null;
    }

    // true to stop crawler
    private boolean handleExceptionAndCheckIfStopCrawler(
            DocProcessorContext ctx, RuntimeException e) {
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
            crawler.getEventManager().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.CRAWLER_ERROR)
                            .source(crawler)
                            .docContext(rec)
                            .exception(e)
                            .build());
            return stopTheCrawler;
        }

        rec.setState(CrawlDocState.ERROR);
        if (LOG.isDebugEnabled()) {
            LOG.info("Could not process document: {} ({})",
                    ctx.docContext().getReference(), e.getMessage(), e);
        } else {
            LOG.info("Could not process document: {} ({})",
                    ctx.docContext().getReference(), e.getMessage());
        }
        crawler.getEventManager().fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_ERROR)
                        .source(crawler)
                        .docContext(rec)
                        .exception(e)
                        .build());
        DocProcessorFinalize.execute(ctx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses =
                ctx.crawler().getConfiguration().getStopOnExceptions();
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

    // thread safe
    //TODO maybe move to commons lang?
    private static class TimeoutWatcher {
        private final StopWatch watch = new StopWatch();

        private void reset() {
            watch.reset();
        }

        void track() {
            if (!watch.isStarted()) {
                watch.start();
            }
        }

        boolean isTimedOut(Duration duration) {
            if (duration == null) {
                return false;
            }
            track();
            return watch.getTime() > duration.toMillis();
        }
    }
}
