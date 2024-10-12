/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.util;

import org.slf4j.Logger;
import org.slf4j.MDC;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.CrawlerConfig;

import lombok.NonNull;

public final class LogUtil {

    public static final String MDC_CRAWLER_ID = "crawler.id";
    public static final String MDC_CRAWLER_ID_SAFE = "crawler.id.safe";

    private LogUtil() {
    }

    /**
     * <p>Sets two representations of the supplied crawler ID to the
     * Mapped Diagnostic Context (MDC) for logging implementations
     * supporting it:</p>
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
    public static void setMdcCrawlerId(String crawlerId) {
        MDC.put(MDC_CRAWLER_ID, crawlerId);
        MDC.put(MDC_CRAWLER_ID_SAFE, FileUtil.toSafeFileName(crawlerId));
    }

    public static void logCommandIntro(
            @NonNull Logger logger, @NonNull CrawlerConfig crawlerConfig) {
        if (logger.isInfoEnabled()) {
            logger.info("\n\n{}", About.about(crawlerConfig, true));
        }
    }
}
