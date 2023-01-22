/* Copyright 2022-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import static com.norconex.crawler.core.crawler.CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN;
import static com.norconex.crawler.core.crawler.CrawlerEvent.CRAWLER_RUN_THREAD_END;

import java.util.concurrent.CountDownLatch;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.response.ImporterResponse;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Process a single reference.
 */
@Slf4j
@Builder
class CrawlerThread implements Runnable {

    private final CountDownLatch latch;
    private final int threadIndex;
    private final Crawler crawler;
    private final boolean deleting;
    private final boolean orphan;


    @Data
    @Accessors(fluent = true)
    static class ReferenceContext {
        private Crawler crawler;
        private CrawlDocRecord docRecord;
        private CrawlDoc doc;
        private ImporterResponse importerResponse;
        private boolean orphan;
        private boolean finalized;
    }

    @Override
    public void run() {
        MdcUtil.setCrawlerId(crawler.getId());
        Thread.currentThread().setName(crawler.getId() + "#" + threadIndex);
        LOG.debug("Crawler thread #{} started.", threadIndex);
        try {
            crawler.fire(CRAWLER_RUN_THREAD_BEGIN, Thread.currentThread());
            while (!crawler.isStopped()) {
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
        var ctx = new ReferenceContext()
                .crawler(crawler)
                .orphan(orphan);
        try {
            if (isMaxDocsReached()) {
                crawler.stop();
                return false;
            }

            ctx.docRecord(pullNextDocRecordFromQueue());
            if (ctx.docRecord() == null) {
                return isCrawlerStillActive() || isQueueStillInitializing();
            }

            ctx.doc(createDocWithDocRecordFromCache(ctx.docRecord()));

            if (deleting) {
                ThreadActionDelete.execute(ctx);
            } else {
                ThreadActionUpsert.execute(ctx);
            }
        } catch (RuntimeException e) {
            if (handleExceptionAndCheckIfStopCrawler(ctx, e)) {
                crawler.stop();
                return false;
            }
        } finally {
            ThreadActionFinalize.execute(ctx);
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
        var maxDocs = crawler.getCrawlerConfig().getMaxDocuments();
        if (maxDocs > -1
                && crawler.getMonitor().getProcessedCount() >= maxDocs) {
            LOG.info("Maximum documents reached: {}", maxDocs);
            return true;
        }
        return false;
    }

    private CrawlDocRecord pullNextDocRecordFromQueue() {
        var rec = crawler.getDocRecordService().pollQueue().orElse(null);
        LOG.trace("Pulled next reference from Queue: {}", rec);
        return rec;
    }

    private boolean isCrawlerStillActive() {
        var noneActive = crawler.getDocRecordService().isActiveEmpty();
        var queueEmpty = crawler.getDocRecordService().isQueueEmpty();
        if (noneActive && queueEmpty) {
            LOG.trace("Queue is empty and no documents are currently"
                    + "being processed.");
            return false;
        }
        Sleeper.sleepMillis(1); // to avoid fast loops taking all CPU
        // If there are some activity left, it means the queue
        // can grow again, we stop processing this non-existing doc
        // and let parent wait an try again.
        return true;
    }

    private boolean isQueueStillInitializing() {
        if (crawler.isQueueInitialized()) {
            return false;
        }
        LOG.info("References are still being queued. "
                + "Waiting for new references...");
        Sleeper.sleepSeconds(5);
        return true;
    }

    private CrawlDoc createDocWithDocRecordFromCache(DocRecord docRec) {
        // put timer for whole thread or closer to just importer
        // or have importer offer its own timer, in addition.
        // var elapsedTime = Timer.timeWatch(() ->

//WAS:            processNextQueuedRecord(docRecord.get(), flags))
//                    .toString();
        var cachedDocRec = crawler.getDocRecordService()
                .getCached(docRec.getReference()).orElse(null);
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
            ReferenceContext ctx, RuntimeException e) {
        ctx.docRecord().setState(CrawlDocState.ERROR);
        crawler.getEventManager().fire(
                CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_ERROR)
                    .source(crawler)
                    .crawlDocRecord(ctx.docRecord())
                    .exception(e)
                    .build());
        if (LOG.isDebugEnabled()) {
            LOG.info("Could not process document: {} ({})",
                    ctx.docRecord().getReference(), e.getMessage(), e);
        } else {
            LOG.info("Could not process document: {} ({})",
                    ctx.docRecord().getReference(), e.getMessage());
        }
        ThreadActionFinalize.execute(ctx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses =
                ctx.crawler().getCrawlerConfig().getStopOnExceptions();
        if (CollectionUtils.isNotEmpty(exceptionClasses)) {
            for (Class<? extends Exception> c : exceptionClasses) {
                if (c.isAssignableFrom(e.getClass())) {
                    LOG.error("Encountered a crawler-stopping exception as "
                            + "per configuration.", e);
                    return true;
                }
            }
        }
        return false;
    }
}
