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
 * @since 2.0.0
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CrawlSessionEvent extends Event {

    private static final long serialVersionUID = 1L;

    public static final String SESSION_RUN_BEGIN = "SESSION_RUN_BEGIN";
    public static final String SESSION_RUN_END = "SESSION_RUN_END";
    public static final String SESSION_STOP_BEGIN = "SESSION_STOP_BEGIN";
    public static final String SESSION_STOP_END = "SESSION_STOP_END";
    public static final String SESSION_CLEAN_BEGIN = "SESSION_CLEAN_BEGIN";
    public static final String SESSION_CLEAN_END = "SESSION_CLEAN_END";
    public static final String SESSION_STORE_EXPORT_BEGIN =
            "SESSION_STORE_EXPORT_BEGIN";
    public static final String SESSION_STORE_EXPORT_END =
            "SESSION_STORE_EXPORT_END";
    public static final String SESSION_STORE_IMPORT_BEGIN =
            "SESSION_STORE_IMPORT_BEGIN";
    public static final String SESSION_STORE_IMPORT_END =
            "SESSION_STORE_IMPORT_END";

    //TODO Not used. Needed?
    public static final String SESSION_ERROR = "SESSION_ERROR";

    @Override
    public CrawlSession getSource() {
        return (CrawlSession) super.getSource();
    }

    public boolean isCollectorShutdown(Event event) {
        return is(SESSION_RUN_END, SESSION_ERROR, SESSION_STOP_END);
    }
}
