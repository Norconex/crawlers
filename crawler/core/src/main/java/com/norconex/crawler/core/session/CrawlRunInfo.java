/* Copyright 2025 Norconex Inc.
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

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Details on the current crawl run.
 */
@Value
@Builder
@Jacksonized
public class CrawlRunInfo {
    /**
     * Stable identity of a crawler. Usually configuration-driven.
     */
    private final String crawlerId;
    /**
     * Full life-cycle identifier for a single crawl. Represents the crawl
     * session, spans multiple cluster restarts/resumes.
     */
    private final String crawlSessionId;
    /**
     * Unique identifier for each time the crawler starts (until termination).
     * When run on a cluster, it is also an ephemeral cluster instance
     * identifier.
     */
    private final String crawlRunId;
    /**
     * The mode used to crawl web sites. For any given crawler, a fresh crawl
     * is always a {@link CrawlMode#FULL} crawl while subsequent crawls
     * are usually considered {@link CrawlMode#INCREMENTAL} crawls.
     */
    private final CrawlMode crawlMode;

    /**
     * Whether we are starting a new crawl session or resuming a previous one.
     */
    private final CrawlResumeState crawlResumeState;
}
