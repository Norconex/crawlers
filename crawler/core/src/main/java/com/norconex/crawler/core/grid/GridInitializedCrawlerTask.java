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
package com.norconex.crawler.core.grid;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.NonNull;

/**
 * Wraps a grid task with a <code>crawler.init()</code> before execution
 * and <code>crawler.orderlyShutdown()</code> after.
 */
public abstract class GridInitializedCrawlerTask implements GridTask {

    private static final long serialVersionUID = 1L;

    @Override
    public void run(@NonNull Crawler crawler, String arg) {
        crawler.init();
        try {
            crawler.fire(CrawlerEvent.CRAWLER_RUN_BEGIN);
            runWithInitializedCrawler(crawler, arg);
        } finally {
            crawler.fire(CrawlerEvent.CRAWLER_RUN_END);
            crawler.orderlyShutdown(false);
        }
    }

    protected abstract void runWithInitializedCrawler(
            Crawler crawler, String arg);

}
