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

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;

/**
 * Facade for launching or obtaining a Web Crawler.
 */
public final class WebCrawler {

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
        return CliCrawlerLauncher.launch(WebCrawlDriverFactory.create(), args);
    }

    /**
     * Creates a Web Crawler instance.
     * @param crawlerConfig Web Crawler configuration
     * @return crawler
     */
    public static Crawler create(WebCrawlerConfig crawlerConfig) {
        return new Crawler(WebCrawlDriverFactory.create(), crawlerConfig);
    }
}
