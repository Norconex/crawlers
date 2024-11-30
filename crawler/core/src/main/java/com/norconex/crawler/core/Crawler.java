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

import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.services.CleanService;
import com.norconex.crawler.core.services.StopService;
import com.norconex.crawler.core.services.StoreExportService;
import com.norconex.crawler.core.services.StoreImportService;
import com.norconex.crawler.core.services.crawl.CrawlService;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler. Facade to crawl-related commands.
 * </p>
 * <h3>JMX Support</h3>
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

    public void crawl() {
        executeService(CrawlService.class, null);
    }

    public void stop() {
        executeService(StopService.class, null);
    }

    public void clean() {
        executeService(CleanService.class, null);
    }

    public void storageExport(Path dir, boolean pretty) {
        executeService(
                StoreExportService.class,
                SerialUtil.toJsonString(new StoreExportService.Args(
                        dir.toAbsolutePath().toString(),
                        pretty)));
    }

    public void storageImport(Path inFile) {
        executeService(
                StoreImportService.class, inFile.toAbsolutePath().toString());
    }

    protected void executeService(
            Class<? extends GridService> gridServiceClass, String arg) {

        var serviceName = gridServiceClass.getSimpleName();
        LOG.info("Executing service: {}", serviceName);
        var grid = crawlerConfig
                .getGridConnector()
                .connect(crawlerSpecProviderClass, crawlerConfig);

        // Block until main services is done
        ConcurrentUtil.block(
                grid.services().start(serviceName, gridServiceClass, arg));

        //        // trying...
        //MAYBE? Instead pass a "terminating" flag to start? That way
        // in this case it will be the singleton service that will
        // invoke shutdown as opposed to every nodes.

        //        if (!StopService.class.isAssignableFrom(gridServiceClass)) {
        LOG.info("Shutting down the crawler...");
        ConcurrentUtil.block(grid.shutdown());
        //        }

        // Despite blocking... it only blocks sending the command, not until
        // it is shut down...
        LOG.info("Crawler shudown complete.");

    }
}
