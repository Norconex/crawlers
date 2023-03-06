/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import com.norconex.commons.lang.event.EventListener;

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
        if (event.is(CrawlerEvent.CRAWLER_INIT_BEGIN)) {
            onCrawlerInitBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_INIT_END)) {
            onCrawlerInitEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_BEGIN)) {
            onCrawlerRunBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_END)) {
            onCrawlerRunEnd(event);
            onCrawlerShutdown(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)) {
            onCrawlerRunThreadBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_END)) {
            onCrawlerRunThreadEnd(event);
        } else if (event.is(CrawlerEvent.CRAWLER_STOP_BEGIN)) {
            onCrawlerStopBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_STOP_END)) {
            onCrawlerStopEnd(event);
            onCrawlerShutdown(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CLEAN_BEGIN)) {
            onCrawlerCleanBegin(event);
        } else if (event.is(CrawlerEvent.CRAWLER_CLEAN_END)) {
            onCrawlerCleanEnd(event);
        }
    }

    protected void onCrawlerEvent(CrawlerEvent event) {
        //NOOP
    }
    /**
     * Triggered when a crawler is ending its execution on either
     * a {@link CrawlerEvent#CRAWLER_RUN_END} or
     * {@link CrawlerEvent#CRAWLER_STOP_END} event.
     * @param event crawler event
     */
    protected void onCrawlerShutdown(CrawlerEvent event) {
        //NOOP
    }
    protected void onCrawlerInitBegin(CrawlerEvent event) {
        //NOOP
    }
    protected void onCrawlerInitEnd(CrawlerEvent event) {
        //NOOP
    }
    protected void onCrawlerRunBegin(CrawlerEvent event) {
        //NOOP
    }
    protected void onCrawlerRunEnd(CrawlerEvent event) {
        //NOOP
    }
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
