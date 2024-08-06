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

import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.session.CrawlSessionImpl;
import com.norconex.crawler.fs.crawler.impl.FsCrawlerImplFactory;

public class FsCrawlSession {

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
        return CliLauncher.launch(
                initCrawlSessionImpl(new CrawlSessionConfig()),
                args);
    }

    public static CrawlSession createSession(CrawlSessionConfig sessionConfig) {
        return new CrawlSession(initCrawlSessionImpl(
                Optional.ofNullable(sessionConfig)
                    .orElseGet(CrawlSessionConfig::new)));
    }

    // Return same builder, for chaining
    static CrawlSessionImpl initCrawlSessionImpl(
            CrawlSessionConfig sessionConfig) {
        return CrawlSessionImpl
                .builder()
                .crawlerConfigClass(CrawlerConfig.class)
                .crawlerFactory(
                    (sess, cfg) -> Crawler.builder()
                        .crawlSession(sess)
                        .crawlerConfig(cfg)
                        .crawlerImpl(FsCrawlerImplFactory.create())
                        .build()
                )
                .crawlSessionConfig(sessionConfig)
                .build();
    }
}
