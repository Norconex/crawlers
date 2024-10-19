/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.core.CrawlerBuilderModifier;
import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.CrawlerSessionAttributes;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.doc.pipelines.DocPipelines;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Crawler task base class.
 * </p>
 * @see CrawlerConfig
 */
@Slf4j
@EqualsAndHashCode
@Getter
public class CrawlerTaskContext extends CrawlerContext {

    private static final InheritableThreadLocal<CrawlerTaskContext> INSTANCE =
            new InheritableThreadLocal<>();

    // --- Set in constructor ---
    private final DocPipelines docPipelines;
    private final CrawlerCallbacks callbacks;
    private final CrawlerSessionAttributes attributes;

    //TODO have queue services? that way it will match pipelines?
    //TODO do we really need to make the committer service a java generic?
    private CommitterService<CrawlDoc> committerService;
    private Importer importer;

    // TODO really have the following ones here or make them part of one of
    // the super class?
    private final Fetcher<
            ? extends FetchRequest, ? extends FetchResponse> fetcher;
    // TODO remove stopper listener when we are fully using an accessible store?
    private CrawlerStopper stopper = new FileBasedStopper();
    private DedupService dedupService = new DedupService();

    // --- Set in init ---
    Path workDir; //TODO <-- we want to keep this? use ignite store instead?
    Path tempDir; //TODO <-- we want to keep this? use ignite store instead?
    CachedStreamFactory streamFactory;

    @SuppressWarnings("resource")
    CrawlerTaskContext(CrawlerBuilder b,
            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
        super(b, builderFactoryClass);

        attributes = b.context();
        callbacks = b.callbacks();
        committerService = CommitterService
                .<CrawlDoc>builder()
                .committers(getConfiguration().getCommitters())
                .eventManager(getEventManager())
                .upsertRequestBuilder(doc -> new UpsertRequest(
                        doc.getReference(),
                        doc.getMetadata(),
                        // InputStream closed by caller
                        doc.getInputStream()))
                .deleteRequestBuilder(doc -> new DeleteRequest(
                        doc.getReference(),
                        doc.getMetadata()))
                .build();
        importer = new Importer(
                getConfiguration().getImporterConfig(),
                getEventManager());
        fetcher = b.fetcherProvider().apply(this);
        docPipelines = b.docPipelines();
    }

    public static CrawlerTaskContext create(
            @NonNull Class<CrawlerBuilderFactory> builderFactoryClass) {
        return create(builderFactoryClass, null);
    }

    public static CrawlerTaskContext create(
            @NonNull Class<? extends CrawlerBuilderFactory> builderFactoryClass,
            CrawlerBuilderModifier builderModifier) {
        var factory = ClassUtil.newInstance(builderFactoryClass);
        var builder = factory.create();
        if (builderModifier != null) {
            builderModifier.modify(builder);
        }
        return new CrawlerTaskContext(builder, builderFactoryClass);
    }
    
    public CrawlDocContext newDocContext(@NonNull String reference) {
        var docContext = ClassUtil.newInstance(getDocContextType());
        docContext.setReference(reference);
        return docContext;
    }

    public CrawlDocContext newDocContext(@NonNull DocContext parentContext) {
        var docContext = newDocContext(parentContext.getReference());
        docContext.copyFrom(parentContext);
        return docContext;
    }

    /**
     * Gets the instance of the initialized crawler associated with the current
     * thread. If the crawler was not yet initialized, an
     * {@link IllegalStateException} is thrown.
     *
     * @return a crawler instance
     */
    public static CrawlerTaskContext get() {
        var cs = INSTANCE.get();
        if (cs == null) {
            throw new IllegalStateException("Crawler is not initialized.");
        }
        return cs;
    }

    // --- Init/Destroy --------------------------------------------------------

    // local init...
    @Override
    public void init() {
        super.init();
        //TODO differentiate between CRAWLER_* events and CRAWLER_TASK_* events?
        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        // need those? // maybe append cluster node id?
        workDir = Optional.ofNullable(getConfiguration().getWorkDir())
                .orElseGet(() -> CrawlerConfig.DEFAULT_WORKDIR)
                .resolve(FileUtil.toSafeFileName(getId()));
        tempDir = workDir.resolve("temp");
        try {
            // Will also create workdir parent:
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new CrawlerException(
                    "Could not create directory: " + tempDir, e);
        }
        streamFactory = new CachedStreamFactory(
                (int) getConfiguration().getMaxStreamCachePoolSize(),
                (int) getConfiguration().getMaxStreamCacheSize(),
                tempDir);

        //TODO DO WE STILL LOCK?
        //        state.init(execution.lock);

        committerService.init(
                CommitterContext
                        .builder()
                        .setEventManager(getEventManager())
                        .setWorkDir(getWorkDir().resolve("committer"))
                        .setStreamFactory(getStreamFactory())
                        .build());

        dedupService.init(this);

        getStopper().listenForStopRequest(this);

        fire(CrawlerEvent.CRAWLER_INIT_END);
    }

