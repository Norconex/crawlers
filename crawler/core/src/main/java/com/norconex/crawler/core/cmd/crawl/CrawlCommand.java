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

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.pipeline.CrawlPipelineStages;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    public static final String KEY_CRAWL_PIPELINE = "crawlPipeline";
    private static final String PROGRESS_LOGGER_KEY = "progressLogger";

    @Override
    public void execute(CrawlerContext ctx) {

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN);

        trackProgress(ctx);

        var completed = Boolean.TRUE.equals(ConcurrentUtil.get(ctx
                .getGrid()
                .pipeline()
                .run(KEY_CRAWL_PIPELINE,
                        CrawlPipelineStages.create(),
                        ctx)));

        if (completed) {
            LOG.info("Crawler completed execution.");
        } else {
            LOG.info("Crawler execution ended before completion.");
        }
        ctx.getGrid().compute().stop(PROGRESS_LOGGER_KEY);
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_END);
        LOG.info("Node done crawling.");
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
                .runOnOne(PROGRESS_LOGGER_KEY, progressLogger);
    }
}
