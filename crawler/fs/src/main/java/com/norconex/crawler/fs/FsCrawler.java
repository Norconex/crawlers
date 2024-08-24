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
package com.norconex.crawler.fs;

import java.util.Optional;
import java.util.function.Supplier;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.fs.callbacks.BeforeFsCrawlerExecution;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.doc.pipelines.FsDocPipelines;
import com.norconex.crawler.fs.fetch.FileFetcherProvider;

public class FsCrawler {

    protected static final Supplier<CrawlerBuilder> crawlerBuilderSupplier =
            () -> Crawler
            .builder()
            .fetcherProvider(new FileFetcherProvider())
            .callbacks(
                    CrawlerCallbacks
                    .builder()
                    .beforeCrawlerExecution(new BeforeFsCrawlerExecution())
                    .build())
            .docPipelines(FsDocPipelines.get())
            .docContextType(FsCrawlDocContext.class);

    /**
     * Invokes the File System Crawler from the command line.
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

    public static int launch(String... args) {
        return CliCrawlerLauncher.launch(crawlerBuilderSupplier.get(), args);
    }

    public static Crawler create(CrawlerConfig crawlerConfig) {
        return crawlerBuilderSupplier
                .get()
                .configuration(Optional.ofNullable(crawlerConfig)
                    .orElseGet(CrawlerConfig::new))
                .build();
    }
}
