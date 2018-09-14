/* Copyright 2014-2018 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.data.ICrawlData;

/**
 * An HTTP Crawler Event.
 * @author Pascal Essiembre
 */
public class HttpCrawlerEvent extends CrawlerEvent<HttpCrawler> {

    private static final long serialVersionUID = 1L;

    public static final String REJECTED_ROBOTS_TXT =
            "REJECTED_ROBOTS_TXT";
    public static final String CREATED_ROBOTS_META = "CREATED_ROBOTS_META";
    public static final String REJECTED_ROBOTS_META_NOINDEX =
            "REJECTED_ROBOTS_META_NOINDEX";
    public static final String REJECTED_TOO_DEEP = "REJECTED_TOO_DEEP";
    public static final String URLS_EXTRACTED = "URLS_EXTRACTED";
    /** @since 2.8.0 (renamed from REJECTED_CANONICAL) */
    public static final String REJECTED_NONCANONICAL = "REJECTED_NONCANONICAL";
    /** @since 2.3.0 */
    public static final String REJECTED_REDIRECTED = "REJECTED_REDIRECTED";

    /**
     * New crawler event.
     * @param name event name
     * @param source crawler responsible for triggering the event
     * @param crawlData information about a document being crawled
     * @param subject other relevant source related to the event
     * @param exception exception tied to this event (may be <code>null</code>)
     */
    public HttpCrawlerEvent(String name, HttpCrawler source,
            ICrawlData crawlData, Object subject, Throwable exception) {
        super(name, source, crawlData, subject, exception);
    }

    public static HttpCrawlerEvent create(String name, HttpCrawler crawler) {
        return create(name, crawler, null);
    }
    public static HttpCrawlerEvent create(
            String name, HttpCrawler crawler, ICrawlData crawlData) {
        return create(name, crawler, crawlData, null, null);
    }
    public static HttpCrawlerEvent create(String name, HttpCrawler crawler,
            ICrawlData crawlData, Object subject) {
        return create(name, crawler, crawlData, subject, null);
    }
    public static HttpCrawlerEvent create(String name, HttpCrawler crawler,
            ICrawlData crawlData, Object subject, Throwable exception) {
        return new HttpCrawlerEvent(name, crawler, crawlData, subject, exception);
    }
}
