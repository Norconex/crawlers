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
package com.norconex.crawler.core.services.crawl;

import static java.util.Optional.ofNullable;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerProgressLogger;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridTxOptions;
import com.norconex.crawler.core.tasks.crawl.CrawlTask;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.LogUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for crawling documents as per configuration.
 * On a grid, it is meant to run on a single instance for the entire crawl
 * session. This service will itself distribute crawling tasks to other nodes.
 */
@Slf4j
public class CrawlService implements GridService {

    //    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private CrawlerProgressLogger progressLogger;

    //TODO have a general life-cycle facade or procesing-state facade
    // where all the global keys can be found.  Or is the
    // DocProcessingLedger already for that?
    //    public static final String KEY_START_REFS_QUEUED =
    //            "crawl.start.refs.queued";

    @Override
    public void init(CrawlerContext crawlerContext, String arg) {
        LogUtil.logCommandIntro(LOG, crawlerContext.getConfiguration());
        progressLogger = new CrawlerProgressLogger(
                crawlerContext.getMetrics(),
                crawlerContext.getConfiguration()
                        .getMinProgressLoggingInterval());
        progressLogger.startTracking();
    }

    @Override
    public void start(CrawlerContext crawlerContext) {

        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN)
                .source(this)
                .message("Crawl service started.")
                .build());

        DocLedgerPrepareExecutor.execute(crawlerContext);
        QueueInitExecutor.execute(crawlerContext);
        processQueue(crawlerContext, null);
        if (!crawlerContext.getState().isStopRequested()) {
            handleOrphans(crawlerContext);
        }

        //        System.err.println("XXX I THINK I AM DONE, shutting down.");

        // process orphans
        // finalize
        //                crawlerContext.fire(CrawlerEvent
        //                        .builder()
        //                        .name(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN)
        //                        .source(this)
        //                        .message("Starting to shut down crawler.")
        //                        .build());
        //
        //        //        crawlerContext.getGrid().shutdown();
        //        //        System.err.println("XXX SHUT DOWN called.");

        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_END)
                .source(this)
                .message("Done crawling.")
                .build());

    }

    @Override
    public void end(CrawlerContext crawlerContext) {
        //        crawlerContext.fire(CrawlerEvent
        //                .builder()
        //                .name(CrawlerEvent.CRAWLER_SHUTDOWN_END)
        //                .source(this)
        //                .message("Done shutting down the crawler.")
        //                .build());
        //        System.err.println("XXX Ending crawl service...");
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        //
        progressLogger.stopTracking();
        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());
        LOG.info("Crawler {}",
                (crawlerContext.getState().isStopped() ? "stopped."
                        : "completed."));
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
        //        crawlerContext.close();
        //System.err.println("XXX About to close crawler context.");
        //crawlerContext.close();
        //System.err.println("XXX Closed crawler context");
    }

    // On all nodes, block
    private void processQueue(CrawlerContext crawlerContext, String arg) {
        LOG.info("Processing queue...");
        ConcurrentUtil.block(crawlerContext
                .getGrid()
                .compute()
                .runOnAll(
                        CrawlTask.class,
                        arg,
                        GridTxOptions
                                .builder()
                                .name("crawl-" + ofNullable(arg).orElse("main"))
                                .build()));
    }

    // Queue orphans for reprocess/delete here.
    private void handleOrphans(CrawlerContext crawlerContext) {
        var strategy = crawlerContext.getConfiguration().getOrphansStrategy();
        // If PROCESS, we do not care to validate if really orphan since
        // all cache items will be reprocessed regardless
        if (strategy == OrphansStrategy.PROCESS) {
            reprocessCacheOrphans(crawlerContext);
        } else if (strategy == OrphansStrategy.DELETE) {
            deleteCacheOrphans(crawlerContext);
        }
        // Else, do nothing
    }

    void reprocessCacheOrphans(CrawlerContext ctx) {
        if (ctx.getDocProcessingLedger().isMaxDocsProcessedReached()) {
            LOG.info("""
                Max documents reached. \
                Not reprocessing orphans (if any). \
                Run the crawler again to resume.""");
            return;
        }
        LOG.info("Queueing orphan references for processing...");
        var count = new MutableLong();
        ctx.getDocProcessingLedger().forEachCached((ref, docInfo) -> {
            ctx.getDocPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(ctx, docInfo));
            count.increment();
            return true;
        });
        if (count.longValue() > 0) {
            LOG.info("Reprocessing {} orphan references...", count);
            processQueue(ctx, CrawlTask.ARG_FLAG_ORPHAN);
        }
        LOG.info("Reprocessed {} cached/orphan references.", count);
    }

    void deleteCacheOrphans(CrawlerContext ctx) {
        LOG.info("Queueing orphan references for deletion...");

        var count = new MutableLong();

        ctx.getDocProcessingLedger().forEachCached((k, v) -> {
            ctx.getDocProcessingLedger().queue(v);
            count.increment();
            return true;
        });
        if (count.longValue() > 0) {
            LOG.info("Deleting {} orphan references...", count);
            processQueue(ctx, CrawlTask.ARG_FLAG_DELETE);
        }
        LOG.info("Deleted {} orphan references.", count);
    }
}
