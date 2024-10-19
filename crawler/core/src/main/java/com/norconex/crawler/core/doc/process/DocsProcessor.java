/* Copyright 2024 Norconex Inc.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.CrawlerState;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.core.util.LogUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DocsProcessor implements Runnable {

    @Getter
    private final CrawlerTaskContext crawler;
    private final DocProcessingLedger ledger;
    private final CrawlerState state;

    private int resumableMaxDocs;

    public DocsProcessor(CrawlerTaskContext crawler) {
        this.crawler = crawler;
        ledger = crawler.getDocProcessingLedger();
        state = crawler.getState();
    }

    @Override
    public void run() {
        try {
            init();
            ofNullable(crawler.getCallbacks().getBeforeCrawlerExecution())
                    .ifPresent(cb -> cb.accept(crawler));
            execute();
        } finally {
            try {
                ofNullable(crawler.getCallbacks().getAfterCrawlerExecution())
                        .ifPresent(cb -> cb.accept(crawler));
            } finally {
                destroy();
            }
        }
    }

    private void init() {
        // max documents
        var cfgMaxDocs = crawler.getConfiguration().getMaxDocuments();
        resumableMaxDocs = cfgMaxDocs;
        if (cfgMaxDocs > -1 && state.isResuming()) {
            resumableMaxDocs += ledger.getProcessedCount();
            LOG.info("""
                    Adding configured maximum documents ({})\s\
                    to this resumed session. The combined maximum\s\
                    documents for this run and previous stopped one(s) is: {}
                    """,
                    cfgMaxDocs, resumableMaxDocs);
        }
    }

    private void execute() {
        try {
            //TODO wrapping in thread stuff (BEGIN/END, etc.) is similar to
            // DocProcessor#run(), consider making it a shared method.
            LogUtil.setMdcCrawlerId(crawler.getId());
            Thread.currentThread().setName(crawler.getId() + "#queue-init");
            LOG.debug("Crawler thread 'init-queue' started.");
            crawler.fire(CRAWLER_RUN_THREAD_BEGIN, Thread.currentThread());
            crawler.getDocPipelines()
                    .getQueuePipeline()
                    .initializeQueue(crawler);

        } finally {
            crawler.fire(CRAWLER_RUN_THREAD_END, Thread.currentThread());
            Thread.currentThread().setName(crawler.getId());
        }

        if (state.isStopRequested() || state.isStopped()) {
            return;
        }
        LOG.info("Crawling references...");
        crawlReferences(new ProcessFlags());

        if (!state.isStopRequested()) {
            new OrphanDocsProcessor(this).handleOrphans();
        }
    }

    private void destroy() {
        LOG.info("Crawler {}", (state.isStopped() ? "stopped." : "completed."));
        //        try {
        //            progressLogger.stopTracking();
        //            LOG.info(
        //                    "Execution Summary:{}",
        //                    progressLogger.getExecutionSummary());
        //        } finally {
        if (state.isStopRequested()) {
            state.setStopped(true);
            //                state.setStopRequested(false);
            crawler.fire(CrawlerEvent.CRAWLER_STOP_END);
        }
        //        }
    }

    void crawlReferences(final ProcessFlags flags) {
        var numThreads = crawler.getConfiguration().getNumThreads();
        final var latch = new CountDownLatch(numThreads);
        var execService = Executors.newFixedThreadPool(numThreads);
        try {
            for (var i = 0; i < numThreads; i++) {
                final var threadIndex = i + 1;
                LOG.debug("Crawler thread #{} starting...", threadIndex);
                execService.execute(
                        DocProcessor.builder()
                                .crawler(crawler)
                                .latch(latch)
                                .threadIndex(threadIndex)
                                .deleting(flags.delete)
                                .orphan(flags.orphan)
                                .crawlRunner(this)
                                .build());
            }
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlerException(e);
        } finally {
            execService.shutdown();
            // necessary to ensure thread end event is not sometimes fired
            // after crawler run end.
            try {
                execService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Failed to wait for crawler termination.", e);
            }
        }
    }

    boolean isMaxDocsReached() {
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        // Check if we merge with StopCrawlerOnMaxEventListener
        // or if we remove maxDocument in favor of the listener.
        // what about clustering?
        var isMax = resumableMaxDocs > -1
                && ledger.getProcessedCount() >= resumableMaxDocs;
        if (isMax) {
            LOG.info("Maximum documents reached for this crawling session: {}",
                    resumableMaxDocs);
        }
        return isMax;
    }

    //    private void logJmxState() {
    //        if (Boolean.getBoolean(CrawlerTaskContext.SYS_PROP_ENABLE_JMX)) {
    //            LOG.info("JMX support enabled.");
    //        } else {
    //            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
    //                    + "system property as a JVM argument.");
    //        }
    //    }
    //
    @Getter
    public static final class ProcessFlags {
        private boolean delete;
        private boolean orphan;

        ProcessFlags delete() {
            delete = true;
            return this;
        }

        ProcessFlags orphan() {
            orphan = true;
            return this;
        }
    }
}
