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
    public void init(CrawlerContext crawlerContext, String arg) {
        Thread.currentThread().setName(crawlerContext.getId() + "/CLEAN");
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

        // Close metrics prematurely, before cleaning, or it will want to
        // report on a blown-away store:
        crawlerContext.getMetrics().close();

        crawlerContext.getGrid().storage().clean();

        //        try {
        //            System.err.println("XXX CLEAN CALLING SHUTDOWN");
        //            System.err.flush();
        //            //            crawlerContext.getMetrics().flush();
        //            crawlerContext.getGrid().shutdown().get();
        //            System.err.println("XXX CLEAN CALLED SHUTDOWN");
        //            System.err.flush();
        //        } catch (InterruptedException | ExecutionException e) {
        //            // TODO Auto-generated catch block
        //            e.printStackTrace();
        //        }

        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_END)
                .source(this)
                .message("Done cleaning crawler.")
                .build());

    }

    @Override
    public void end(CrawlerContext crawlerContext) {

        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        //        crawlerContext.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);

    }
}
