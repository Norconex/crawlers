/* Copyright 2024-2025 Norconex Inc.
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

import static com.norconex.crawler.core2.util.ExceptionSwallower.swallow;

import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.metrics.CrawlerMetricsJMX;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";
    //    public static final String KEY_CRAWL_PIPELINE = "crawlPipeline";
    private static final String PROGRESS_LOGGER_KEY = "progressLogger";
    private final AtomicBoolean pendingLoggerStopped = new AtomicBoolean();

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        //TODO apply maxCrawlDuration (pass timeout to pipeline)

        pendingLoggerStopped.set(false); // just in case
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(ctx);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        session.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN, this);

        trackProgress(session);

        //TODO migrate to pipeline:
        //        try {
        //            CrawlPipelineFactory.create(session).run();
        //            if (!ConcurrentUtil.waitUntil(() -> session
        //                    .getCrawlState()
        //                    .isTerminal(), Duration.ofSeconds(5))) {
        //                updateCrawlState(session, CrawlState.COMPLETED);
        //                LOG.info("Crawler completed execution.");
        //            } else {
        //                LOG.debug("Crawl state not terminal after waiting, setting it "
        //                        + "to COMPLETED.");
        //            }
        //        } catch (Exception e) {
        //            updateCrawlState(session, CrawlState.FAILED);
        //            LOG.error("Crawler execution failed.", e);
        //        }

        //TODO have thread/timer that invoke stopPipeline(id) with the
        // timeout value from crawler configuration.

        //        var result = ctx.getGrid()
        //                .getCompute()
        //                .executePipeline(CrawlPipelineFactory.create(ctx));

        //        // If there is a terminal crawl state already set, we use it, else
        //        // we wait a bit for one, in case it hasn't been synched yet, then,
        //        // we rely on pipeline last task state as fallback.
        //        if (!ConcurrentUtil.waitUntil(() -> ctx.getSessionProperties()
        //                .getCrawlState()
        //                .map(CrawlState::isTerminal)
        //                .orElse(false), Duration.ofSeconds(5))) {
        //            if (result.getState() == TaskState.COMPLETED) {
        //                updateCrawlState(ctx, CrawlState.COMPLETED);
        //                LOG.info("Crawler completed execution.");
        //            } else {
        //                updateCrawlState(ctx, CrawlState.FAILED);
        //                LOG.info("Crawler execution failed or otherwise ended "
        //                        + "before completion.");
        //            }
        //        }

        //TODO migrate this stopTask?
        //     session.getCluster().getTaskManager().stopTask(PROGRESS_LOGGER_KEY);

        //        ctx.getGrid().getCompute().stopTask(PROGRESS_LOGGER_KEY);

        //TODO check if we should reintroduce waiting for logger shutdown
        // as with latest code it seems to always shut down before returning
        //        try {
        //            ConcurrentUtil.waitUntilOrThrow(
        //                    //TODO make it configurable???
        //                    pendingLoggerStopped::get, Duration.ofSeconds(60));
        //        } catch (TimeoutException e) {
        //            throw new CrawlerException("Could not stop progress logger.", e);
        //        }
        session.fire(CrawlerEvent.CRAWLER_CRAWL_END, this);
        LOG.info("Node done crawling with state: {}", session.getCrawlState());

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            swallow(() -> CrawlerMetricsJMX.unregister(ctx));
        }
    }

    private void updateCrawlState(CrawlSession session, CrawlState state) {
        //TODO still needed?
        //        session.getCluster().getTaskManager().runOnOneSync("updateCrawlState",
        //                sess -> {
        //                    sess.updateCrawlState(state);
        //                    return null;
        //                });
    }

    private void trackProgress(CrawlSession session) {
        //TODO still needed?
        //        // only 1 node reports progress
        //        CompletableFuture.runAsync(() -> {
        //            var taskManager = session.getCluster().getTaskManager();
        //            var loggerTask = new LoggerTask();
        //            taskManager.runOnOneSync(PROGRESS_LOGGER_KEY, loggerTask);
        //            // Wait for logger to actually stop before setting flag
        //            while (!pendingLoggerStopped.get()) {
        //                try {
        //                    Thread.sleep(100);
        //                } catch (InterruptedException e) {
        //                    Thread.currentThread().interrupt();
        //                    break;
        //                }
        //            }
        //        }, Executors.newFixedThreadPool(1));
    }

    //    static class LoggerTask implements ClusterTask<Void> {
    //        private CrawlProgressLogger logger;
    //
    //        @Override
    //        public Void execute(CrawlSession session) {
    //            logger = new CrawlProgressLogger(session.getCrawlContext());
    //            logger.start();
    //            return null;
    //        }
    //
    //        @Override
    //        public void stop(CrawlSession session) {
    //            if (logger != null) {
    //                logger.stop();
    //            }
    //            // Signal that logger has stopped
    //            //            var cmd =
    //            //                    (CrawlCommand) session.getCrawlContext().getCommand();
    //            //            if (cmd != null) {
    //            //                cmd.pendingLoggerStopped.set(true);
    //            //            }
    //        }
    //    }
}
