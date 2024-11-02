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

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerProgressLogger;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridTxOptions;
import com.norconex.crawler.core.tasks.crawl.CrawlTask;
import com.norconex.crawler.core.util.LogUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for crawling documents as per configuration.
 * On a grid, it is meant to run on a single instance for the entire crawl
 * session. This service will itself distribute crawling tasks to other nodes.
 */
@Slf4j
public class CrawlService implements GridService {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private CrawlerProgressLogger progressLogger;

    //TODO have a general life-cycle facade or procesing-state facade
    // where all the global keys can be found.  Or is the
    // DocProcessingLedger already for that?
    //    public static final String KEY_START_REFS_QUEUED =
    //            "crawl.start.refs.queued";

    @Override
    public void init(CrawlerContext crawlerContext) {
        LogUtil.logCommandIntro(LOG, crawlerContext.getConfiguration());
        progressLogger = new CrawlerProgressLogger(
                crawlerContext.getMetrics(),
                crawlerContext.getConfiguration()
                        .getMinProgressLoggingInterval());
        progressLogger.startTracking();
    }

    @Override
    public void start(CrawlerContext crawlerContext) {

        System.err.println("XXX I AM AT YOUR SERVICE!");

        DocLedgerInitExecutor.execute(crawlerContext);

        System.err.println("XXX GOT THE LEDGER.");

        QueueInitExecutor.execute(crawlerContext);

        System.err.println("XXX INITIALIZED THE QUEUE.");

        crawlDocs(crawlerContext);

        System.err.println("XXX I THINK I AM DONE.");

        Sleeper.sleepMinutes(1);
        // process orphans
        // finalize
    }

    @Override
    public void end(CrawlerContext crawlerContext) {
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        //
        //        progressLogger.stopTracking();
        //        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());
        //        LOG.info("Crawler {}",
        //                (crawlerContext.getState().isStopped() ? "stopped."
        //                        : "completed."));
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
        //        crawlerContext.close();
    }

    //    // On 1 node, block unless config says async
    //    private void queueStartReferences(CrawlerContext crawlerContext) {
    //        var cfg = crawlerContext.getConfiguration();
    //        var grid = crawlerContext.getGrid();
    //        if (crawlerContext.getState().isResuming()) {
    //            LOG.info("Unfinished previous crawl detected. Resuming...");
    //        } else {
    //            LOG.info("Queuing start references ({})...",
    //                    cfg.isStartReferencesAsync() ? "async" : "");
    //            grid.compute().runTask(
    //                    QueueInitExecutor.class,
    //                    null,
    //                    GridTxOptions.builder()
    //                            .name("queue-start-refs")
    //                            .block(!cfg.isStartReferencesAsync())
    //                            .singleton(true)
    //                            .build());
    //        }
    //    }

    // On all nodes, block
    private void crawlDocs(CrawlerContext crawlerContext) {
        LOG.info("Crawling...");
        crawlerContext.getGrid().compute().runTask(CrawlTask.class, null,
                GridTxOptions.builder()
                        .name("crawl")
                        .block(true)
                        .build());
    }

    //    private void finalizeExecution(Crawler crawlerContext) {
    //        // TODO Auto-generated method stub
    //
    //    }

    //    // On all nodes, block
    //    private void handleOrphanDocs(crawlerContext crawlerContext) {
    //        LOG.warn("TODO: Implement me");
    //    }
}
