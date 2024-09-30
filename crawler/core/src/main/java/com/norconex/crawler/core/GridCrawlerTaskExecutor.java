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
package com.norconex.crawler.core;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Executes a task on the grid, to be run within the context of an already
 * initialized and running crawler on a node (i.e., within a command
 * execution life-cycle).
 * Equivalent to invoking:
 * </p>
 * <pre><code>
 * grid.getCompute().runTask(() -> {
 *   // initialize the crawler
 *   // your code
 *   // shutdown the crawler
 * })
 * </code></pre> where the
 * local crawler is initialized and shutdown properly
 */
@Slf4j
public final class GridCrawlerTaskExecutor {
    //
    //    private GridCrawlerTaskExecutor() {
    //    }
    //
    //    /**
    //     * Runs a task on a node local crawler.
    //     * @param grid the grid
    //     * @param crawlerTask task
    //     * @param opts grid transaction options
    //     */
    //    public static void runTask(
    //            GridSystem grid,
    //            Consumer<Crawler> crawlerTask,
    //            GridTxOptions opts) {
    //
    //        //TODO do not pass grid here ---------------v
    //        grid.getCompute().runTask(() -> doRunTask(grid, crawlerTask), opts);
    //    }
    //
    //    static void doRunTask(GridSystem grid, Consumer<Crawler> crawlerTask) {
    //        var localCrawler = grid.getLocalCrawler();
    //        initLocalCrawler(localCrawler);
    //        try {
    //            crawlerTask.accept(localCrawler);
    //        } finally {
    //            shutdownLocalCrawler(localCrawler);
    //        }
    //    }
    //
    //    // Implicitly invoked when using RunTask
    //    public static void initLocalCrawler(Crawler crawler) {
    //        //--- Ensure good state/config ---
    //        if (StringUtils.isBlank(crawler.getId())) {
    //            throw new CrawlerException(
    //                    "Crawler must be given a unique identifier (id).");
    //        }
    //
    //        LogUtil.setMdcCrawlerId(crawler.getId());
    //        Thread.currentThread().setName(crawler.getId());
    //
    //        crawler.getServices().getEventManager().addListenersFromScan(
    //                crawler.getConfiguration());
    //
    //        crawler.fire(CrawlerEvent.CRAWLER_INIT_BEGIN);
    //
    //        crawler.workDir =
    //                ofNullable(crawler.getConfiguration().getWorkDir())
    //                        .orElseGet(() -> CrawlerConfig.DEFAULT_WORKDIR)
    //                        .resolve(FileUtil.toSafeFileName(crawler.getId()));
    //        // Will also create workdir parent:
    //
    //        //TODO, when starting, maybe detect if
    //        crawler.tempDir = crawler.workDir.resolve("temp");
    //        try {
    //            // Will also create workdir parent:
    //            Files.createDirectories(crawler.tempDir);
    //        } catch (IOException e) {
    //            throw new CrawlerException(
    //                    "Could not create directory: "
    //                            + crawler.tempDir,
    //                    e);
    //        }
    //        crawler.streamFactory = new CachedStreamFactory(
    //                (int) crawler.getConfiguration()
    //                        .getMaxStreamCachePoolSize(),
    //                (int) crawler.getConfiguration().getMaxStreamCacheSize(),
    //                crawler.tempDir);
    //
    //        //TODO still pass flag?
    //        //        if (execution.logIntro) {
    //        LogUtil.logCommandIntro(LOG, crawler);
    //        //        }
    //
    //        //TODO still lock?
    //        //        crawler.getState().init(execution.lock);
    //
    //        //TODO delete:
    //        //        crawler.getDataStoreEngine().init(crawler);
    //
    //        // clear if we want to have the crawler resetable (to rerun
    //        // the same instance)...
    //        //services.getEventManager().clearListeners();
    //        crawler.getServices().init(crawler);
    //        //        services.getEventManager().addListenersFromScan(configuration);
    //
    //        if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
    //            CrawlerMonitorJMX.register(crawler);
    //        }
    //
    //        crawler.getStopper().listenForStopRequest(crawler);
    //
    //        crawler.fire(CrawlerEvent.CRAWLER_INIT_END);
    //        //        crawler.fire(eventBeginName(execution));
    //    }
    //
    //    //TODO move this to a util "CrawlerShutdown" class?
    //    // Implicitly invoked when using RunTask
    //    public static void shutdownLocalCrawler(Crawler crawler) {
    //
    //        //        crawler.fire(eventEndName(execution));
    //        crawler.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
    //        try {
    //            // Defer shutdown
    //            ofNullable(crawler.getConfiguration()
    //                    .getDeferredShutdownDuration())
    //                            .filter(d -> d.toMillis() > 0)
    //                            .ifPresent(d -> {
    //                                LOG.info(
    //                                        "Deferred shutdown requested. Pausing for {} "
    //                                                + "starting from this UTC moment: {}",
    //                                        DurationFormatter.FULL.format(d),
    //                                        LocalDateTime.now(ZoneOffset.UTC));
    //                                Sleeper.sleepMillis(d.toMillis());
    //                                LOG.info("Shutdown resumed.");
    //                            });
    //
    //            // Unregister JMX crawlers
    //            if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
    //                LOG.info("Unregistering JMX crawler MBeans.");
    //                CrawlerMonitorJMX.unregister(crawler);
    //            }
    //            //            crawler.getDataStoreEngine().close();
    //
    //            //TODO be conscious not to close for entire cluster here:
    //            crawler.getServices().close();
    //        } finally {
    //            // close state last
    //            if (crawler.tempDir != null) {
    //                try {
    //                    FileUtil.delete(crawler.tempDir.toFile());
    //                } catch (IOException e) {
    //                    LOG.error(
    //                            "Could not delete the temporary directory:"
    //                                    + crawler.tempDir,
    //                            e);
    //                }
    //            }
    //            MDC.clear();
    //            crawler.getState().setTerminatedProperly(true);
    //            crawler.getState().close();
    //            crawler.getStopper().destroy();
    //            crawler.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
    //            crawler.getServices().getEventManager().clearListeners();
    //        }
    //    }
}
