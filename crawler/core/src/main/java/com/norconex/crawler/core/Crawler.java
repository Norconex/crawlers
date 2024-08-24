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

import static com.norconex.crawler.core.CrawlerCommandExecuter.executeCommand;

import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.CrawlerCommandExecuter.CommandExecution;
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
import com.norconex.crawler.core.state.CrawlerState;
import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.DataStoreExporter;
import com.norconex.crawler.core.store.DataStoreImporter;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
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
    private final CrawlerCallbacks callbacks;
    private final DataStoreEngine dataStoreEngine;
    private final CrawlerContext context;

    //TODO really have the following ones here or make them part of one of
    // the above class?
    private final Fetcher<
            ? extends FetchRequest, ? extends FetchResponse> fetcher;
    private final Class<? extends CrawlDocContext> docContextType;
    private CrawlerState state;
    //TODO remove stopper listener when we are fully using a table?
    private CrawlerStopper stopper = new FileBasedStopper();

    //--- Set in init ---
    Path workDir;
    Path tempDir;
    CachedStreamFactory streamFactory;

    @SuppressWarnings("resource")
    Crawler(CrawlerBuilder b) {
        configuration = Objects.requireNonNull(b.configuration());
        context = b.context();
        docContextType = b.docContextType();
        callbacks = b.callbacks();
        dataStoreEngine = configuration.getDataStoreEngine();
        var eventManager = new EventManager(b.eventManager());
        var trackerService = new DocTrackerService(this, docContextType);
        var monitor = new CrawlerMonitor(trackerService, eventManager);
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

    //--- Commands -------------------------------------------------------------

    public void start() {
        executeCommand(new CommandExecution(this, "RUN")
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
        executeCommand(new CommandExecution(this, "STORE_EXPORT")
            .failableCommand(
                    () -> DataStoreExporter.exportDataStore(this, exportDir))
            .lock(true)
            .logIntro(true));
    }

    public void importDataStore(Path file) {
        executeCommand(new CommandExecution(this, "STORE_IMPORT")
            .failableCommand(
                    () -> DataStoreImporter.importDataStore(this, file))
            .lock(true)
            .logIntro(true));
    }

    /**
     * Cleans the crawler cache information, leading to the next run
     * being as if the crawler was run for the first time.
     */
    public void clean() {
        executeCommand(new CommandExecution(this, "CLEAN")
            .failableCommand(() -> {
                getServices().getCommitterService().clean();
                dataStoreEngine.clean();
                FileUtils.deleteDirectory(getWorkDir().toFile());
            })
            .lock(true)
            .logIntro(true));
    }
}
