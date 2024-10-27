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

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;
import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.crawler.core.tasks.crawl.pipelines.DedupService;
import com.norconex.crawler.core.tasks.crawl.pipelines.DocPipelines;
import com.norconex.crawler.core.tasks.crawl.process.DocProcessingLedger;
import com.norconex.crawler.core.util.ExceptionSwallower;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Crawler class holding state properties required for running commands and
 * tasks.
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
 */
@EqualsAndHashCode
@Getter
@Slf4j
public class CrawlerContext implements Closeable {

    private static final InheritableThreadLocal<CrawlerContext> INSTANCE =
            new InheritableThreadLocal<>();

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    // -- NOTE on server nodes ---
    // Some of the following fields are only used on server nodes when on a
    // grid. They will be initialized on client even if not used... given
    // on non-grid they are needed on client as well (i.e., there are no
    // distinctions) and implementers may need them on client extensions,
    // the benefits outweighs the drawbacks vs splitting them
    // into two context classes that overlap much.

    //--- Set on declaration ---
    private final CrawlerStopper stopper = new FileBasedStopper(); // server only?
    private final DedupService dedupService = new DedupService(); // server only?

    //--- Set in constructor ---
    private final CrawlerConfig configuration;
    private final Class<? extends CrawlerSpecProvider> specProviderClass;//Need?
    private final CrawlerSpec spec;//Need?

    private final BeanMapper beanMapper;
    private final Class<? extends CrawlDocContext> docContextType;
    private final EventManager eventManager;
    private final CrawlerCallbacks callbacks;

    //TODO have queue services? that way it will match pipelines?
    //TODO do we really need to make the committer service a java generic?
    private CommitterService<CrawlDoc> committerService;
    private Importer importer;

    // TODO really have the following ones here or make them part of one of
    // the super class?
    private final DocPipelines docPipelines; // server only?
    private final CrawlerSessionAttributes attributes; // server only?
    private final Fetcher<? extends FetchRequest,
            ? extends FetchResponse> fetcher; // server only?
    // TODO remove stopper listener when we are fully using an accessible store?

    //--- Set in init ---
    private Grid grid;
    private Path workDir;
    private Path tempDir;
    private CachedStreamFactory streamFactory;
    private DocProcessingLedger docProcessingLedger;
    private CrawlerState state;
    private CrawlerMetrics metrics;

    private boolean initialized;

    @SuppressWarnings("resource")
    public CrawlerContext(
            @NonNull Class<? extends CrawlerSpecProvider> specProviderClass,
            CrawlerConfig crawlerConfig) {
        spec = ClassUtil.newInstance(specProviderClass).get();
        configuration = ofNullable(crawlerConfig).orElseGet(
                () -> ClassUtil.newInstance(spec.crawlerConfigClass()));
        beanMapper = spec.beanMapper();
        this.specProviderClass = specProviderClass;
        docContextType = spec.docContextType();
        callbacks = spec.callbacks();
        eventManager =
                ofNullable(spec.eventManager()).orElse(new EventManager());

        attributes = spec.attributes();
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
                configuration.getImporterConfig(), eventManager);
        fetcher = spec.fetcherProvider().apply(this);
        docPipelines = spec.docPipelines();
        INSTANCE.set(this);
    }

    public void init() {
        // if this instance is already initialized, do nothing.
        if (initialized) {
            return;
        }
        initialized = true; // important to flag it early

        getEventManager().addListenersFromScan(getConfiguration());

        fire(CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN);

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

        docProcessingLedger = new DocProcessingLedger();
        state = new CrawlerState();
        metrics = new CrawlerMetrics();

        // NOTE: order matters
        grid = configuration.getGridConnector().connect(this);
        state.init(this);
        docProcessingLedger.init(this);

        //--- Ensure good state/config ---
        if (StringUtils.isBlank(getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
        LogUtil.setMdcCrawlerId(getId());
        Thread.currentThread().setName(getId());

        metrics.init(this);

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(this);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }

        committerService.init(
                CommitterContext
                        .builder()
                        .setEventManager(eventManager)
                        .setWorkDir(workDir.resolve("committer"))
                        .setStreamFactory(streamFactory)
                        .build());

        dedupService.init(this);

        getStopper().listenForStopRequest(this);

        fire(CrawlerEvent.CRAWLER_CONTEXT_INIT_END);
    }

    @Override
    public void close() {
        fire(CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN);

        // Defer shutdown
        swallow(() -> Optional.ofNullable(
                configuration.getDeferredShutdownDuration())
                .filter(d -> d.toMillis() > 0)
                .ifPresent(d -> {
                    LOG.info("Deferred shutdown requested. Pausing for {} "
                            + "starting from this UTC moment: {}",
                            DurationFormatter.FULL.format(d),
                            LocalDateTime.now(ZoneOffset.UTC));
                    Sleeper.sleepMillis(d.toMillis());
                    LOG.info("Shutdown resumed.");
                }));

        ExceptionSwallower.close(
                committerService,
                dedupService);

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            swallow(() -> CrawlerMetricsJMX.unregister(this));
        }

        swallow(MDC::clear);
        ExceptionSwallower.close(metrics::close);
        swallow(eventManager::clearListeners);
        swallow(() -> {
            if (tempDir != null) {
                FileUtil.delete(tempDir.toFile());
            }
        }, "Could not delete the temporary directory:" + tempDir);

        swallow(() -> state.setTerminatedProperly(true));
        swallow(() -> getStopper().destroy());

        initialized = false;
        fire(CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END);
        ExceptionSwallower.close(grid);
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

    //    public boolean isClient() {
    //        return true;
    //    }

    /**
     * Gets the instance of the initialized crawler associated with the current
     * thread. If the crawler was not yet initialized, an
     * {@link IllegalStateException} is thrown.
     *
     * @return a crawler instance
     */
    public static CrawlerContext get() {
        var cs = INSTANCE.get();
        if (cs == null) {
            throw new IllegalStateException("Crawler is not initialized.");
        }
        return cs;
    }

    public void stop() {
        //TODO implement me
        //TODO still needed?
        //throw new UnsupportedOperationException("Not yet implemented.");
    }

    //TODO document they do not cross over nodes
    public void fire(Event event) {
        getEventManager().fire(event);
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

    public String getId() {
        return getConfiguration().getId();
    }

    @Override
    public String toString() {
        return getId();
    }
}
