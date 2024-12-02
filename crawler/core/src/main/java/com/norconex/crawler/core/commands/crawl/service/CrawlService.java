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
package com.norconex.crawler.core.commands.crawl.service;

import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.commands.crawl.CrawlStage;
import com.norconex.crawler.core.commands.crawl.task.CrawlTask;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridTxOptions;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridKeys;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for crawling documents as per configuration.
 * On a grid, it is meant to run on a single instance for the entire crawl
 * session. This service will itself distribute crawling tasks to other nodes.
 */
@Slf4j
public class CrawlService implements GridService {

    private static final Map<CrawlStage,
            BiConsumer<CrawlService, CrawlerContext>> stageRunners =
                    new ListOrderedMap<>();
    static {
        stageRunners.put(CrawlStage.INITIALIZE, CrawlService::prepare);
        stageRunners.put(CrawlStage.CRAWL, CrawlService::crawl);
        stageRunners.put(CrawlStage.HANDLE_ORPHANS,
                CrawlService::handleOrphans);
    }

    private CrawlerProgressLogger progressLogger;
    private CrawlStage firstStage;

    @Override
    public void init(CrawlerContext ctx, String arg) {
        progressLogger = new CrawlerProgressLogger(
                ctx.getMetrics(),
                ctx.getConfiguration().getMinProgressLoggingInterval());
        progressLogger.startTracking();

        firstStage = ctx.getCrawlStage(); // defaults to IDLE
        // Do some cleaning if we are starting a new crawl session
        // (as opposed to resuming)
        // Another state does not always mean we are resuming as the grid
        // could be recreating this service due to a crash and invoke
        // this init method again.
        if (firstStage.isAnyOf(CrawlStage.IDLE, CrawlStage.ENDED)) {
            ctx.getGrid().storage().getCache(
                    IgniteGridKeys.RUN_ONCE_CACHE, String.class).clear();
            setStage(ctx, CrawlStage.IDLE);
        } else {
            LOG.info("Service (re)starting at crawl stage: {}", firstStage);
        }
    }

    @Override
    public void execute(CrawlerContext ctx) {
        // in case of crashes/resume, start where we left off
        var caughtUp = firstStage == CrawlStage.IDLE;
        for (var entry : stageRunners.entrySet()) {
            if (!caughtUp && firstStage != entry.getKey()) {
                continue;
            }
            caughtUp = true;
            if (!ctx.isStopping()) {
                setStage(ctx, entry.getKey());
                entry.getValue().accept(this, ctx);
            }
        }

        progressLogger.stopTracking();
        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());
        // we don't end in a try/finally because abnormal termination
        // can be resumed where it stopped and marking as ended would
        // prevent that.
        setStage(ctx, CrawlStage.ENDED);
        LOG.info("Crawler ended");
    }

    private void prepare(CrawlerContext ctx) {
        DocLedgerPrepareExecutor.execute(ctx);
        QueueInitExecutor.execute(ctx);
    }

    private void crawl(CrawlerContext ctx) {
        processQueue(ctx, null);
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

    @Override
    public void stop(CrawlerContext ctx) {
        if (ctx.getCrawlStage() != CrawlStage.ENDED) {
            ctx.stop();
        }
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

    private void setStage(CrawlerContext ctx, CrawlStage stage) {
        ctx.getGrid().storage().getGlobalCache().put(
                CrawlStage.class.getSimpleName(), stage.name());
    }
}
