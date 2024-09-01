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
package com.norconex.crawler.fs.stubs;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.fs.FsCrawler;

public final class CrawlerStubs {

    public static final String CRAWLER_ID = CrawlerConfigStubs.CRAWLER_ID;

    private CrawlerStubs() {
    }

    public static Crawler memoryCrawler(Path workDir) {
        return memoryCrawlerBuilder(workDir).build();
    }

    public static Crawler memoryCrawler(
            Path workDir, Consumer<CrawlerConfig> c) {
        return memoryCrawlerBuilder(workDir, c).build();
    }

    public static CrawlerBuilder memoryCrawlerBuilder(Path workDir) {
        return memoryCrawlerBuilder(workDir, null);
    }

    public static CrawlerBuilder memoryCrawlerBuilder(
            Path workDir, Consumer<CrawlerConfig> c) {
        var b = SneakyFsCrawler
                .builder()
                .configuration(
                        CrawlerConfigStubs.memoryCrawlerConfig(workDir));
        if (c != null) {
            c.accept(b.configuration());
        }
        return b;
    }

    static class SneakyFsCrawler extends FsCrawler {
        static CrawlerBuilder builder() {
            return crawlerBuilderSupplier.get();
        }
    }
}
