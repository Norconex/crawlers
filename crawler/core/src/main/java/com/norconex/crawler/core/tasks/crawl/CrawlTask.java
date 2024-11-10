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
package com.norconex.crawler.core.tasks.crawl;

import static java.util.Optional.ofNullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.tasks.crawl.process.DocProcessor;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Performs a crawl by getting references from the crawl queue until there
 * are no more in the queue or being processed.
 */
@Slf4j
public class CrawlTask implements GridTask<Void> {

    private static final long serialVersionUID = 1L;
    public static final String ARG_FLAG_DELETE = "delete";
    public static final String ARG_FLAG_ORPHAN = "orphan";

    @Override
    public Void run(CrawlerContext crawlerContext, String arg) {
        //TODO maybe merge DocsProcessor here instead?
        System.err.println("XXX About to run DocsProcessor...");
        var state = crawlerContext.getState();
        if (state.isStopRequested() || state.isStopped()) {
            return null;
        }

        var flags = new ProcessFlags();
        if (ARG_FLAG_DELETE.equalsIgnoreCase(arg)) {
            flags.delete(true);
        } else if (ARG_FLAG_ORPHAN.equalsIgnoreCase(arg)) {
            flags.orphan(true);
        } else if (arg != null) {
            throw new IllegalArgumentException(
                    "Unsupported CrawlTask argument: " + arg);
        }

        try {
            ofNullable(crawlerContext.getCallbacks().getBeforeCrawlTask())
                    .ifPresent(cb -> cb.accept(crawlerContext));
            LOG.info("Crawling references...");
            crawlReferences(crawlerContext, flags);
        } finally {
            try {
                ofNullable(crawlerContext.getCallbacks().getAfterCrawlTask())
                        .ifPresent(cb -> cb.accept(crawlerContext));
            } finally {
                //TODO do this here??
                LOG.info("Crawler {}",
                        (crawlerContext.getState().isStopped() ? "stopped."
                                : "completed."));
                if (state.isStopRequested()) {
                    state.setStopped(true);
                    crawlerContext.fire(CrawlerEvent.CRAWLER_STOP_END);
                }
            }
        }

        //TODO do this here??
        LOG.info("Crawler {}",
                (crawlerContext.getState().isStopped() ? "stopped."
                        : "completed."));
        if (crawlerContext.getState().isStopRequested()) {
            crawlerContext.getState().setStopped(true);
            crawlerContext.fire(CrawlerEvent.CRAWLER_STOP_END);
        }
        return null;
    }

    void crawlReferences(CrawlerContext ctx, final ProcessFlags flags) {
        var numThreads = ctx.getConfiguration().getNumThreads();
        final var latch = new CountDownLatch(numThreads);
        var execService = Executors.newFixedThreadPool(numThreads);
        try {
            for (var i = 0; i < numThreads; i++) {
                final var threadIndex = i + 1;
                LOG.debug("Crawler thread #{} starting...", threadIndex);
                execService.execute(
                        DocProcessor.builder()
                                .crawlerContext(ctx)
                                .latch(latch)
                                .threadIndex(threadIndex)
                                .deleting(flags.delete())
                                .orphan(flags.orphan())
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

    //    @Override
    //    public void run(CrawlerContext crawlerContext, String arg) {
    //        //TODO maybe merge DocsProcessor here instead?
    //        System.err.println("XXX About to run DocsProcessor...");
    //        new DocsProcessor(crawlerContext).run();
    //    }

    @Data
    @Accessors(fluent = true)
    public static final class ProcessFlags {
        private boolean delete;
        private boolean orphan;

    }
}
