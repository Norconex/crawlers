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
package com.norconex.crawler.core.services;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StopService implements GridService {

    @Override
    public void init(CrawlerContext crawlerContext, String arg) {
        Thread.currentThread().setName(crawlerContext.getId() + "/STOP");
        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_STOP_BEGIN)
                .source(this)
                .message("Received request to stop the crawler.")
                .build());
    }

    @Override
    public void start(CrawlerContext crawlerContext) {
        ConcurrentUtil.block(crawlerContext.getGrid().shutdown());
    }

    @Override
    public void end(CrawlerContext crawlerContext) {
        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_STOP_END)
                .source(this)
                .message("Crawler stopped")
                .build());

    }
}
