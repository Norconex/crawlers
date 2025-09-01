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
import static java.util.Optional.ofNullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.pipeline.CrawlPipelineFactory;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core2.util.ConcurrentUtil;

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

        CrawlState finalState = null;
        try {
            var pipeFuture = session.getCluster().getPipelineManager()
                    .executePipeline(CrawlPipelineFactory.create(session));

            // Max duration is already handled in CrawlProcessStep, here we
            // have it again just as a safeguard, but we pad to give time to
            // CrawlProcessStep to finish normally after timeout.
            var maxDuration = ctx.getCrawlConfig().getMaxCrawlDuration();
            var result =
                    (maxDuration != null && maxDuration.toMillis() > 0)
                            ? ConcurrentUtil.get(pipeFuture, 5,
                                    TimeUnit.MINUTES)
                            : ConcurrentUtil.get(pipeFuture);

            finalState = session.oncePerSessionAndGet("final-status-task",
                    () -> storeFinalCrawlState(session, result));
        } catch (Exception e) {
            LOG.error("Crawler execution failed.", e);
            finalState = CrawlState.FAILED;
            session.updateCrawlState(CrawlState.FAILED);
        }

        session.fire(CrawlerEvent.CRAWLER_CRAWL_END, this);
        LOG.info("Crawler terminated with state: {}", finalState);

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            swallow(() -> CrawlerMetricsJMX.unregister(ctx));
        }
    }

    private CrawlState storeFinalCrawlState(
            CrawlSession session, PipelineResult result) {
        var pipeStatus = ofNullable(result).map(PipelineResult::getStatus)
                .orElse(PipelineStatus.FAILED);
        LOG.info("Crawl pipeline status: {}", pipeStatus);
        var state = switch (pipeStatus) {
            case FAILED, EXPIRED, STOPPING, PENDING, RUNNING ->
                    CrawlState.FAILED;
            case COMPLETED -> CrawlState.COMPLETED;
            case STOPPED -> CrawlState.STOPPED;
            default -> throw new IllegalArgumentException(
                    "Unexpected value: " + pipeStatus);
        };
        session.updateCrawlState(state);
        return state;
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
