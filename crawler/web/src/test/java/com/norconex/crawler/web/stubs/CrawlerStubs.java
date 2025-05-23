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
package com.norconex.crawler.web.stubs;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.WebCrawler;
import com.norconex.crawler.web.WebCrawlerConfig;

public final class CrawlerStubs {

    public static final String CRAWLER_ID = CrawlerConfigStubs.CRAWLER_ID;

    private CrawlerStubs() {
    }

    //    public static Crawler memoryCrawler(Path workDir) {
    //        return memoryCrawlerBuilder(workDir).build();
    //    }

    //    public static Crawler memoryCrawler(
    //            Path workDir, Consumer<WebCrawlerConfig> c) {
    //        return memoryCrawlerBuilder(workDir, c).build();
    //    }
    //
    //    public static CrawlDriver memoryCrawlerBuilder(Path workDir) {
    //        return memoryCrawlerBuilder(workDir, null);
    //    }

    public static Crawler memoryCrawler(Path workDir) {
        return memoryCrawler(workDir, null);
    }

    public static Crawler memoryCrawler(
            Path workDir, Consumer<WebCrawlerConfig> c) {
        var webCrawlerConfig = CrawlerConfigStubs.memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(webCrawlerConfig);
        }
        //TODO wrap the memory committer to provide static access to
        // docs?
        // with threadlocal?
        return WebCrawler.create(webCrawlerConfig);
    }

    //    public static CrawlContext memoryCrawlContext(
    //            Path workDir, WebCrawlerConfig config) {
    //        var memConfig = CrawlerConfigStubs.memoryCrawlerConfig(workDir);
    //        if (config != null) {
    //            BeanUtil.copyProperties(memConfig, config);
    //        }
    //        var context = new CrawlContext(WebCrawlerSpecProvider.class, config);
    //        BeanUtil.copyProperties(context.getConfiguration(), memConfig);
    //        context.getConfiguration().setWorkDir(workDir);
    //        return context;
    //    }

    public static CrawlContext memoryCrawlerCrawlContext(Path workDir) {
        return memoryCrawlerCrawlContext(workDir, null);
    }

    public static CrawlContext memoryCrawlerCrawlContext(
            Path workDir, Consumer<WebCrawlerConfig> c) {
        var webCrawlerConfig = CrawlerConfigStubs.memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(webCrawlerConfig);
        }

        return null;
        //        return memoryCrawlContext(workDir, webCrawlerConfig);
        //        return CrawlContext.create(WebCrawlerSpecProvider.class, b -> {
        //            b.configuration(webCrawlerConfig);
        //        });
    }

    //    public static Crawler memorycrawler(Path workDir) {
    //        return memorycrawler(workDir, null);
    //    }
    //
    //    public static Crawler memorycrawler(
    //            Path workDir, Consumer<WebCrawlerConfig> c) {
    //        var webCrawlerConfig = CrawlerConfigStubs.memoryCrawlerConfig(workDir);
    //        if (c != null) {
    //            c.accept(webCrawlerConfig);
    //        }
    //        return WebCrawler.create(webCrawlerConfig);
    //    }

    //    public static CrawlDriver memoryCrawlerBuilder(
    //            Path workDir, Consumer<WebCrawlerConfig> c) {
    //
    //
    //        var b = WebCrawler
    //                .builder()
    //                .configuration(CrawlerConfigStubs.memoryCrawlerConfig(workDir));
    //        if (c != null) {
    //            c.accept((WebCrawlerConfig) b.configuration());
    //        }
    //        return b;
    //    }
}
