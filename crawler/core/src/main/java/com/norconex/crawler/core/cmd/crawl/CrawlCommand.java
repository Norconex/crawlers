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
package com.norconex.crawler.core.cmd.crawl;

import java.util.List;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.clean.CleanCommand;
import com.norconex.crawler.core.cmd.crawl.init.CrawlInitTask;
import com.norconex.crawler.core.cmd.crawl.orphans.CrawlHandleOrphansTask;
import com.norconex.crawler.core.cmd.crawl.queueread.CrawlProcessQueueTask;
import com.norconex.crawler.core.cmd.crawl.queueread.CrawlProcessQueueTask.ProcessQueueAction;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.grid.core.compute.GridCompute.RunOn;
import com.norconex.grid.core.pipeline.GridPipelineStage;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    private static final String PROGRESS_LOGGER_KEY = "progressLogger";

    @Override
    public void execute(CrawlerContext ctx) {

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN);

        trackProgress(ctx);

        var completed = Boolean.TRUE.equals(ConcurrentUtil.get(ctx.getGrid()
                .pipeline().run(CrawlerContext.KEY_CRAWL_PIPELINE, List.of(
                        // Init
                        GridPipelineStage.<CrawlerContext>builder()
                                .name("crawlInitStage")
                                .runOn(RunOn.ONE_ONCE)
                                .task(new CrawlInitTask())
                                .build(),
                        // Main crawl
                        GridPipelineStage.<CrawlerContext>builder()
                                .name("crawlCoreStage")
                                .runOn(RunOn.ALL)
                                .task(new CrawlProcessQueueTask(
                                        ProcessQueueAction.CRAWL_ALL))
                                .build(),
                        // Handle orphans
                        GridPipelineStage.<CrawlerContext>builder()
                                .name("crawlOrphansStage")
                                .runOn(RunOn.ALL)
                                .task(new CrawlHandleOrphansTask())
                                .build()

                //                        // Requeue (or ignore) orphans
                //                        GridPipelineStage.<CrawlerContext>builder()
                //                                .name("crawlOrphansStage")
                //                                .runOn(RunOn.ALL)
                //                                .task(new CrawlHandleOrphansTask())
                //                                .build(),
                //                        // Recrawl orphans (if not ignored)
                //                        GridPipelineStage.<CrawlerContext>builder()
                //                                .name("crawlOrphansStage")
                //                                .runOn(RunOn.ALL)
                //                                .task(new CrawlHandleOrphansTask())
                //                                .build()
                //                // Shutdown
                //                GridPipelineStage.<CrawlerContext>builder()
                //                        .name("crawlShutdownStage")
                //                        .runOn(RunOn.ALL)
                //                        .task(new CrawlShutdownTask())
                //                        .build(),
                ),
                        ctx)));

        //        while (ctx.getCrawlStage() != CrawlStage.ENDED) {
        //            Sleeper.sleepSeconds(1);
        //        }

        if (completed) {
            LOG.info("Crawler completed execution.");
        } else {
            LOG.info("Crawler execution ended before completion.");
        }

        ctx.getGrid().compute().requestStop(PROGRESS_LOGGER_KEY);

        //        progressLogger.stopTracking();
        //        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());

        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_END);
        LOG.info("Node done crawling.");
    }

    private void cleanFirst(CrawlerContext ctx) {
        new CleanCommand().execute(ctx);
        // re-initialize the context to recreate destroyed cache
        // for crawling
        ctx.close();
        ctx.init();
    }

    private void trackProgress(CrawlerContext ctx) {
        var progressLogger = new CrawlProgressLogger(
                ctx.getMetrics(),
                ctx.getConfiguration().getMinProgressLoggingInterval());
        // TODO: make sure this (or all) runOnOneOnce can be recovered
        // upon node failure
        // only 1 node reports progress
        ctx.getGrid()
                .compute()
                .runOnOneOnce(PROGRESS_LOGGER_KEY, progressLogger);
    }

    //    private CrawlProgressLogger trackProgress(CrawlerContext ctx) {
    //        var progressLogger = new CrawlProgressLogger(
    //                ctx.getMetrics(),
    //                ctx.getConfiguration().getMinProgressLoggingInterval());
    //        // TODO: make sure this (or all) runOnOneOnce can be recovered
    //        // upon node failure
    //        return ConcurrentUtil.get(ctx.getGrid().compute()
    //                // only 1 node reports progress
    //                .runOnOneOnce("progressLogger", () -> {
    //                    progressLogger.startTracking();
    //                    return progressLogger;
    //                }));
    //    }
}
