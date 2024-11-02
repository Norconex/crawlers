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
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.services.crawl.CrawlService;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler client class.
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
//@RequiredArgsConstructor
public class Crawler {

    //NOTE: this is a lightweight version of the crawler that only controls
    // and report on actual crawler instances running on nodes. It has access
    // to grid system and some monitoring and that is pretty much it.

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    //    private CrawlerProgressLogger progressLogger;
    //    private final CrawlerContext context;
    //    private

    private final Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass;
    private final CrawlerConfig crawlerConfig;

    public Crawler(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {
        this.crawlerSpecProviderClass = crawlerSpecProviderClass;
        this.crawlerConfig = crawlerConfig;
    }

    //    Crawler(CrawlerSpec b,
    //            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
    //        super(b, builderFactoryClass);
    //    }
    //
    //    public static Crawler create(
    //            @NonNull Class<CrawlerBuilderFactory> builderFactoryClass) {
    //        return create(builderFactoryClass, null);
    //    }
    //
    //    public static Crawler create(
    //            @NonNull Class<? extends CrawlerBuilderFactory> builderFactoryClass,
    //            CrawlerBuilderModifier builderModifier) {
    //        var factory = ClassUtil.newInstance(builderFactoryClass);
    //        var builder = factory.create();
    //        if (builderModifier != null) {
    //            builderModifier.modify(builder);
    //        }
    //        return new Crawler(builder, builderFactoryClass);
    //    }

    public void crawl() {
        executeCommand(CrawlService.class);
    }

    public void stop() {
        //TODO implement me
    }

    public void clean() {
        //TODO implement me
    }

    public void storageExport(Path dir) {
        //TODO implement me
    }

    public void storageImport(Path inFile) {
        //TODO implement me
    }

    public static class TestTask implements GridTask {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(CrawlerContext crawlerContext, String arg) {
            System.err.println("XXXXXXXXXX hello world!!!!!!!");
        }
    }

    private void executeCommand(Class<? extends GridService> gridServiceClass) {
        //        CrawlerSpecProvider crawlerSpecProvider =
        //                ClassUtil.newInstance(crawlerSpecProviderClass);
        //------------------
        var serviceName = gridServiceClass.getSimpleName();

        try (var grid = crawlerConfig
                .getGridConnector()
                .connect(crawlerSpecProviderClass, crawlerConfig)) {

            Future<?> future =
                    grid.services().start(serviceName, gridServiceClass);

            System.err.println("XXX IS DONE? " + future.isDone());
            System.err.println("XXX IS CANCELLED? " + future.isCancelled());
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            //            ConcurrentUtil.block(
            //                    grid.services().start(serviceName, gridServiceClass));
            //            ConcurrentUtil.block(grid.compute().runOnce(
            //                    "JOB_" + TestTask.class.getName(),
            //                    () -> grid.compute().runTask(TestTask.class, null,
            //                            GridTxOptions
            //                                    .builder()
            //                                    .name("Test-Job")
            //                                    .build())));

            //TODO ???????? do we explicitly end the service?
            System.err.println("XXX I AM ALL DONE");

        }
        //------------------
        // just have the config at this point...
        // connect to the grid.
        // launch the command on the grid
        //      from within the grid compute:
        //      - execute the command
        //          - the command will invoke other tasks

        //        LOG.info("Executing command: {}",
        //                command.getClass().getSimpleName());
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
    /*
    protected void init() {
        //        context.fire(CrawlerEvent.CRAWLER_INIT_BEGIN);
        LogUtil.logCommandIntro(LOG, context.getConfiguration());
    
        context.init();
    
        progressLogger = new CrawlerProgressLogger(
                context.getMetrics(),
                context.getConfiguration()
                        .getMinProgressLoggingInterval());
        progressLogger.startTracking();
        //        context.fire(CrawlerEvent.CRAWLER_INIT_END);
    }
    
    protected void close() {
        context.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
    
        progressLogger.stopTracking();
        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());
        LOG.info("Crawler {}",
                (context.getState().isStopped() ? "stopped."
                        : "completed."));
        context.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
        context.close();
    }
    */
}
