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

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.clean.CleanCommand;
import com.norconex.crawler.core.cmd.crawl.service.CrawlService;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CrawlCommand implements Command {

    private final boolean startClean;

    @Override
    public void execute(CrawlerContext ctx) {

        if (startClean) {
            cleanFirst(ctx);
        }

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN);
        ConcurrentUtil.block(ctx.getGrid().services().start(
                "conductor_service", CrawlService.class, null));

        while (ctx.getCrawlStage() != CrawlStage.ENDED) {
            Sleeper.sleepSeconds(1);
        }
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
}
