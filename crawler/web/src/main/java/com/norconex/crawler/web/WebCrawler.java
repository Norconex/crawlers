/* Copyright 2023-2024 Norconex Inc.
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

import java.util.Optional;
import java.util.function.Supplier;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.web.callbacks.WebCrawlerCallbacks;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.pipelines.WebDocPipelines;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;

/**
 * Facade for launching or obtaining a Web Crawler.
 */
public final class WebCrawler {

    private static final Supplier<CrawlerBuilder> crawlerBuilderSupplier =
            () -> Crawler
                    .builder()
                    .configuration(new WebCrawlerConfig())
                    .fetcherProvider(new HttpFetcherProvider())
                    .callbacks(WebCrawlerCallbacks.get())
                    .docPipelines(WebDocPipelines.get())
                    .docContextType(WebCrawlDocContext.class)
                    .context(new WebCrawlerContext());

    private WebCrawler() {
    }

    /**
     * Invokes the Web Crawler from the command line.
     * You can invoke it without any arguments to get a list of command-line
     * options.
     * @param args command-line options
     */
    public static void main(String[] args) {
        try {
            System.exit(launch(args));
        } catch (Exception e) {
            e.printStackTrace(System.err); //NOSONAR
            System.exit(1);
        }
    }

    /**
     * Launches the Web Crawler. Similar to {@link #main(String[])}, but
     * do not call {@link System#exit(int)} and returns the execution status
     * code instead. It will throw a runtime exception upon failure
     * (typically a {@link CrawlerException}).
     * @param args command line arguments
     * @return execution status code
     */
    public static int launch(String... args) {
        return CliCrawlerLauncher.launch(builder(), args);
    }

    /**
     * Creates a Web Crawler instance.
     * @param crawlerConfig Web Crawler configuration
     * @return crawler
     */
    public static Crawler create(WebCrawlerConfig crawlerConfig) {
        return builder()
                .configuration(Optional.ofNullable(crawlerConfig)
                        .orElseGet(WebCrawlerConfig::new))
                .build();
    }

    /**
     * Gets the builder used to create a Web Crawler. To get a web crawler
     * instance, it is best to call {@link #create(WebCrawlerConfig)}.
     * This method is typically for internal use, unless you know what you are
     * doing and want to create your own crawler, based on this one.
     * @return crawler builder
     */
    public static CrawlerBuilder builder() {
        return crawlerBuilderSupplier.get();
    }
}
