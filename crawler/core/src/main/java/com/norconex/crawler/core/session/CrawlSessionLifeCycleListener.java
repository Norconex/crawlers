/* Copyright 2018-2022 Norconex Inc.
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
package com.norconex.crawler.core.session;

import com.norconex.commons.lang.event.EventListener;

/**
 * Crawl session event listener adapter for session startup/shutdown.
 *
 * @since 4.0.0 (renamed CollectorSessionLifeCycleListener)
 */
public class CrawlSessionLifeCycleListener
        implements EventListener<CrawlSessionEvent> {

    @Override
    public final void accept(CrawlSessionEvent event) {
        if (event == null) {
            return;
        }
        onCrawlSessionEvent(event);
        if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)) {
            onCrawlSessionRunBegin(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_END)) {
            onCrawlSessionRunEnd(event);
            onCrawlSessionShutdown(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN)) {
            onCrawlSessionStopBegin(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_STOP_END)) {
            onCrawlSessionStopEnd(event);
            onCrawlSessionShutdown(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN)) {
            onCrawlSessionCleanBegin(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_CLEAN_END)) {
            onCrawlSessionCleanEnd(event);
        } else if (event.is(CrawlSessionEvent.CRAWLSESSION_ERROR)) {
            onCrawlSessionError(event);
            onCrawlSessionShutdown(event);
        }
    }

    protected void onCrawlSessionEvent(CrawlSessionEvent event) {
        //NOOP
    }

    /**
     * Triggered when a crawl session is ending its execution on either
     * a {@link CrawlSessionEvent#CRAWLSESSION_ERROR},
     * {@link CrawlSessionEvent#CRAWLSESSION_RUN_END} or
     * {@link CrawlSessionEvent#CRAWLSESSION_STOP_END} event.
     * @param event crawl session event
     */
    protected void onCrawlSessionShutdown(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionError(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionRunBegin(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionRunEnd(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionStopBegin(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionStopEnd(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionCleanBegin(CrawlSessionEvent event) {
        //NOOP
    }
    protected void onCrawlSessionCleanEnd(CrawlSessionEvent event) {
        //NOOP
    }
}
