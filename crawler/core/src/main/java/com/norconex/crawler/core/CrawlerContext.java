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
import com.norconex.crawler.core.commands.crawl.CrawlStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.DedupService;
import com.norconex.crawler.core.commands.crawl.task.pipelines.DocPipelines;
import com.norconex.crawler.core.commands.crawl.task.process.DocProcessingLedger;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.util.ConfigUtil;
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

    private static final Map<String, CrawlerContext> INSTANCES =
            new HashMap<>();

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private static final String KEY_RESUMING = "resuming";
    private static final String KEY_STOPPING = "stopping";
    private static final String KEY_QUEUE_INITIALIZED = "queueInitialized";

    // -- NOTE on server nodes ---
    // Some of the following fields are only used on server nodes when on a
    // grid. They will be initialized on client even if not used... given
    // on non-grid they are needed on client as well (i.e., there are no
    // distinctions) and implementers may need them on client extensions,
    // the benefits outweighs the drawbacks vs splitting them
    // into two context classes that overlap much.

    //--- Set on declaration ---
    private final DedupService dedupService = new DedupService();

    //--- Set in constructor ---
    private final CrawlerConfig configuration;
    private final CrawlerSpec spec;

    private final BeanMapper beanMapper;
    private final Class<? extends CrawlDocContext> docContextType;
    private final EventManager eventManager;
    private final CrawlerCallbacks callbacks;

    //TODO do we really need to make the committer service a java generic?
    private CommitterService<CrawlDoc> committerService;
    private Importer importer;

    private final DocPipelines docPipelines;
    private final Fetcher<? extends FetchRequest,
            ? extends FetchResponse> fetcher;

    //--- Set in init ---
    private Grid grid;
    private Path workDir;
    private Path tempDir;
    private CachedStreamFactory streamFactory;
    private DocProcessingLedger docProcessingLedger;
    private CrawlerMetrics metrics;
    private GridCache<Boolean> stateCache;

    @SuppressWarnings("resource")
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
        docPipelines = spec.docPipelines();
    }

    public void init() {
        // NOTE: order matters

        if (INSTANCES.putIfAbsent(grid.nodeId(), this) != null) {
            throw new IllegalStateException(
                    "A crawler context was already created for this "
                            + "local grid node.");
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

        stateCache = grid.storage().getCache(
                CrawlerContext.class.getSimpleName(), Boolean.class);
        grid.compute().runLocalOnce(CrawlerContext.class.getSimpleName(),
                () -> {
                    stateCache.clear();
                    return null;
                });

        docProcessingLedger = new DocProcessingLedger();
        metrics = new CrawlerMetrics();

        docProcessingLedger.init(this);

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
        INSTANCES.remove(grid.nodeId());
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
     * Gets the last registered crawl stage.
     * @return crawl stage
     */
    public CrawlStage getCrawlStage() {
        return CrawlStage.of(grid
                .storage()
                .getGlobalCache()
                .get(CrawlStage.class.getSimpleName()));
    }

    /**
     * Whether a request was made to stop the crawler.
     * @return <code>true</code> if stopping the crawler was requested
     */
    public boolean isStopping() {
        return getState(KEY_STOPPING);
    }

    public void stop() {
        LOG.info("Received request to stop the crawler.");
        setState(KEY_STOPPING, true);
    }

    /**
     * Whether the crawler has ended (done or stopped). Same
     * as invoking <code>getCrawlStage() == CrawlStage.END</code>.
     * @return <code>true</code> if the crawler has ended
     */
    public boolean isEnded() {
        return getCrawlStage() == CrawlStage.ENDED;
    }

    public boolean isResuming() {
        return getState(KEY_RESUMING);
    }

    public void resuming() {
        setState(KEY_RESUMING, true);
    }

    public boolean isQueueInitialized() {
        return getState(KEY_QUEUE_INITIALIZED);
    }

    public void queueInitialized() {
        setState(KEY_QUEUE_INITIALIZED, true);
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
    public void setState(String key, boolean value) {
        if (stateCache == null) {
            throw new IllegalStateException("Crawler context not initialized.");
        }
        stateCache.put(key, value);
    }

    /**
     * Gets the state value matching the key.
     * @param key state key
     * @return state value
     */
    public boolean getState(String key) {
        if (stateCache == null) {
            throw new IllegalStateException("Crawler context not initialized.");
        }
        return Boolean.TRUE.equals(stateCache.get(key));
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
}
