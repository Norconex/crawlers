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

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrappers;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocLedger;
import com.norconex.crawler.core.doc.CrawlDocLedgerEntry;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;
import com.norconex.crawler.core.util.LogUtil;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.pipeline.GridPipelineState;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Crawler class holding state properties required for running commands and
 * tasks.
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
 */
@EqualsAndHashCode
@Getter
@Slf4j
public class CrawlerContext implements AutoCloseable {

    private static final Map<String, CrawlerContext> INSTANCES =
            new HashMap<>();

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private static final String KEY_RESUMING = "resuming";
    private static final String KEY_INCREMENTING = "incrementing";
    private static final String KEY_STOP_CMD_REQUESTED =
            "stopCommandRequested";
    private static final String KEY_QUEUE_INITIALIZED = "queueInitialized";
    private static final String KEY_MAX_PROCESSED_DOCS = "maxProcessedDocs";
    //TODO somehow abstract/hide this:
    public static final String KEY_CRAWL_PIPELINE = "crawlPipeline";

    //--- Set on declaration ---
    private final DedupService dedupService = new DedupService();
    private final CrawlDocLedger docProcessingLedger =
            new CrawlDocLedger();
    private final CrawlerMetrics metrics = new CrawlerMetrics();

    //--- Set in constructor ---
    private final CrawlerConfig configuration;
    private final CrawlerSpec spec;

    private final BeanMapper beanMapper;
    private final Class<? extends CrawlDocLedgerEntry> docContextType;
    private final EventManager eventManager;
    private final CrawlerCallbacks callbacks;

    //TODO do we really need to make the committer service a java generic?
    private CommitterService<CrawlDoc> committerService;
    private Importer importer;

    private final CrawlBootstrappers bootstrappers;
    private final CrawlDocPipelines pipelines; //TODO rename docPipelines ................
    private final Fetcher<? extends FetchRequest,
            ? extends FetchResponse> fetcher;

    //--- Set in init ---
    private Grid grid;
    private Path workDir;
    private Path tempDir;
    private CachedStreamFactory streamFactory;
    private GridMap<Boolean> ctxStateStore; //TODO rename flagsMap?
    // for some obscure reasons, these 2 bools vanish if @Getter isn't set again
    @Getter
    private boolean resuming; // starting where it ended (before completion)
    @Getter
    private boolean incrementing; // ran before and has a cache

    public CrawlerContext(
            @NonNull CrawlerSpec crawlerSpec,
            @NonNull CrawlerConfig crawlerConfig,
            @NonNull Grid grid) {
        this.grid = grid;
        spec = crawlerSpec;
        configuration = ofNullable(crawlerConfig).orElseGet(
                () -> ClassUtil.newInstance(spec.crawlerConfigClass()));
        beanMapper = spec.beanMapper();
        docContextType = spec.docContextType();
        callbacks = spec.callbacks();
        eventManager =
                ofNullable(spec.eventManager()).orElse(new EventManager());
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
        bootstrappers = spec.bootstrappers();
        pipelines = spec.pipelines();
    }

    public void init() {
        // NOTE: order matters

        if (INSTANCES.putIfAbsent(grid.getNodeName(), this) != null) {
            throw new IllegalStateException(
                    "A crawler context was already created and initialized for "
                            + "this grid node.");
        }

        // need those? // maybe append cluster node id?
        workDir = ConfigUtil.resolveWorkDir(getConfiguration());
        tempDir = ConfigUtil.resolveTempDir(getConfiguration());
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

        eventManager.addListenersFromScan(getConfiguration());
        fire(CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN);

        docProcessingLedger.init(this);

        ctxStateStore = grid.storage().getMap(
                CrawlerContext.class.getSimpleName(), Boolean.class);

        // Here We initialize things that should only be initialized once
        // for the entire crawl sessions (i.e., only set by one node).
        var result = grid.compute().runOnOne("context-init",
                () -> {
                    //TODO encapsulate stateMap and other "generic" stores
                    // into their own object? Or a base object for them all?
                    var newSession = docProcessingLedger.isQueueEmpty();
                    if (newSession) {
                        LOG.info("Starting a new crawl session.");
                        grid.resetSession();
                    } else {
                        LOG.info("Resuming a previous crawl session.");
                    }
                    ctxStateStore.clear();
                    var props = new GridContextProps();
                    props.setResuming(!newSession);
                    props.setIncrementing(
                            !docProcessingLedger.isProcessedEmpty());
                    setState(KEY_RESUMING, props.isResuming());
                    setState(KEY_INCREMENTING, props.isIncrementing());
                    return props;
                });

        resuming = result.getValue().resuming;
        incrementing = result.getValue().incrementing;

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
        swallow(() -> {
            if (tempDir != null) {
                FileUtil.delete(tempDir.toFile());
            }
        }, "Could not delete the temporary directory:" + tempDir);

        fire(CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END);
        swallow(eventManager::clearListeners);
        INSTANCES.remove(grid.getNodeName());
    }

