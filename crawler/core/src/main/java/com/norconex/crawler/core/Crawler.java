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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.DocPipelines;
import com.norconex.crawler.core.doc.process.DocsProcessor;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.services.CrawlerProgressLogger;
import com.norconex.crawler.core.services.CrawlerServices;
import com.norconex.crawler.core.services.DocTrackerService;
import com.norconex.crawler.core.services.monitor.CrawlerMonitor;
import com.norconex.crawler.core.services.monitor.CrawlerMonitorJMX;
import com.norconex.crawler.core.state.CrawlerState;
import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.DataStoreExporter;
import com.norconex.crawler.core.store.DataStoreImporter;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;


/**
 * <p>Crawler base class.</p>
 *
 * <h3>JMX Support</h3>
 * <p>JMX support is disabled by default.  To enable it,
 * set the system property "enableJMX" to <code>true</code>.  You can do so
 * by adding this to your Java launch command:
 * </p>
 * <pre>
 *     -DenableJMX=true
 * </pre>
 *
 * @see CrawlerConfig
 */
@Slf4j
@EqualsAndHashCode //(onlyExplicitlyIncluded = true)
@Getter
public class Crawler {

    private static final InheritableThreadLocal<Crawler> INSTANCE =
            new InheritableThreadLocal<>();


    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    //--- Set in constructor ---
    private final CrawlerConfig configuration;
    private final CrawlerServices services;
    private final DocPipelines docPipelines;
//    private final CrawlerCommands commands;
    private final CrawlerCallbacks callbacks;
    private final DataStoreEngine dataStoreEngine;
    //TODO really have the following ones here or make them part of one of
    // the above class?
    //TODO consider if we want to merge FS Crawler here
    private final Fetcher<
            ? extends FetchRequest, ? extends FetchResponse> fetcher;
    private final Class<CrawlDocContext> docContextType;
    private CrawlerState state;
    //TODO remove stopper listener when we are fully using a table?
    private CrawlerStopper stopper = new FileBasedStopper();

    //--- Set in init ---
    private Path workDir;
    private Path tempDir;
    private CachedStreamFactory streamFactory;

    @SuppressWarnings("resource")
    Crawler(CrawlerBuilder b) {
        configuration = Objects.requireNonNull(b.configuration());
        docContextType = b.docContextType();
        callbacks = b.callbacks();
        dataStoreEngine = configuration.getDataStoreEngine();
        var eventManager = new EventManager(b.eventManager());
        var trackerService = new DocTrackerService(this, docContextType);
        var monitor = new CrawlerMonitor(trackerService, eventManager);
//        commands = b.commands();
        services = CrawlerServices
                .builder()
                .beanMapper(b.beanMapper())
                .eventManager(eventManager)
                .committerService(
                        CommitterService
                        .<CrawlDoc>builder()
                        .committers(configuration.getCommitters())
                        .eventManager(eventManager)
                        .upsertRequestBuilder(doc -> new UpsertRequest(
                                doc.getReference(),
                                doc.getMetadata(),
                                doc.getInputStream())) // Closed by caller
                        .deleteRequestBuilder(doc -> new DeleteRequest(
                                doc.getReference(),
                                doc.getMetadata()))
                        .build())
                .docTrackerService(trackerService)
                .importer(new Importer(
                        configuration.getImporterConfig(),
                        eventManager))
                .monitor(monitor)
                .progressLogger(new CrawlerProgressLogger(
                        monitor,
                        configuration.getMinProgressLoggingInterval()))
                .build();
        fetcher = b.fetcherProvider().apply(this);
        docPipelines = b.docPipelines();
        state = new CrawlerState(this);
    }

