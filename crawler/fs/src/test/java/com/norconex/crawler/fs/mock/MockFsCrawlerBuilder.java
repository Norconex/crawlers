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
import java.util.List;
import java.util.function.Consumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.fs.FsCrawlDriverFactory;

/**
 * Builder for test {@link Crawler} instances pre-configured with the
 * file-system crawl driver and a {@link MemoryCommitter}.
 */
public final class MockFsCrawlerBuilder {

    private final Path workDir;
    private Consumer<CrawlConfig> cfgModifier = cfg -> {};

    public MockFsCrawlerBuilder(Path workDir) {
        this.workDir = workDir;
    }

    public MockFsCrawlerBuilder configModifier(Consumer<CrawlConfig> modifier) {
        this.cfgModifier = modifier;
        return this;
    }

    /**
     * Builds a crawler with a {@link MemoryCommitter} already registered.
     * Retrieve results via
     * {@code (MemoryCommitter) crawler.getCrawlConfig().getCommitters().get(0)}.
     */
    public Crawler crawler() {
        var mem = new MemoryCommitter();
        var config = new CrawlConfig()
                .setId("test-crawler")
                .setWorkDir(workDir)
                .setCommitters(List.of(mem));
        cfgModifier.accept(config);
        return new Crawler(FsCrawlDriverFactory.create(), config);
    }
}
