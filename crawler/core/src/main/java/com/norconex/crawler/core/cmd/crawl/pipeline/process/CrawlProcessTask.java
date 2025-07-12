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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.BaseGridTask.AllNodesTask;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.importer.doc.DocContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Main crawler task, getting references from the crawl queue, fetch them,
 * and process them until the queue is empty.
 */
@Slf4j
public class CrawlProcessTask extends AllNodesTask {

    private static final long serialVersionUID = 1L;

    /**
     * What to do with ALL current entries in the queue.
     */
    public enum ProcessQueueAction {
        CRAWL_ALL, DELETE_ALL
    }

    private final ProcessQueueAction queueAction;
    private boolean stopRequested;

    public CrawlProcessTask(String id, ProcessQueueAction queueAction) {
        super(id);
        this.queueAction = queueAction;
    }

    @Override
    public void process(Grid grid) {
        var ctx = CrawlContext.get(grid);
        if (stopRequested) {
            return;
        }
        LOG.info("Processing crawler queue...");
        try {
            ofNullable(ctx.getCallbacks().getBeforeCrawlTask())
                    .ifPresent(cb -> cb.accept(ctx));
            //TODO add timeout?

            var numThreads = ctx.getCrawlConfig().getNumThreads();

            // move tfc to context?
            new AtomicInteger();
            var executor = Executors.newFixedThreadPool(
                    numThreads, ctx.getThreadFactoryCreator().create(getId()));
            var futures = IntStream.range(0, numThreads)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            processQueue(ctx, i);
                        } catch (Exception e) {
                            LOG.error("Problem running task {} {} of {}.",
                                    "crawl-" + getId(),
                                    i, numThreads, e);
                            throw new CompletionException(e);
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])).join();
            ConcurrentUtil.cleanShutdown(executor);
        } finally {
            ofNullable(ctx.getCallbacks().getAfterCrawlTask())
                    .ifPresent(cb -> cb.accept(ctx));
        }
    }

    @Override
    public void stop(Grid grid) {
        stopRequested = true;
        grid.getCompute().executeTask(GridTaskBuilder
                .create("updateCrawlState")
                .singleNode()
                .processor(g -> CrawlContext
                        .get(g)
                        .getSessionProperties()
                        .updateCrawlState(CrawlState.PAUSED))
                .build());
    }

    // just invoked in its own thread
    void processQueue(CrawlContext ctx, int threadIndex) {
        LOG.debug("Crawler thread #{} starting...", threadIndex);
        Thread.currentThread().setName(ctx.getId() + "#" + threadIndex);
        LogUtil.setMdcCrawlerId(ctx.getId());

        try {
            var activityChecker = new CrawlActivityChecker(
                    ctx, queueAction == ProcessQueueAction.DELETE_ALL);
            // TODO shall we check for "stopped" and other states?
            // abort now if we've reach configured max documents or
            // other ending conditions
            // At this point all threads/nodes shall reach the same
            // conclusion and break, effectively ending crawling.
            while (!stopRequested
                    && !activityChecker.isMaxDocsApplicableAndReached()
                    && processNextInQueue(ctx, activityChecker))
                ;
        } catch (Exception e) {
            //TODO also check here for configured exceptions that should
            // end the crawl?
            LOG.error("Problem in thread execution.", e);
        }
    }

    // true to continue and false to abort/break
    private boolean processNextInQueue(
            CrawlContext crawlCtx,
            CrawlActivityChecker activityChecker) {

        var docProcessCtx = new ProcessContext().crawlContext(crawlCtx);
        try {
            var docContext = crawlCtx
                    .getDocLedger()
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
                ProcessDelete.execute(docProcessCtx);
            } else {
                ProcessUpsert.execute(docProcessCtx);
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
                crawlCtx.getGrid().stop();
                return false;
            }
        } finally {
            ProcessFinalize.execute(docProcessCtx);
        }
        return true;
    }

    // if not incremental, won't even attempt to go to cache
    private CrawlDoc createDocFromCache(
            CrawlContext crawlCtx, DocContext docCtx) {

        //TODO instead or in addition to get doc from cache as a separate
        // instance, populate the main one with relevant bits from the cache.

        // put timer for whole thread or closer to just importer
        // or have importer offer its own timer, in addition.
        // var elapsedTime = Timer.timeWatch(() ->
        var cachedDocCtx = crawlCtx.isIncrementalCrawl() ? crawlCtx
                .getDocLedger()
                .getCached(docCtx.getReference())
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
                docCtx,
                cachedDocCtx,
                crawlCtx.getStreamFactory().newInputStream(),
                false); //TODO handle orphan properly or make it an emum
        doc.getMetadata().set(
                CrawlDocMetaConstants.IS_DOC_NEW, cachedDocCtx == null);
        return doc;
    }

    // true to stop crawler
    private boolean handleExceptionAndCheckIfStopCrawler(
            CrawlContext crawlCtx,
            ProcessContext docProcessCtx, Exception e) {

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

        rec.setState(CrawlDocStatus.ERROR);
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
        ProcessFinalize.execute(docProcessCtx);

        // Rethrow exception if we want the crawler to stop
        var exceptionClasses = docProcessCtx.crawlContext().getCrawlConfig()
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
