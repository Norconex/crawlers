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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.services.CleanService;
import com.norconex.crawler.core.services.crawl.CrawlService;

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

    //TODO ensure it is supported somewhere else in code
    //    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private final Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass;
    private final CrawlerConfig crawlerConfig;

    public Crawler(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {
        this.crawlerSpecProviderClass = crawlerSpecProviderClass;
        this.crawlerConfig = crawlerConfig;
    }

    public void crawl() {
        executeService(CrawlService.class);
    }

    public void stop() {
        //TODO implement me
    }

    public void clean() {
        executeService(CleanService.class);
    }

    public void storageExport(Path dir) {
        //TODO implement me
    }

    public void storageImport(Path inFile) {
        //TODO implement me
    }

    //    public static class TestTask implements GridTask {
    //        private static final long serialVersionUID = 1L;
    //
    //        @Override
    //        public void run(CrawlerContext crawlerContext, String arg) {
    //            System.err.println("XXXXXXXXXX hello world!!!!!!!");
    //        }
    //    }

    protected void executeService(
            Class<? extends GridService> gridServiceClass) {

        var serviceName = gridServiceClass.getSimpleName();
        LOG.info("Executing service: {}", serviceName);

        var grid = crawlerConfig
                .getGridConnector()
                .connect(crawlerSpecProviderClass, crawlerConfig);

        Future<?> future =
                grid.services().start(serviceName, gridServiceClass);

        System.err.println(
                "XXX Crawler class has invoked the crawl service. Blocking");

        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlerException(
                    "Could not execute crawler service.", e);
        } catch (ExecutionException e) {
            throw new CrawlerException(
                    "Could not execute crawler service.", e);
        }

        System.err.println(
                "XXX CrawlService has returned... start() is done. Was end/cancel called everywhere?");

        //TODO ???????? do we explicitly end the service?
        //        System.err.println(
        //                "XXX Crawler class has invoked the crawl service. Nothign to do. Shall we block/wait?");

        //TODO what about these? are they handled?
        //        ofNullable(context.getCallbacks().getBeforeCommand())
        //                .ifPresent(c -> c.accept(context));
        //        command.execute(context);
        //        ofNullable(context.getCallbacks().getAfterCommand())
        //                .ifPresent(c -> c.accept(context));
    }

    //    private void executeCommand_OLD(Command command) {
    //        init();
    //        try {
    //            LOG.info("Executing command: {}",
    //                    command.getClass().getSimpleName());
    //            ofNullable(context.getCallbacks().getBeforeCommand())
    //                    .ifPresent(c -> c.accept(context));
    //            command.execute(context);
    //            ofNullable(context.getCallbacks().getAfterCommand())
    //                    .ifPresent(c -> c.accept(context));
    //        } finally {
    //            close();
    //        }
    //    }
}
