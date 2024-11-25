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

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.mocks.grid.MockNoopGrid;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

public final class MockCrawlerContext extends CrawlerContext {

    private MockCrawlerContext(
            CrawlerSpec crawlerSpec,
            CrawlerConfig crawlerConfig,
            Grid grid) {
        super(crawlerSpec, crawlerConfig, grid);
    }

    public static MockCrawlerContext memoryContext(Path workDir) {
        return memoryContext(
                workDir, StubCrawlerConfig.memoryCrawlerConfig(workDir));
    }

    public static MockCrawlerContext memoryContext(
            Path workDir, Consumer<CrawlerConfig> configConsumer) {
        var config = StubCrawlerConfig.memoryCrawlerConfig(workDir);
        if (configConsumer != null) {
            configConsumer.accept(config);
        }
        return memoryContext(workDir, config);
    }

    public static MockCrawlerContext memoryContext(
            Path workDir, CrawlerConfig config) {
        config.setWorkDir(workDir);
        var grid = config.getGridConnector() == null
                ? new MockNoopGrid()
                : config.getGridConnector().connect(
                        MockCrawlerSpecProvider.class, config);
        return new MockCrawlerContext(
                new MockCrawlerSpecProvider().get(),
                config,
                grid);
    }
}
