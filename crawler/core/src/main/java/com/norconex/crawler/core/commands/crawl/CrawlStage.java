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
package com.norconex.crawler.core.commands.crawl;

import java.util.stream.Stream;

import org.apache.commons.lang3.EnumUtils;

public enum CrawlStage {
    /**
     * Has not started yet.
     */
    IDLE,
    /**
     * Prepare doc ledger and init URL queue (single node).
     */
    INITIALIZE,
    /**
     * Distribute running the crawler across nodes,
     * reading and filling the queue (multiple nodes).
     */
    CRAWL,
    /**
     * Handle orphans after crawling is done (multiple nodes).
     */
    HANDLE_ORPHANS,
    /**
     * The crawler has completed (successfully or not).
     */
    ENDED;

    /**
     * Gets the crawl stage from its name. A null or non-matching stage
     * always returns <code>IDLE</code>.
     * @param name stage name
     * @return crawl stage
     */
    public static CrawlStage of(String name) {
        return EnumUtils.getEnumIgnoreCase(CrawlStage.class, name, IDLE);
    }

    public boolean isAnyOf(CrawlStage... stages) {
        if (stages == null) {
            return false;
        }
        return Stream.of(stages).anyMatch(stage -> stage == this);
    }
}