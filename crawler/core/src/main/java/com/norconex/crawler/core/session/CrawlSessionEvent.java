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

import com.norconex.commons.lang.event.Event;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * A crawl session event.
 *
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CrawlSessionEvent extends Event {

    private static final long serialVersionUID = 1L;

    public static final String CRAWLSESSION_RUN_BEGIN =
            "CRAWLSESSION_RUN_BEGIN";
    public static final String CRAWLSESSION_RUN_END = "CRAWLSESSION_RUN_END";
    public static final String CRAWLSESSION_STOP_BEGIN =
            "CRAWLSESSION_STOP_BEGIN";
    public static final String CRAWLSESSION_STOP_END = "CRAWLSESSION_STOP_END";
    public static final String CRAWLSESSION_CLEAN_BEGIN =
            "CRAWLSESSION_CLEAN_BEGIN";
    public static final String CRAWLSESSION_CLEAN_END =
            "CRAWLSESSION_CLEAN_END";
    public static final String CRAWLSESSION_STORE_EXPORT_BEGIN =
            "CRAWLSESSION_STORE_EXPORT_BEGIN";
    public static final String CRAWLSESSION_STORE_EXPORT_END =
            "CRAWLSESSION_STORE_EXPORT_END";
    public static final String CRAWLSESSION_STORE_IMPORT_BEGIN =
            "CRAWLSESSION_STORE_IMPORT_BEGIN";
    public static final String CRAWLSESSION_STORE_IMPORT_END =
            "CRAWLSESSION_STORE_IMPORT_END";

    //TODO Not used. Needed?
    public static final String CRAWLSESSION_ERROR = "CRAWLSESSION_ERROR";

    @Override
    public CrawlSession getSource() {
        return (CrawlSession) super.getSource();
    }
    public boolean isCrawlSessionShutdown() {
        return is(
                CRAWLSESSION_RUN_END,
                CRAWLSESSION_ERROR,
                CRAWLSESSION_STOP_END);
    }
    public static boolean isCrawlSessionShutdown(Event event) {
        if (event == null) {
            return false;
        }
        return event.is(
                CRAWLSESSION_RUN_END,
                CRAWLSESSION_ERROR,
                CRAWLSESSION_STOP_END);
    }
}
