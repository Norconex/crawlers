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
package com.norconex.crawler.core.mocks.crawler;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

public final class MockCrawler {

    public static final String CRAWLER_ID = StubCrawlerConfig.CRAWLER_ID;

    private MockCrawler() {
    }

    public static Crawler memoryCrawler(Path workDir) {
        return memoryCrawler(workDir, (CrawlerConfig) null);
    }

    public static Crawler memoryCrawler(Path workDir, CrawlerConfig config) {
        //NOTE: we could just assume that the workDir is set in config already,
        // but we want to enforce passing one when testing
        var cfg = config != null
                ? StubCrawlerConfig.toMemoryCrawlerConfig(workDir, config)
                : StubCrawlerConfig.memoryCrawlerConfig(workDir);
        return new Crawler(
                new CrawlerContext(MockCrawlerSpecProvider.class, cfg));
    }

    public static Crawler memoryCrawler(
            Path workDir, Consumer<CrawlerConfig> c) {
        var cfg = StubCrawlerConfig.memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(cfg);
        }
        return new Crawler(
                new CrawlerContext(MockCrawlerSpecProvider.class, cfg));
    }
}