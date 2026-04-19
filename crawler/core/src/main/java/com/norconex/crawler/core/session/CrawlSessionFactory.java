/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.context.CrawlContextFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CrawlSessionFactory {

    private CrawlSessionFactory() {
    }

    public static CrawlSession create(
            CrawlDriver crawlDriver, CrawlConfig crawlConfig) {
        var cluster = crawlConfig.getClusterConfig().getConnector().connect();
        var ctx = CrawlContextFactory.builder() //NOSONAR
                .config(crawlConfig)
                .driver(crawlDriver)
                .build()
                .create();

        createDir(ctx.getTempDir()); // also creates workDir
        var session = new CrawlSession(cluster, ctx);
        session.init();
        return session;
    }

    private static void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: " + dir, e);
        }
    }
}