    public CrawlDocLedgerEntry newDocContext(@NonNull String reference) {
        var docContext = ClassUtil.newInstance(docContextType);
        docContext.setReference(reference);
        return docContext;
    }

    public CrawlDocLedgerEntry
            newDocContext(@NonNull DocContext parentContext) {
        var docContext = newDocContext(parentContext.getReference());
        docContext.copyFrom(parentContext);
        return docContext;
    }

    /**
     * Gets the last registered crawl stage.
     * @return crawl stage
     */
    //TODO needed/useful?
    public Optional<String> getCrawlStage() {
        return grid.pipeline().getActiveStageName(KEY_CRAWL_PIPELINE);
    }

    //TODO needed/useful?
    public GridPipelineState getCrawlState() {
        return grid.pipeline().getState(KEY_CRAWL_PIPELINE);
    }

    /**
     * Whether a request was made to stop the current crawler command.
     * @return <code>true</code> if stopping the crawler was requested
     */
    public boolean isStopCrawlerCommandRequested() {
        return getState(KEY_STOP_CMD_REQUESTED);
    }

    public void stopCrawlerCommand() {
        LOG.info("Received request to stop the crawler.");
        grid.pipeline().stop(null);
        grid.compute().stop(null);
        setState(KEY_STOP_CMD_REQUESTED, true);
    }

    /**
     * Whether the crawler has ended (done or stopped). Same
     * as invoking <code>getCrawlStage() == CrawlStage.END</code>.
     * @return <code>true</code> if the crawler has ended
     */
    public boolean isEnded() {
        return getCrawlState() == GridPipelineState.ENDED;
    }

    public boolean isQueueInitialized() {
        return getState(KEY_QUEUE_INITIALIZED);
    }

    public void queueInitialized() {
        setState(KEY_QUEUE_INITIALIZED, true);
    }

    //MAYBE: Consider caching this value once set by DocLedgerInitializer
    public long maxProcessedDocs() {
        var maxDocs = grid.storage()
                .getSessionAttributes().get(KEY_MAX_PROCESSED_DOCS);
        return maxDocs == null ? configuration.getMaxDocuments()
                : Long.parseLong(maxDocs);
    }

    public void maxProcessedDocs(long maxDocs) {
        grid.storage()
                .getSessionAttributes()
                .put(KEY_MAX_PROCESSED_DOCS, Long.toString(maxDocs));
    }

    public static boolean isInitialized(String nodeId) {
        return INSTANCES.get(nodeId) != null;
    }

    public static CrawlerContext get(String nodeId) {
        var ctx = INSTANCES.get(nodeId);
        if (ctx == null) {
            throw new IllegalStateException(
                    "CrawlerContext was not initialized.");
        }
        return ctx;
    }

    /**
     * Sets arbitrary state flag valid for the duration of a crawl session
     * only.
     * @param key state key
     * @param value state value
     */
    private void setState(String key, boolean value) {
        if (ctxStateStore == null) {
            throw new IllegalStateException("Crawler context not initialized.");
        }
        ctxStateStore.put(key, value);
    }

    /**
     * Gets the state value matching the key.
     * @param key state key
     * @return state value
     */
    private boolean getState(String key) {
        if (ctxStateStore == null) {
            throw new IllegalStateException("Crawler context not initialized.");
        }
        return Boolean.TRUE.equals(ctxStateStore.get(key));
    }

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

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class GridContextProps {
        boolean resuming;
        boolean incrementing;
    }
}
