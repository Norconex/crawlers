/* Copyright 2019-2026 Norconex Inc.
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

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.Data;

/**
 * Listener adapter for crawler life-cycle events (e.g., start,
 * init, stop, clean).
 */
@Data
public abstract class CrawlerLifeCycleListener
        implements EventListener<Event> {

    @Override
    public final void accept(Event event) {
        if (!(event instanceof CrawlerEvent crawlerEvent)) {
            return;
        }
        onCrawlerEvent(crawlerEvent);
        if (crawlerEvent.is(CrawlerEvent.CRAWLER_CRAWL_BEGIN)) {
            onCrawlerCrawlBegin(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_CRAWL_END)) {
            onCrawlerCrawlEnd(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN)) {
            onCrawlerStopBegin(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_STOP_REQUEST_END)) {
            onCrawlerStopEnd(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_CLEAN_BEGIN)) {
            onCrawlerCleanBegin(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_CLEAN_END)) {
            onCrawlerCleanEnd(crawlerEvent);
        } else if (crawlerEvent.is(CrawlerEvent.CRAWLER_ERROR)) {
            onCrawlerError(crawlerEvent);
        }
    }

    protected void onCrawlerEvent(CrawlerEvent event) {
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
