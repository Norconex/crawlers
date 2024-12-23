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
package com.norconex.crawler.core;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.clean.CleanCommand;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.cmd.stop.StopCommand;
import com.norconex.crawler.core.cmd.storeexport.StoreExportCommand;
import com.norconex.crawler.core.cmd.storeimport.StoreImportCommand;
import com.norconex.crawler.core.util.LogUtil;

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
 * @see CrawlerConfig
 */
@Slf4j
@EqualsAndHashCode
@Getter
public class Crawler {

    private final Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass;
    private final CrawlerConfig crawlerConfig;

    public Crawler(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {
        this.crawlerSpecProviderClass = crawlerSpecProviderClass;
        this.crawlerConfig = crawlerConfig;
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
        executeCommand(new CrawlCommand(startClean));
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

    private void validateConfig(CrawlerConfig config) {
        if (StringUtils.isBlank(config.getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
    }

    protected void executeCommand(Command command) {
        validateConfig(crawlerConfig);
        LogUtil.logCommandIntro(LOG, crawlerConfig);
        LOG.info("Executing command: {}", command.getClass().getSimpleName());

        var spec = ClassUtil.newInstance(crawlerSpecProviderClass).get();
        try (var grid = crawlerConfig
                .getGridConnector()
                .connect(crawlerSpecProviderClass, crawlerConfig)) {
            // grid auto closes
            try (var ctx = new CrawlerContext(spec, crawlerConfig, grid)) {
                ctx.init();
                command.execute(ctx);
            }
            grid.nodeStop();
        }
    }
}
