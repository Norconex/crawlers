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
package com.norconex.crawler.core;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.doc.pipelines.DocPipelines;
import com.norconex.crawler.core.doc.process.DocProcessingLedger;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.monitor.CrawlerMonitor;
import com.norconex.crawler.core.monitor.CrawlerMonitorJMX;
import com.norconex.crawler.core.monitor.CrawlerProgressLogger;
import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Crawler base class.
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
@EqualsAndHashCode // (onlyExplicitlyIncluded = true)
@Getter
//TODO maybe rename CrawlerResources or CrawlerServerResources and have the CrawlerClient renamed to Crawler?
public class Crawler {

    //TODO by default start with Ignite embedded as a compute engine (data is still data store).
    // one can configure ignite as a cluster.  When doing so, the default store (ignite) uses that config.

    private static final InheritableThreadLocal<Crawler> INSTANCE =
            new InheritableThreadLocal<>();

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    // --- Set in constructor ---
    private final CrawlerConfig configuration;
    private final Class<? extends CrawlerBuilderFactory> builderFactoryClass;
    private final DocPipelines docPipelines;
    private final CrawlerCallbacks callbacks;
    private final DocProcessingLedger docProcessingLedger;
    private final CrawlerContext context;

    private EventManager eventManager;
    private BeanMapper beanMapper;
    private CrawlerMonitor monitor;
    //TODO have queue services? that way it will match pipelines?
    //TODO do we really need to make the committer service generic?
    private CommitterService<CrawlDoc> committerService;
    //TODO make general logging messages verbosity configurable
    private CrawlerProgressLogger progressLogger;
    //TODO do we really need to make the committer service generic?
    private Importer importer;

    // TODO really have the following ones here or make them part of one of
    // the above class?
    private final Fetcher<
            ? extends FetchRequest, ? extends FetchResponse> fetcher;
    private final Class<? extends CrawlDocContext> docContextType;
    private CrawlerState state = new CrawlerState();
    // TODO remove stopper listener when we are fully using an accessible store?
    private CrawlerStopper stopper = new FileBasedStopper();
    private DedupService dedupService = new DedupService();

    // --- Set in init ---
    private Grid grid;
    Path workDir; // <-- we want to keep this? use ignite store instead?
    Path tempDir; // <-- we want to keep this? use ignite store instead?
    CachedStreamFactory streamFactory;

    //TODO accept configuration object + CrawlerBuilder.class and have
    // an init method create all instance variables (or even, encapsulate
    // them in a CrawlerState object or equiv..
    // With the config in argument, create the GridSystem and
    // invoke the commands.  The grid system will initialize the crawler (call init) as needed.

