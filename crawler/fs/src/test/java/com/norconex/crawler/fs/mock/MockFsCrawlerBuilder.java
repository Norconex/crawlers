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
package com.norconex.crawler.fs.mock;

import java.nio.file.Path;

import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.fs.FsCrawlDriverFactory;

/**
 * Same as {@link MockCrawlerBuilder}, but with a file system crawl driver.
 */
public final class MockFsCrawlerBuilder extends MockCrawlerBuilder {

    public MockFsCrawlerBuilder(Path workDir) {
        super(workDir);
        crawlDriver(FsCrawlDriverFactory.create());
    }
}