    @Override
    public void close() {
        fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        try {
            // Defer shutdown
            Optional.ofNullable(
                    getConfiguration().getDeferredShutdownDuration())
                    .filter(d -> d.toMillis() > 0)
                    .ifPresent(d -> {
                        LOG.info("Deferred shutdown requested. Pausing for {} "
                                + "starting from this UTC moment: {}",
                                DurationFormatter.FULL.format(d),
                                LocalDateTime.now(ZoneOffset.UTC));
                        Sleeper.sleepMillis(d.toMillis());
                        LOG.info("Shutdown resumed.");
                    });

            safeClose(() -> getGrid().close());
            safeClose(() -> ofNullable(committerService)
                    .ifPresent(CommitterService::close));
            safeClose(() -> ofNullable(dedupService)
                    .ifPresent(DedupService::close));
        } finally {
            // close state last
            if (tempDir != null) {
                try {
                    FileUtil.delete(tempDir.toFile());
                } catch (IOException e) {
                    LOG.error("Could not delete the temporary directory:"
                            + tempDir, e);
                }
            }
            //TODO register failure instead and block? If at least one 
            // detected on client node, it failed
            safeClose(() -> getState().setTerminatedProperly(true));
            safeClose(() -> getStopper().destroy());
            fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
        }
        super.close();
    }

    // --- Commands ------------------------------------------------------------

    //TODO are these commands still required here since we execute tasks
    // now, triggered from client?
    //
    //    public void start() {
    //        // we need finer-grained control on cluster for "start" so we don't
    //        // use the grid executer here but deeper in the code where needed.
    //
    //        // for now we have it here just as a test
    //        executeCommand(new CrawlCommand(), "RUN");
    //
    //        //
    //        //        executeCommand(new CommandExecution(this, "RUN")
    //        //                .command(() -> new DocsProcessor(this))
    //        //                // end test
    //        //                .lock(true)
    //        //                .logIntro(true));
    //
    //        //        executeCommand(new CommandExecution(this, "RUN")
    //        //                .command(() -> gridExecuter.execute(() ->
    //        //                // start test
    //        //                        new DocsProcessor(this), GridExecuterOptions.builder()
    //        //                                .name("crawler-start")
    //        //                                .block(true)
    //        //                                .build()))
    //        //                // end test
    //        //                .lock(true)
    //        //                .logIntro(true));
    //    }
    //
    public void stop() {
        //TODO set stopping state across cluster and have nodes monitor
        // for it and set their local stop state accordingly.
        //        //        gridExecuter.execute(() -> {
        //        //            if (!getState().isStopped() && !getState().isStopping()
        //        //                    && getState().isExecutionLocked()) {
        //        //                fire(CrawlerEvent.CRAWLER_STOP_BEGIN);
        //        //                getState().setStopping(true);
        //        //                LOG.info("Stopping the crawler.");
        //        //            } else {
        //        //                LOG.info("CANNOT STOP: the targetted crawler does not appear "
        //        //                        + "to be running on on this host.");
        //        //            }
        //        //        }, GridExecuterOptions.builder()
        //        //                .name("crawler-stop")
        //        //                .block(true)
        //        //                .build());
    }

    @Override
    public boolean isClient() {
        return false;
    }
    //
    //    public void exportDataStore(Path exportDir) {
    //        //        executeCommand(new CommandExecution(this, "STORE_EXPORT")
    //        //                .failableCommand(() -> gridExecuter.execute(
    //        //                        () -> DataStoreExporter.exportDataStore(
    //        //                                this, exportDir),
    //        //                        GridExecuterOptions.builder()
    //        //                                .name("crawler-export")
    //        //                                .block(true)
    //        //                                .singleton(true)
    //        //                                .build()))
    //        //                .lock(true)
    //        //                .logIntro(true)
    //        //                .singleton(true));
    //    }
    //
    //    public void importDataStore(Path file) {
    //        //        executeCommand(new CommandExecution(this, "STORE_IMPORT")
    //        //                .failableCommand(() -> gridExecuter.execute(
    //        //                        () -> DataStoreImporter.importDataStore(this, file),
    //        //                        GridExecuterOptions.builder()
    //        //                                .name("crawler-import")
    //        //                                .block(true)
    //        //                                .singleton(true)
    //        //                                .build()))
    //        //                .lock(true)
    //        //                .logIntro(true)
    //        //                .singleton(true));
    //    }
    //
    //    /**
    //     * Cleans the crawler cache information, leading to the next run being as if
    //     * the crawler was run for the first time.
    //     */
    //    public void clean() {
    //        //        executeCommand(new CommandExecution(this, "CLEAN")
    //        //                .failableCommand(() -> gridExecuter.execute(() -> {
    //        //                    getServices().getCommitterService().clean();
    //        //                    dataStoreEngine.clean();
    //        //                    FileUtils.deleteDirectory(getWorkDir().toFile());
    //        //                }, GridExecuterOptions.builder()
    //        //                        .name("crawler-clean")
    //        //                        .block(true)
    //        //                        .singleton(true)
    //        //                        .build()))
    //        //                .lock(true)
    //        //                .logIntro(true)
    //        //                .singleton(true));
    //    }
    //
    //    private void executeCommand(Command command, String commandName) {
    //        //        try (var grid = getGridSystem()) {
    //        //            grid.init(this);
    //        init(true);
    //        try {
    //            fire("CRAWLER_%s_BEGIN".formatted(commandName));
    //            command.execute(this);
    //        } finally {
    //            fire("CRAWLER_%s_END".formatted(commandName));
    //            orderlyShutdown(true);
    //        }
    //        //        }
    //    }
}