    @SuppressWarnings("resource")
    Crawler(CrawlerBuilder b,
            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
        this.builderFactoryClass = builderFactoryClass;
        configuration = Objects.requireNonNull(b.configuration());
        beanMapper = b.beanMapper();
        context = b.context();
        docContextType = b.docContextType();
        callbacks = b.callbacks();
        //        grid = configuration.getGridConnector().connect(this);
        //        dataStoreEngine = configuration.getDataStoreEngine();
        eventManager = new EventManager(b.eventManager());
        //        var trackerService = new DocTrackerService(this, docContextType);
        //        var monitor = new CrawlerMonitor(trackerService, eventManager);
        docProcessingLedger = new DocProcessingLedger(this, docContextType);
        monitor = new CrawlerMonitor(docProcessingLedger, eventManager);
        committerService = CommitterService
                .<CrawlDoc>builder()
                .committers(configuration.getCommitters())
                .eventManager(eventManager)
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
                configuration.getImporterConfig(),
                eventManager);
        progressLogger = new CrawlerProgressLogger(
                monitor,
                configuration.getMinProgressLoggingInterval());
        fetcher = b.fetcherProvider().apply(this);
        docPipelines = b.docPipelines();
        //        state = new CrawlerState_REPLACEME(this);
    }

    public static Crawler create(
            @NonNull Class<CrawlerBuilderFactory> builderFactoryClass) {
        return create(builderFactoryClass, null);
    }

    public static Crawler create(
            @NonNull Class<? extends CrawlerBuilderFactory> builderFactoryClass,
            CrawlerBuilderModifier builderModifier) {
        var factory = ClassUtil.newInstance(builderFactoryClass);
        var builder = factory.create();
        if (builderModifier != null) {
            builderModifier.modify(builder);
        }
        return new Crawler(builder, builderFactoryClass);
    }
    // maybe an override with builderInitializer in addition of the factory Class?

    //    public static CrawlerBuilder builder() {
    //        return new CrawlerBuilder();
    //    }
    //
    public String getId() {
        return configuration.getId();
    }

    public CrawlDocContext newDocContext(@NonNull String reference) {
        var docContext = ClassUtil.newInstance(docContextType);
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
    public static Crawler get() {
        var cs = INSTANCE.get();
        if (cs == null) {
            throw new IllegalStateException("Crawler is not initialized.");
        }
        return cs;
    }

    public void fire(Event event) {
        eventManager.fire(event);
    }

    public void fire(String eventName) {
        fire(CrawlerEvent.builder().name(eventName).source(this).build());
    }

    public void fire(String eventName, Object subject) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .subject(subject)
                .build());
    }

    @Override
    public String toString() {
        return getId();
    }

    // --- Init/Destroy --------------------------------------------------------

    // local init...
    public void init() {
        //--- Ensure good state/config ---
        if (StringUtils.isBlank(getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
        LogUtil.setMdcCrawlerId(getId());
        Thread.currentThread().setName(getId());

        grid = configuration.getGridConnector().connect(this);

        eventManager.addListenersFromScan(configuration);

        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        // need those? // maybe append cluster node id?
        workDir = Optional.ofNullable(configuration.getWorkDir())
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
                (int) configuration.getMaxStreamCachePoolSize(),
                (int) configuration.getMaxStreamCacheSize(),
                tempDir);

        //TODO do this?
        //        if (execution.logIntro) {
        LogUtil.logCommandIntro(LOG, configuration);
        //        }

        state.init(grid);
        //TODO DO WE STILL LOCK?
        //        state.init(execution.lock);

        // DELETE THIS:
        //        crawler.getDataStoreEngine().init(crawler);

        // clear if we want to have the crawler resetable (to rerun
        // the same instance)...
        //services.getEventManager().clearListeners();
        docProcessingLedger.init();

        committerService.init(
                CommitterContext
                        .builder()
                        .setEventManager(getEventManager())
                        .setWorkDir(getWorkDir().resolve("committer"))
                        .setStreamFactory(getStreamFactory())
                        .build());

        dedupService.init(this);
        //        services.getEventManager().addListenersFromScan(configuration);

        if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
            CrawlerMonitorJMX.register(this);
        }

        getStopper().listenForStopRequest(this);

        fire(CrawlerEvent.CRAWLER_INIT_END);
    }

    public void orderlyShutdown(boolean isClientMode) {
        fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        try {
            // Defer shutdown
            Optional.ofNullable(configuration.getDeferredShutdownDuration())
                    .filter(d -> d.toMillis() > 0)
                    .ifPresent(d -> {
                        LOG.info("Deferred shutdown requested. Pausing for {} "
                                + "starting from this UTC moment: {}",
                                DurationFormatter.FULL.format(d),
                                LocalDateTime.now(ZoneOffset.UTC));
                        Sleeper.sleepMillis(d.toMillis());
                        LOG.info("Shutdown resumed.");
                    });

            // Unregister JMX crawlers
            if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
                LOG.info("Unregistering JMX crawler MBeans.");
                CrawlerMonitorJMX.unregister(this);
            }
            grid.close();
            //            getDataStoreEngine().close();
            //            if (isClientMode) {
            //                gridSystem.close();
            //            }
            //            docProcessingLedger.close();
            ofNullable(committerService).ifPresent(CommitterService::close);
            ofNullable(dedupService).ifPresent(DedupService::close);
        } finally {
            // close state last
            if (tempDir != null) {
                try {
                    FileUtil.delete(tempDir.toFile());
                } catch (IOException e) {
                    LOG.error(
                            "Could not delete the temporary directory:"
                                    + tempDir,
                            e);
                }
            }
            MDC.clear();
            getState().setTerminatedProperly(true);
            //            getState().close();
            getStopper().destroy();
            fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
            eventManager.clearListeners();
        }
        //        }, GridExecuterOptions.builder()
        //                .name("crawler-shutdown")
        //                .block(true)
        //                .singleton(execution.singleton)
        //                .build());
        //        gridExecuter.close();
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
