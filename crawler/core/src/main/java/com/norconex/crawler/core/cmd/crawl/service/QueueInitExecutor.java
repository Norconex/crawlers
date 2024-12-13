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
package com.norconex.crawler.core.cmd.crawl.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.util.LogUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Prepare the doc processing ledger for a new or incremental crawl.
 * Takes care of caching processed documents on previously completed crawls
 * or simply logs the current progress if resuming from an incomplete
 * previous crawl.
 */
@Slf4j
final class QueueInitExecutor {

    private QueueInitExecutor() {
    }

    /**
     * Queues all initial references. If configured to be asynchronous, the
     * method returns right away.
     * Otherwise the method returns when all initial references are queued.
     * If no queue initializer was provided, returns right away with a
     * completed future.
     * @param crawlerContext the crawler context
     */
    public static void execute(CrawlerContext crawlerContext) {
        //TODO do something with future here?
        initializeQueue(crawlerContext);
    }

    public static Future<?> initializeQueue(CrawlerContext crawlerContext) {

        if (crawlerContext.isResuming()) {
            LOG.info("Unfinished previous crawl detected. Resuming...");
        } else {
            LOG.info("Queuing start references ({})...",
                    crawlerContext.getConfiguration().isStartReferencesAsync()
                            ? "asynchronously"
                            : "synchronously");
        }
        //
        //        // depending on whether we are running the queuing sync or async,
        //        // the value will be returned when it is done or right away.
        //        crawlerContext.getDocPipelines()
        //                .getQueuePipeline()
        //                .initializeQueue(crawlerContext);
        //    }
        //
        //    public Future<?> initializeQueue(CrawlerContext crawlerContext) {
        //--- Queue initial references ---------------------------------
        //TODO if we resume, shall we not queue again? What if it stopped
        // in the middle of initial queuing, then to be safe we have to
        // queue again and expect that those that were already processed
        // will simply be ignored (default behavior).
        // Consider persisting a flag that would tell us if we are resuming
        // with an incomplete queue initialization, or make initialization
        // more sophisticated so we can resume in the middle of it
        // (this last option would likely be very impractical).
        var initializer = crawlerContext.getSpec().queueInitializer();

        if (initializer != null) {
            LOG.info("Queueing initial references...");
            var queueInitContext = new QueueInitContext(
                    crawlerContext,
                    crawlerContext.isResuming(),
                    rec -> queue(
                            new QueuePipelineContext(crawlerContext, rec)));
            var cfg = crawlerContext.getConfiguration();
            if (cfg.isStartReferencesAsync()) {
                return initializeQueueAsync(initializer, queueInitContext);
            }
            return initializeQueueSync(initializer, queueInitContext);
        }
        crawlerContext.queueInitialized();
        return CompletableFuture.completedFuture(null);

    }

    public static void queue(QueuePipelineContext context) {
        context.getCrawlerContext()
                .getDocPipelines()
                .getQueuePipeline()
                .accept(context);
        //        var ctx = contextAdapter != null
        //                ? contextAdapter.apply(context)
        //                : context;
        //        stages.test(ctx);
    }

    private static Future<?> initializeQueueAsync(
            QueueInitializer initializer, QueueInitContext ctx) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> {
                LogUtil.setMdcCrawlerId(ctx.getCrawlerContext().getId());
                Thread.currentThread().setName(ctx.getCrawlerContext().getId());
                LOG.info("Queuing start references asynchronously.");
                initializer.accept(ctx);
                ctx.getCrawlerContext().queueInitialized();
            });
        } finally {
            try {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error("Reading of start references interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Future<?> initializeQueueSync(
            QueueInitializer initializer, QueueInitContext ctx) {
        LOG.info("Queuing start references synchronously.");
        initializer.accept(ctx);
        ctx.getCrawlerContext().queueInitialized();
        return CompletableFuture.completedFuture(null);
    }
}
