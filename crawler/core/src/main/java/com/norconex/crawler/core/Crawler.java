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
package com.norconex.crawler.core;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.clean.CleanCommand;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.cmd.storeexport.StoreExportCommand;
import com.norconex.crawler.core.cmd.storeimport.StoreImportCommand;
import com.norconex.crawler.core.session.CrawlSessionManager;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.grid.core.BaseGridContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler. Facade to crawl-related commands.
 * </p>
 * <h2>JMX Support</h2>
 * <p>
 * JMX support is disabled by default. To enable it, set the system property
 * "enableJMX" to <code>true</code>. You can do so by adding this to your Java
 * launch command:
 * </p>
 *
 * <pre>
 *     -DenableJMX=true
 * </pre>
 *
 * @see CrawlConfig
 */
@Slf4j
@EqualsAndHashCode
public class Crawler {

    private static final String GRID_WORKDIR_NAME = "grid";

    @Getter
    private final CrawlDriver crawlDriver;
    @Getter
    private final CrawlConfig crawlConfig;

    private final CrawlSessionManager sessionManager;

    public Crawler(CrawlDriver crawlDriver, CrawlConfig crawlConfig) {
        this.crawlDriver = crawlDriver;
        this.crawlConfig = crawlConfig;
        sessionManager = new CrawlSessionManager(crawlDriver, crawlConfig);
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
        // Since we are stopping we are not initializing
        // the context and not explicitly connecting to the grid
        var gridWorkDir = ConfigUtil
                .resolveWorkDir(crawlConfig).resolve(GRID_WORKDIR_NAME);
        crawlConfig.getGridConnector().shutdownGrid(
                new BaseGridContext(gridWorkDir));
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
        sessionManager.withCrawlContext(ctx -> {
            try {
                ofNullable(ctx.getCallbacks()
                        .getBeforeCommand())
                                .ifPresent(c -> c.accept(ctx));
                command.execute(ctx);
            } finally {
                ofNullable(ctx.getCallbacks()
                        .getAfterCommand())
                                .ifPresent(c -> c.accept(ctx));
            }
        });
    }
}
