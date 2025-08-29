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
package com.norconex.crawler.core2.mocks.crawler;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core2.stubs.CrawlerConfigStubber;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
public class MockCrawlerBuilder {

    public static final String CRAWLER_ID = CrawlerConfigStubber.CRAWLER_ID;

    //NOTE: we could just assume that the workDir is set in config already,
    // but we want to enforce passing one when testing.
    private final Path workDir;

    /**
     * Crawler specification provider. Defaults to
     * {@link MockCrawlDriverFactory}.
     */
    @NonNull
    private CrawlDriver crawlDriver = MockCrawlDriverFactory.create();

    /**
     * Crawler configuration. Creates a default one if not provided.
     */
    private CrawlConfig config;

    /**
     * Crawler configuration modifier.
     */
    private Consumer<CrawlConfig> configModifier;

    public MockCrawlerBuilder(Path workDir) {
        this.workDir = workDir;
    }

    /**
     * Builds a mock {@link Crawler}.
     * @return crawler
     */
    public Crawler build() {
        return new Crawler(crawlDriver, resolvedConfig());
    }

    //    /**
    //     * Builds a non-initialized mock {@link CrawlContext}, except for the
    //     * grid, which is initialized. Closes the context after the consumable
    //     * has ran.
    //     * @param <T> type of returned object
    //     * @param f function taking consumer context and returning any value back
    //     * @return any object
    //     */
    //    @SneakyThrows
    //    public <T> T withCrawlContext(
    //            FailableFunction<CrawlContext, T, Throwable> f) {
    //        // FAULTY.... created but not always closed
    //        var cfg = resolvedConfig();
    //        var returnValue = new AtomicReference<T>();
    //        new CrawlSessionManager_DELETE(crawlDriver, cfg)
    //                .withCrawlContext(ctx -> {
    //                    try {
    //                        returnValue.set(f.apply(ctx));
    //                    } catch (Throwable e) {
    //                        fail("Test failed.", e);
    //                    }
    //                });
    //        return returnValue.get();
    //    }

    private CrawlConfig resolvedConfig() {
        var cfg = config != null
                ? CrawlerConfigStubber.toMemoryCrawlerConfig(workDir, config)
                : CrawlerConfigStubber.memoryCrawlerConfig(workDir);

        //TODO test with other grids
        //cfg.setGridConnector(new CoreGridConnector(new MockStorage()));

        if (configModifier != null) {
            configModifier.accept(cfg);
        }
        return cfg;
    }
}