    public static CrawlerBuilder builder() {
        return new CrawlerBuilder();
    }

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
     * Gets the instance of the initialized crawler associated with the
     * current thread. If the crawler was not yet initialized, an
     * {@link IllegalStateException} is thrown.
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
        services.getEventManager().fire(event);
    }
    public void fire(String eventName) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .build());
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

    public void start() {
        executeCommand(new CommandExecution("RUN")
                .command(new DocsProcessor(this))
                .lock(true)
                .logIntro(true));
    }

    public void stop() {
        if (!getState().isStopped() && !getState().isStopping()
                && getState().isExecutionLocked()) {
            fire(CrawlerEvent.CRAWLER_STOP_BEGIN);
            getState().setStopping(true);
            LOG.info("Stopping the crawler.");
        } else {
            LOG.info("CANNOT STOP: the targetted crawler does not appear "
                    + "to be running on on this host.");
        }
    }

    public void exportDataStore(Path exportDir) {
        executeCommand(new CommandExecution("STORE_EXPORT")
            .command(() -> {
                try {
                    DataStoreExporter.exportDataStore(this, exportDir);
                } catch (IOException e) {
                    throw new CrawlerException(
                            "Could not export the crawler data store.", e);
                }
            })
            .lock(true)
            .logIntro(true));
    }

    public void importDataStore(Path file) {
        executeCommand(new CommandExecution("STORE_IMPORT")
            .command(() -> {
                try {
                    DataStoreImporter.importDataStore(this, file);
                } catch (IOException e) {
                    throw new CrawlerException(
                            "Could not import the crawler data store.", e);
                }
            })
            .lock(true)
            .logIntro(true));
    }

    /**
     * Cleans the crawler cache information, leading to the next run
     * being as if the crawler was run for the first time.
     */
    public void clean() {
        executeCommand(new CommandExecution("CLEAN")
            .command(() -> {
                try {
                    getServices().getCommitterService().clean();
                    dataStoreEngine.clean();
                    FileUtils.deleteDirectory(getWorkDir().toFile());
                } catch (IOException e) {
                    throw new CrawlerException(
                            "Could not clean the crawler directory.", e);
                }
            })
            .lock(true)
            .logIntro(true));
    }

    private void executeCommand(CommandExecution execution) {
        try {
            init(execution);
            execution.command.run();
        } finally {
            orderlyShutdown(execution);
        }
    }


    void init(CommandExecution execution) {
        //--- Ensure good state/config ---
        if (StringUtils.isBlank(configuration.getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
        LogUtil.setMdcCrawlerId(getId());
        Thread.currentThread().setName(getId() + "/" + execution.name);

        services.getEventManager().addListenersFromScan(configuration);


        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        workDir = ofNullable(configuration.getWorkDir())
                .orElseGet(() -> CrawlerConfig.DEFAULT_WORKDIR)
                .resolve(FileUtil.toSafeFileName(configuration.getId()));
        // Will also create workdir parent:
        tempDir = workDir.resolve("temp");
        try {
            // Will also create workdir parent:
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: "
                    + tempDir, e);
        }
        streamFactory = new CachedStreamFactory(
                (int) configuration.getMaxStreamCachePoolSize(),
                (int) configuration.getMaxStreamCacheSize(),
                tempDir);

        if (execution.logIntro) {
            LogUtil.logCommandIntro(LOG, this);
        }
        state.init(execution.lock);


        dataStoreEngine.init(this);

        // clear if we want to have the crawler resetable (to rerun
        // the same instance)...
        //services.getEventManager().clearListeners();
        services.init(this);
//        services.getEventManager().addListenersFromScan(configuration);

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMonitorJMX.register(this);
        }

        stopper.listenForStopRequest(this);

        fire(CrawlerEvent.CRAWLER_INIT_END);
        fire(eventBeginName(execution));
    }

    //TODO move this to a util "CrawlerShutdown" class?
    void orderlyShutdown(CommandExecution execution) {
        fire(eventEndName(execution));
        fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        try {
            // Defer shutdown
            ofNullable(configuration.getDeferredShutdownDuration())
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
            if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
                LOG.info("Unregistering JMX crawler MBeans.");
                CrawlerMonitorJMX.unregister(this);
            }
            dataStoreEngine.close();
            services.close();
        } finally {
            // close state last
            if (tempDir != null) {
                try {
                    FileUtil.delete(tempDir.toFile());
                } catch (IOException e) {
                    throw new CrawlerException(
                            "Could not cleanly shutdown the crawler.", e);
                }
            }
            MDC.clear();
            state.setTerminatedProperly(true);
            state.close();
            stopper.destroy();
            fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
            services.getEventManager().clearListeners();


            //TODO close event manager at the very last here.
        }
    }

    private String eventBeginName(CommandExecution execution) {
        return "CRAWLER_%s_BEGIN".formatted(execution.name);
    }
    private String eventEndName(CommandExecution execution) {
        return "CRAWLER_%s_END".formatted(execution.name);
    }



    //--- Inner classes --------------------------------------------------------

    @Accessors(fluent = true)
    @Setter
    @RequiredArgsConstructor
    static class CommandExecution {
        private final String name;
        private boolean logIntro;
        // We only allow locking if the crawl state is initialized
        private boolean lock;
        private Runnable command;
    }

}



//MAYBE Merge FS + web crawler.  Have a "DynamicFetcher" that would detect which
// one to use based on URI protocol.
