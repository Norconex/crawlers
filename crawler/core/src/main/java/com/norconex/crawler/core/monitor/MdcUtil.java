/* Copyright 2022 Norconex Inc.
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
package com.norconex.crawler.core.monitor;

import org.slf4j.MDC;

import com.norconex.commons.lang.file.FileUtil;

/**
 * Utility methods to simplify adding Mapped Diagnostic Context (MDC) to
 * logging in a consistent way for crawlers and crawl sessions, as well as
 * offering filename-friendly version as well.
 * @since 2.0.1
 */
public final class MdcUtil {

    private MdcUtil() {}

    /**
     * <p>Sets two representations of the supplied crawler ID in the MDC:</p>
     * <ul>
     *   <li>
     *     <code>crawler.id</code> &rarr; the crawler id, as supplied.
     *   </li>
     *   <li>
     *     <code>crawler.id.safe</code> &rarr; the crawler id encoded
     *     to be safe to use as filename on any file system, as
     *     per {@link FileUtil}.
     *   </li>
     * </ul>
     * @param crawlerId crawler id
     */
    public static void setCrawlerId(String crawlerId) {
        MDC.put("crawler.id", crawlerId);
        MDC.put("crawler.id.safe", FileUtil.toSafeFileName(crawlerId));
    }

    /**
     * <p>
     * Sets two representations of the supplied crawl session ID in
     * the MDC:
     * </p>
     * <ul>
     *   <li>
     *     <code>crawlsession.id</code> &rarr; the crawl session ID, as
     *     supplied.
     *   </li>
     *   <li>
     *     <code>crawlsession.id.safe</code> &rarr; the crawl session ID
     *     encoded to be safe to use as filename on any file system, as per
     *     {@link FileUtil#toSafeFileName(String)}.
     *   </li>
     * </ul>
     * @param crawlSessionId crawl session ID
     */
    public static void setCrawlSessionId(String crawlSessionId) {
        MDC.put("crawlsession.id", crawlSessionId);
        MDC.put("crawlsession.id.safe",
                FileUtil.toSafeFileName(crawlSessionId));
    }
}
