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
package com.norconex.crawler.core.stubs;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.MemoryCrawlerBuilderFactory;

public final class CrawlerStubs {

    public static final String CRAWLER_ID = CrawlerConfigStubs.CRAWLER_ID;

    private CrawlerStubs() {
    }

    public static Crawler crawler(Path workDir, CrawlerConfig config) {
        return Crawler.create(MemoryCrawlerBuilderFactory.class, b -> {
            config.setWorkDir(workDir);
            b.configuration(config);
        });
    }

    public static Crawler memoryCrawler(Path workDir) {
        return memoryCrawler(workDir, null);
    }

    public static Crawler memoryCrawler(
            Path workDir, Consumer<CrawlerConfig> c) {
        var crawlerConfig = CrawlerConfigStubs.memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(crawlerConfig);
        }

        return Crawler.create(MemoryCrawlerBuilderFactory.class,
                b -> b.configuration(crawlerConfig));

        //        return Crawler.create(MemoryCrawlerBuilderFactory.class, crawlerConfig);
    }
    //
    //    public static Crawler memoryCrawler(Path workDir) {
    //        return memoryCrawlerBuilder(workDir).build();
    //    }
    //
    //    public static Crawler memoryCrawler(
    //            Path workDir, Consumer<CrawlerConfig> c) {
    //        return memoryCrawlerBuilder(workDir, c).build();
    //    }
    //
    //    public static CrawlerBuilder memoryCrawlerBuilder(Path workDir) {
    //        return memoryCrawlerBuilder(workDir, null);
    //    }
    //
    //    public static CrawlerBuilder memoryCrawlerBuilder(
    //            Path workDir, Consumer<CrawlerConfig> c) {
    //        var b = Crawler
    //                .builder()
    //                .configuration(CrawlerConfigStubs.memoryCrawlerConfig(workDir))
    //                .fetcherProvider(crawler -> new MockFetcher())
    //                .docPipelines(PipelineStubs.pipelines());
    //        if (c != null) {
    //            c.accept(b.configuration());
    //        }
    //        return b;
    //    }
}
