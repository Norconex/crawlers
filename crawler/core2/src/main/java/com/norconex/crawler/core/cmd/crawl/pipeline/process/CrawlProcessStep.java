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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static java.util.Optional.ofNullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.cluster.pipeline.BaseStep;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;
import com.norconex.crawler.core2.util.ConcurrentUtil;
import com.norconex.crawler.core2.util.LogUtil;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Main crawler task, getting references from the crawl queue, fetch them,
 * and process them until the queue is empty.
 */
@Slf4j
@EqualsAndHashCode
public class CrawlProcessStep extends BaseStep {

    /**
     * What to do with ALL current entries in the queue.
     */
    public enum ProcessQueueAction {
        CRAWL_ALL, DELETE_ALL
    }

    private final ProcessQueueAction queueAction;
    private BatchDispatcher batchDispatcher;

    public CrawlProcessStep(String id, ProcessQueueAction queueAction) {
        super(id);
        this.queueAction = queueAction;
    }

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();
        if (isStopRequested()) {
            return;
        }
        LOG.info("Processing crawler queue...");
        try {
            //TODO DELETE THIS:
            //            ofNullable(ctx.getCallbacks().getBeforeCrawlTask())
            //                    .ifPresent(cb -> cb.accept(session));

            var cfg = ctx.getCrawlConfig();

            var numThreads = cfg.getNumThreads();

            batchDispatcher = BatchDispatcher.builder()
                    .maxBatchSize(cfg.getMaxQueueBatchSize())
                    .lowWatermark(Math.max(1, cfg.getMaxQueueBatchSize() / 5))
                    .maxEmptyPolls(10)
                    .pollIntervalMillis(1000)
                    .session(session)
                    .build();

            var executor = Executors.newFixedThreadPool(
                    numThreads, ctx.getThreadFactoryCreator()
                            .create(session.getCrawlerId()));

            //TODO have coordinator launch the monitoring of dormant
            // borrowed refs and check if ref after each iteration.
            // OR (better?) check after each batch if there are dormant ones
            // only if we are coordinator.
            //            maxBatchSize: This is the upper limit for how many items a node/thread should process in one batch, preventing any node from hogging too many items.
            //          •  Dynamic batch size per node: By dividing the total queue count by the number of active nodes, you ensure a fair distribution of work and avoid overloading a single node.
            //        •  Final batch size: Use min(maxBatchSize, queueCount / nodeCount) to determine the actual batch size for each node at each fetch.

            var futures = IntStream.range(0, numThreads)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            processQueue(session, i);
                        } catch (Exception e) {
                            LOG.error("Problem running task {} {} of {}.",
                                    "crawl-" + session.getCrawlerId(),
                                    i, numThreads, e);
                            throw new CompletionException(e);
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])).join();
            ConcurrentUtil.cleanShutdown(executor);
        } finally {
            //TODO DELETE THIS:
            //            ofNullable(ctx.getCallbacks().getAfterCrawlTask())
            //                    .ifPresent(cb -> cb.accept(session));
        }
    }

    //TODO get a batch of URLs (up to X, configurable)
    //TODO have coordinator periodically cleanup staled references via
    // scheduler (or after each X docs?).

    // just invoked in its own thread
    void processQueue(CrawlSession session, int threadIndex) {
        LOG.debug("Crawler thread #{} starting...", threadIndex);
        Thread.currentThread()
                .setName(session.getCrawlerId() + "#" + threadIndex);
        LogUtil.setMdcCrawlerId(session.getCrawlerId());

        try {
            //TODO make this a member variable?
            var activityChecker = new CrawlActivityChecker(
                    session, queueAction == ProcessQueueAction.DELETE_ALL);
            // TODO shall we check for "stopped" and other states?
            // abort now if we've reach configured max documents or
            // other ending conditions
            // At this point all threads/nodes shall reach the same
            // conclusion and break, effectively ending crawling.
            while (!isStopRequested()
                    && activityChecker.canContinue()
                    && processNextInQueue(session, activityChecker))
                ;
        } catch (Exception e) {
            //TODO also check here for configured exceptions that should
            // end the crawl?
            LOG.error("Problem in thread execution.", e);
        }
    }

    // true to continue and false to abort/break
    private boolean processNextInQueue(
            CrawlSession session,
            CrawlActivityChecker activityChecker) {

        var crawlCtx = session.getCrawlContext();
        var docProcessCtx = new ProcessContext().crawlSession(session);
        try {
            var currentEntry = batchDispatcher.take();

            LOG.trace("Pulled next reference from Queue: {}", currentEntry);

            if (currentEntry == null) {
                //TODO ensure this can't create infinite loop if for weird
                // reasons the crawler is always reported active.
                // or make sure it does not happen
                return activityChecker.isActive();
            }

            var doc = new Doc(currentEntry.getReference()); //NOSONAR
            CrawlEntry previousEntry = null;
            if (session.isIncremental()) {
                previousEntry = crawlCtx
                        .getCrawlEntryLedger()
                        .getPreviousEntry(currentEntry.getReference())
                        .orElse(null);
            }

            doc.getMetadata().set(CrawlDocMetaConstants.IS_DOC_NEW,
                    previousEntry == null);

            var docContext = CrawlDocContext.builder()
                    .currentCrawlEntry(currentEntry)
                    .previousCrawlEntry(previousEntry)
                    .doc(doc)
                    .build();
            docProcessCtx.docContext(docContext);

            // Before document processing
            ofNullable(crawlCtx.getCallbacks().getBeforeDocumentProcessing())
                    .ifPresent(bdp -> bdp.accept(session, doc));

            if (activityChecker.isDeleting()) {
                ProcessDelete.execute(docProcessCtx);
            } else {
                ProcessUpsert.execute(docProcessCtx);
            }

            // After document processing
            ofNullable(crawlCtx.getCallbacks().getAfterDocumentProcessing())
                    .ifPresent(adp -> adp.accept(
                            session,
                            docProcessCtx.docContext().getDoc()));
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (handleExceptionAndCheckIfStopCrawler(
                    session, docProcessCtx, e)) {
                session.getCluster().stop();
                return false;
            }
        } finally {
            ProcessFinalize.execute(docProcessCtx);
        }
        return true;
    }

    // true to stop crawler
    private boolean handleExceptionAndCheckIfStopCrawler(
            CrawlSession session,
            ProcessContext docProcessCtx, Exception e) {

        var crawlCtx = session.getCrawlContext();

        //TODO check nested exception for a match.

        var stopTheCrawler = true;
        // if an exception was thrown and there is no CrawlDocRecord we
        // stop the crawler since it means we can't no longer read for the
        // queue, and we can no longer fetch a next document, possibly leading
        // to an infinite loop if it keeps trying and failing.
        var docContext = docProcessCtx.docContext();
        if (docContext == null) {
            LOG.error("An unrecoverable error was detected. The crawler will "
                    + "stop.", e);
            crawlCtx.getEventManager().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.CRAWLER_ERROR)
                            .source(session)
                            .exception(e)
                            .build());
            return stopTheCrawler;
        }

        docContext.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.ERROR);
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
                        .source(session)
                        .crawlEntry(docContext.getCurrentCrawlEntry())
                        .exception(e)
                        .build());
        ProcessFinalize.execute(docProcessCtx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses = crawlCtx.getCrawlConfig()
                .getStopOnExceptions();
        if (CollectionUtils.isNotEmpty(exceptionClasses)) {
            for (Class<? extends Exception> c : exceptionClasses) {
                if (c.isAssignableFrom(e.getClass())) {
                    LOG.error("Encountered a crawler-stopping exception as "
                            + "per configuration.", e);
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
