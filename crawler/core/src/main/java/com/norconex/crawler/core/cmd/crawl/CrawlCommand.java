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

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.pipeline.CrawlPipelineFactory;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.BaseGridTask;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";
    public static final String KEY_CRAWL_PIPELINE = "crawlPipeline";
    private static final String PROGRESS_LOGGER_KEY = "progressLogger";
    private final AtomicBoolean pendingLoggerStopped = new AtomicBoolean();

    @Override
    public void execute(CrawlContext ctx) {
        pendingLoggerStopped.set(false); // just in case
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(ctx);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN);

        trackProgress(ctx);

        var result = ctx.getGrid()
                .getCompute()
                .executePipeline(CrawlPipelineFactory.create(ctx));

        // If there is a terminal crawl state already set, we use it, else
        // we wait a bit for one, in case it hasn't been synched yet, then,
        // we rely on pipeline last task state as fallback.
        if (!ConcurrentUtil.waitUntil(() -> ctx.getSessionProperties()
                .getCrawlState()
                .map(CrawlState::isTerminal)
                .orElse(false), Duration.ofSeconds(5))) {
            if (result.getState() == TaskState.COMPLETED) {
                updateCrawlState(ctx, CrawlState.COMPLETED);
                LOG.info("Crawler completed execution.");
            } else {
                updateCrawlState(ctx, CrawlState.FAILED);
                LOG.info("Crawler execution failed or otherwise ended "
                        + "before completion.");
            }
        }

        ctx.getGrid().getCompute().stopTask(PROGRESS_LOGGER_KEY);
        try {
            ConcurrentUtil.waitUntilOrThrow(
                    pendingLoggerStopped::get, Duration.ofSeconds(60));
        } catch (TimeoutException e) {
            throw new CrawlerException("Could not stop progress logger.", e);
        }
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_END);
        LOG.info("Node done crawling with state: {}",
                ctx.getSessionProperties().getCrawlState().orElse(null));

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            swallow(() -> CrawlerMetricsJMX.unregister(ctx));
        }
    }

    private void updateCrawlState(CrawlContext ctx, CrawlState state) {
        ctx.getGrid().getCompute().executeTask(GridTaskBuilder
                .create("updateCrawlState")
                .singleNode()
                .processor(grid -> CrawlContext
                        .get(grid)
                        .getSessionProperties()
                        .updateCrawlState(state))
                .build());
    }

    private void trackProgress(CrawlContext ctx) {
        // only 1 node reports progress
        CompletableFuture.runAsync(() -> {
            ctx.getGrid()
                    .getCompute()
                    .executeTask(new LoggerTask());
            pendingLoggerStopped.set(true);
        }, Executors.newFixedThreadPool(1));
    }

    static class LoggerTask extends BaseGridTask.SingleNodeTask {

        private static final long serialVersionUID = 1L;
        private transient CrawlProgressLogger logger;

        protected LoggerTask() {
            super(PROGRESS_LOGGER_KEY);
        }

        @Override
        public Serializable execute(Grid grid) {
            logger = new CrawlProgressLogger(CrawlContext.get(grid));
            logger.start();
            return null;
        }

        @Override
        public void stop(Grid grid) {
            if (logger != null) {
                logger.stop();
            }
        }
    }
}
