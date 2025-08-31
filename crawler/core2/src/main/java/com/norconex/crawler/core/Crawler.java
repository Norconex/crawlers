/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionFactory;
import com.norconex.crawler.core2.cmd.Command;
import com.norconex.crawler.core2.cmd.clean.CleanCommand;
import com.norconex.crawler.core2.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core2.cmd.stop.StopCommand;
import com.norconex.crawler.core2.cmd.storeexport.StoreExportCommand;
import com.norconex.crawler.core2.cmd.storeimport.StoreImportCommand;
import com.norconex.crawler.core2.util.LogUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Crawler {

    @Getter
    private final CrawlDriver crawlDriver;
    @Getter
    private final CrawlConfig crawlConfig;

    public Crawler(CrawlDriver crawlDriver, CrawlConfig crawlConfig) {
        this.crawlDriver = crawlDriver;
        this.crawlConfig = crawlConfig;
    }

    /**
     * Crawl without first cleaning the crawler (i.e., incremental crawling).
     * Same as invoking <code>crawl(false)</code>.
     */
    public void crawl() {
        crawl(false);
    }

    /**
     *
     * @param startClean
     */
    public void crawl(boolean startClean) {
        if (startClean) {
            executeCommand(new CleanCommand());
        }
        executeCommand(new CrawlCommand());
    }

    public void clean() {
        executeCommand(new CleanCommand());
    }

    public void stop() {
        executeCommand(new StopCommand());
    }

    public void storageExport(Path dir, boolean pretty) {
        executeCommand(new StoreExportCommand(dir, pretty));
    }

    public void storageImport(Path inFile) {
        executeCommand(new StoreImportCommand(inFile));
    }

    private void validateConfig(CrawlConfig config) {
        if (StringUtils.isBlank(config.getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
    }

    private void executeCommand(Command command) {
        validateConfig(crawlConfig);
        LogUtil.logCommandIntro(LOG, crawlConfig);
        LOG.info("Executing command: {}", command.getClass().getSimpleName());
        withCrawlSession(sess -> {
            try {
                ofNullable(sess.getCrawlContext().getCallbacks()
                        .getBeforeCommand())
                                .ifPresent(c -> c.accept(sess));
                command.execute(sess);
            } finally {
                ofNullable(sess.getCrawlContext().getCallbacks()
                        .getAfterCommand())
                                .ifPresent(c -> c.accept(sess));
            }
        });
    }

    public void withCrawlSession(Consumer<CrawlSession> c) {
        try (var session =
                CrawlSessionFactory.create(crawlDriver, crawlConfig)) {
            c.accept(session);
        }
    }
}
