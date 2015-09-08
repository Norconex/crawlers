/* Copyright 2014-2015 Norconex Inc.
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

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.data.ICrawlData;

/**
 * An HTTP Crawler Event.
 * @author Pascal Essiembre
 */
public class HttpCrawlerEvent extends CrawlerEvent {

    public static final String REJECTED_ROBOTS_TXT = 
            "REJECTED_ROBOTS_TXT";
    public static final String CREATED_ROBOTS_META = "CREATED_ROBOTS_META";
    public static final String REJECTED_ROBOTS_META_NOINDEX = 
            "REJECTED_ROBOTS_META_NOINDEX";
    public static final String REJECTED_TOO_DEEP = "REJECTED_TOO_DEEP";
    public static final String URLS_EXTRACTED = "URLS_EXTRACTED";
    /** @since 2.2.0 */
    public static final String REJECTED_CANONICAL = "REJECTED_CANONICAL";
    /** @since 2.3.0 */
    public static final String REJECTED_DUPLICATE = "REJECTED_DUPLICATE";

    public HttpCrawlerEvent(String eventType, ICrawlData crawlData,
            Object subject) {
        super(eventType, crawlData, subject);
    }

}
