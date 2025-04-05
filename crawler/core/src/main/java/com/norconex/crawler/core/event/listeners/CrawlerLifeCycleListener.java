/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.core.event.listeners;

import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.Data;

/**
 * Listener adapter for crawler life-cycle events (e.g., start,
 * init, stop, clean).
 */
@Data
public abstract class CrawlerLifeCycleListener
        implements EventListener<CrawlerEvent> {

    @Override
    public final void accept(CrawlerEvent event) {
        if (event == null) {
            return;
        }
        onCrawlerEvent(event);
        if (event.is(CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN)) {
            onCrawlerContextInitBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CONTEXT_INIT_END)) {
            onCrawlerContextInitEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CRAWL_BEGIN)) {
            onCrawlerCrawlBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CRAWL_END)) {
            onCrawlerCrawlEnd(event);
            //        } else if (event.is(CrawlerEvent.TASK_RUN_BEGIN)) {
            //            onTaskRunBegin(event);
            //        } else if (event.is(CrawlerEvent.TASK_RUN_END)) {
            //            onTaskRunEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)) {
            onCrawlerRunThreadBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_END)) {
            onCrawlerRunThreadEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN)) {
            onCrawlerStopBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_STOP_REQUEST_END)) {
            onCrawlerStopEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CLEAN_BEGIN)) {
            onCrawlerCleanBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CLEAN_END)) {
            onCrawlerCleanEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_ERROR)) {
            onCrawlerError(event);
        }
    }

    protected void onCrawlerEvent(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerContextInitBegin(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerContextInitEnd(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerCrawlBegin(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerCrawlEnd(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerError(CrawlerEvent event) {
        //NOOP
    }

    //    protected void onTaskRunEnd(CrawlerEvent event) {
    //        //NOOP
    //    }
    //
    //    protected void onTaskRunBegin(CrawlerEvent event) {
    //        //NOOP
    //    }

    protected void onCrawlerRunThreadBegin(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerRunThreadEnd(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerStopBegin(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerStopEnd(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerCleanBegin(CrawlerEvent event) {
        //NOOP
    }

    protected void onCrawlerCleanEnd(CrawlerEvent event) {
        //NOOP
    }
}
