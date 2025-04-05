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

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
public class MockCrawlerBuilder {

    public static final String CRAWLER_ID = StubCrawlerConfig.CRAWLER_ID;

    //NOTE: we could just assume that the workDir is set in config already,
    // but we want to enforce passing one when testing.
    private final Path workDir;

    /**
     * Crawler specification provider. Defaults to
     * {@link MockCrawlerSpecProvider}.
     */
    @NonNull
    private Class<? extends CrawlerSpecProvider> specProviderClass =
            MockCrawlerSpecProvider.class;

    /**
     * Crawler configuration. Creates a default one if not provided.
     */
    private CrawlerConfig config;

    /**
     * Crawler configuration modifier.
     */
    private Consumer<CrawlerConfig> configModifier;

    public MockCrawlerBuilder(Path workDir) {
        this.workDir = workDir;
    }

    /**
     * Builds a mock {@link Crawler}.
     * @return crawler
     */
    public Crawler crawler() {
        return new Crawler(specProviderClass, resolvedConfig());
    }

    /**
     * Builds a non-initialized mock {@link CrawlerContext}.
     * @return crawler context
     */
    public CrawlerContext crawlerContext() {
        var cfg = resolvedConfig();
        return new CrawlerContext(
                ClassUtil.newInstance(specProviderClass).get(),
                cfg,
                cfg.getGridConnector()
                        .connect(cfg.getWorkDir().resolve("grid")));
    }

    private CrawlerConfig resolvedConfig() {
        var cfg = config != null
                ? StubCrawlerConfig.toMemoryCrawlerConfig(workDir, config)
                : StubCrawlerConfig.memoryCrawlerConfig(workDir);
        if (configModifier != null) {
            configModifier.accept(cfg);
        }
        return cfg;
    }
}
