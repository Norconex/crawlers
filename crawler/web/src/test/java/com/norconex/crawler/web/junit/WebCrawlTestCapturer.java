/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.web.junit;

import java.util.List;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.web.WebCrawlDriverFactory;
import com.norconex.crawler.web.WebCrawlerConfig;

/**
 * Utility class for running a web crawler in tests and capturing results.
 */
public final class WebCrawlTestCapturer {

    private WebCrawlTestCapturer() {
    }

    /**
     * Captures the result of a web crawl given the provided configuration.
     * A fresh {@link MemoryCommitter} is always used, replacing any existing
     * committers on the config. Each call provides a clean snapshot of
     * what was committed during that specific crawl run.
     *
     * @param config the web crawler configuration to crawl with
     * @return crawl captures containing the memory committer results
     */
    public static CrawlCaptures crawlAndCapture(WebCrawlerConfig config) {
        var mem = new MemoryCommitter();
        config.setCommitters(List.of(mem));
        new Crawler(WebCrawlDriverFactory.create(), config).crawl();
        return new CrawlCaptures(mem);
    }

    /**
     * Holds the result of a captured crawl run.
     */
    public static final class CrawlCaptures {
        private final MemoryCommitter committer;

        private CrawlCaptures(MemoryCommitter committer) {
            this.committer = committer;
        }

        /**
         * Returns the {@link MemoryCommitter} that received all
         * upserts and deletes during the crawl run.
         * @return memory committer
         */
        public MemoryCommitter getCommitter() {
            return committer;
        }
    }
}
