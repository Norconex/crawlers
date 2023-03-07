/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

import lombok.NonNull;

public final class WebTestUtil {

    private WebTestUtil() {}

    /**
     * Gets the {@link MemoryCommitter} from first committer of the first
     * crawler from a crawl session (assuming the first committer is
     * a {@link MemoryCommitter}).  If that committer does not
     * exists or is not a memory committer, an exception is thrown.
     * @param crawlSession crawl session
     * @return Memory committer
     */
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull CrawlSession crawlSession) {
        return (MemoryCommitter) getFirstCrawlerConfig(
                crawlSession).getCommitters().get(0);
    }
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull Crawler crawler) {
        return (MemoryCommitter)
                crawler.getCrawlerConfig().getCommitters().get(0);
    }
    public static Crawler getFirstCrawler(
            @NonNull CrawlSession crawlSession) {
        if (!crawlSession.getCrawlers().isEmpty()) {
            return crawlSession.getCrawlers().get(0);
        }
        return null;
    }
    public static WebCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSession crawlSession) {
        return getFirstCrawlerConfig(crawlSession.getCrawlSessionConfig());
    }
    public static WebCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSessionConfig crawlSessionConfig) {
        return (WebCrawlerConfig) crawlSessionConfig.getCrawlerConfigs().get(0);
    }

    public static void ignoreAllIgnorables(CrawlSession crawlSession) {
        crawlSession.getCrawlSessionConfig().getCrawlerConfigs().forEach(c -> {
            var cfg = (WebCrawlerConfig) c;
            cfg.setIgnoreCanonicalLinks(true);
            cfg.setIgnoreRobotsMeta(true);
            cfg.setIgnoreRobotsTxt(true);
            cfg.setIgnoreSitemap(true);
        });
    }
}
