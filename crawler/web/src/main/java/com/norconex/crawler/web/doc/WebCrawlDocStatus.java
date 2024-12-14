/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc;

import com.norconex.crawler.core.doc.DocResolutionStatus;

/**
 * Represents a URL crawling status.
 * @see DocResolutionStatus
 */
public class WebCrawlDocStatus extends DocResolutionStatus {

    private static final long serialVersionUID = 1L;

    /**
     * @since 2.3.0
     */
    public static final WebCrawlDocStatus REDIRECT =
            new WebCrawlDocStatus("REDIRECT");

    protected WebCrawlDocStatus(String state) {
        super(state);
    }
}
