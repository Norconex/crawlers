/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.web.event;

/**
 * HTTP Crawler event names. Those are in addition to event names from
 * dependant libraries.
 */
public final class WebCrawlerEvent {

    public static final String FETCHED_ROBOTS_TXT = "FETCHED_ROBOTS_TXT";
    public static final String REJECTED_ROBOTS_TXT =
            "REJECTED_ROBOTS_TXT";
    public static final String EXTRACTED_ROBOTS_META = "EXTRACTED_ROBOTS_META";
    public static final String REJECTED_ROBOTS_META_NOINDEX =
            "REJECTED_ROBOTS_META_NOINDEX";
    public static final String URLS_EXTRACTED = "URLS_EXTRACTED";
    public static final String REJECTED_NONCANONICAL = "REJECTED_NONCANONICAL";
    public static final String REJECTED_REDIRECTED = "REJECTED_REDIRECTED";
    public static final String URLS_POST_IMPORTED =
            "URLS_POST_IMPORTED";
    public static final String SITEMAP_RESOLVE_BEGIN = "SITEMAP_RESOLVE_BEGIN";
    public static final String SITEMAP_RESOLVE_END = "SITEMAP_RESOLVE_END";
    public static final String REJECTED_OUT_OF_SCOPE =
            "REJECTED_OUT_OF_SCOPE";

    private WebCrawlerEvent() {
    }
}
