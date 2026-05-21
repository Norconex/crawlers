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

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerDriver;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.context.CrawlerContextFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CrawlerSessionFactory {

    private CrawlerSessionFactory() {
    }

    public static CrawlerSession create(
            CrawlerDriver crawlDriver, CrawlerConfig crawlConfig) {
        var cluster = crawlConfig.getClusterConfig().getConnector().connect();
        var ctx = CrawlerContextFactory.builder() //NOSONAR
                .config(crawlConfig)
                .driver(crawlDriver)
                .build()
                .create();

        createDir(ctx.getTempDir()); // also creates workDir
        var session = new CrawlerSession(cluster, ctx);
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
