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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanService implements GridService {

    @Override
    public void init(CrawlerContext crawlerContext) {
        //        LogUtil.setMdcCrawlerId(crawlerContext.getId());
        Thread.currentThread().setName(crawlerContext.getId() + "/CLEAN");
        //TODO do we still lock?        lock();

    }

    @Override
    public void start(CrawlerContext crawlerContext) {
        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_BEGIN)
                .source(this)
                .message("Cleaning cached crawler data (does not "
                        + "impact data already committed)...")
                .build());

        crawlerContext.getCommitterService().clean();
        crawlerContext.getGrid().storage().clean();

        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_END)
                .source(this)
                .message("Done cleaning crawler.")
                .build());

        //        mvStore.close();
        //        if (storeDir != null) {
        //            try {
        //                FileUtils.deleteDirectory(storeDir.toFile());
        //            } catch (IOException e) {
        //                throw new GridException("Could not delete crawl store: " +
        //                        storeDir, e);
        //            }
        //        }

        //        crawlerContext.close();
    }

    @Override
    public void end(CrawlerContext crawlerContext) {
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);

    }
}
