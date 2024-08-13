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
package com.norconex.crawler.core.crawler;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.crawler.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlDocRecordFactory;
import com.norconex.crawler.core.doc.CrawlDocRecordService;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.monitor.CrawlerMonitor;
import com.norconex.crawler.core.monitor.CrawlerMonitorJMX;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionException;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.importer.Importer;
import com.norconex.importer.response.ImporterResponse;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;


/**
 * <p>Abstract crawler implementation providing a common base to building
 * crawlers.</p>
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
@SuppressWarnings("javadoc")
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Crawler {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    //--- Properties set in Constructor ----------------------------------------

    /**
     * Gets the crawl session this crawler is part of.
     * @return crawl session
     */
    @Getter
    private final CrawlSession crawlSession;

    /**
     * Gets the crawler configuration.
     * @return the crawler configuration
     */
    @Getter
    @EqualsAndHashCode.Include
    private final CrawlerConfig configuration;

    @Getter(value=AccessLevel.PACKAGE)
    private final CrawlerImpl crawlerImpl;

    /**
     * Gets the directory where files needing to be persisted between
     * crawling sessions are kept.
     * @return working directory, never <code>null</code>
     */
    @Getter
    private Path workDir;

    /**
     * Gets the directory where most temporary files are created for the
     * duration of a crawling session. Those files are typically deleted
     * after a crawling session.
     * @return temporary directory, never <code>null</code>
     */
    @Getter
    private Path tempDir;

    @Getter
    private CommitterService<CrawlDoc> committerService;


    //--- Properties set on Init -----------------------------------------------

    /**
     * Crawler implementation-specific context holding state-data not relevant
     * to Crawler Core.
     */
    @Getter
    private Object crawlerContext;

    @Getter
    private CrawlerDataStoreEngine crawlerDataStoreEngine;

    @Getter
    private CrawlDocRecordService docRecordService;

    @Getter
    private CrawlerService service;


    //--- Properties set on Start ----------------------------------------------

    /**
     * Gets the crawler Importer module.
     * @return the Importer
     */
    @Getter
    private Importer importer;

    @Getter
    private CrawlerMonitor monitor;

    @Getter
    private CrawlProgressLogger progressLogger;

    @Getter
    private Fetcher<? extends FetchRequest, ? extends FetchResponse> fetcher;

    @Getter
    private DataStore<String> dedupMetadataStore;

    @Getter
    private DataStore<String> dedupDocumentStore;

    private MutableBoolean queueInitialized;

    // Actual maximum number of docs after which to stop, which starts
    // at initial "processed count" + configured max. On clean runs or
    // after a session fully completed (whether it was resumed a few times
    // or not), this value will be the same as the max configured.
    //
    private final MutableInt resumableMaxDocuments = new MutableInt();

    @Getter
    private String instanceId;

    //--- Properties set on Stop -----------------------------------------------

    private boolean stopping;
    private boolean stopped;

    @Builder
    protected Crawler(
            @NonNull CrawlSession crawlSession,
            @NonNull CrawlerConfig crawlerConfig,
            @NonNull CrawlerImpl crawlerImpl) {
        instanceId = UUID.randomUUID().toString();
        this.crawlSession = crawlSession;
        configuration = crawlerConfig;
        this.crawlerImpl = crawlerImpl;
        stopping = false;
        stopped = false;


        if (StringUtils.isBlank(getId())) {
            throw new CrawlerException("Crawler must be given "
                    + "a unique identifier (id).");
        }

        workDir = crawlSession.getWorkDir().resolve(
                FileUtil.toSafeFileName(getId()));
        tempDir = workDir.resolve("temp");

        crawlerDataStoreEngine = new CrawlerDataStoreEngine(this);
        service = new CrawlerService(this);

        committerService = CommitterService.<CrawlDoc>builder()
                .committers(crawlerConfig.getCommitters())
                .eventManager(crawlSession.getEventManager())
                .upsertRequestBuilder(doc -> new UpsertRequest(
                        doc.getReference(),
                        doc.getMetadata(),
                        doc.getInputStream()))
                .deleteRequestBuilder(doc -> new DeleteRequest(
                        doc.getReference(),
                        doc.getMetadata()))
                .build();

        importer = new Importer(
                configuration.getImporterConfig(),
                getEventManager());

    }

    //--- Set at construction --------------------------------------------------

    public String getId() {
        return configuration.getId();
    }

    /**
     * Gets the event manager.
     * @return event manager
     */
    public EventManager getEventManager() {
          return crawlSession.getEventManager();
    }
    public CachedStreamFactory getStreamFactory() {
        return crawlSession.getStreamFactory();
    }

    public void queueDocRecord(CrawlDocRecord rec) {
        crawlerImpl.queuePipeline().accept(
                new DocRecordPipelineContext(this, rec));
    }
    //MAYBE: Keep this one or always force to pass context?
    public ImporterResponse importDoc(CrawlDoc doc) {
        return importDoc(new ImporterPipelineContext(this, doc));
    }
    public ImporterResponse importDoc(ImporterPipelineContext ctx) {
        return crawlerImpl.importerPipeline().apply(ctx);
    }
    public void commitDoc(CrawlDoc doc) {
        crawlerImpl.committerPipeline().accept(
                new DocumentPipelineContext(this, doc));
    }

    CrawlDocRecordFactory getDocRecordFactory() {
        return crawlerImpl.docRecordFactory();
    }
    void logProgress() {
        if (progressLogger != null) {
            progressLogger.logProgress();
        } else {
            LOG.debug("ProgressLogger is null.");
        }
    }

    // invoked as the first thing for every commands.
    boolean initCrawler() {
        // Ensure1 clean slate by either replacing or clearing and adding back

        Thread.currentThread().setName(getId());
        MdcUtil.setCrawlerId(getId());

        //--- Directories ---
        createDirectory(workDir);
        createDirectory(tempDir);

        //--- Crawler implementation-specific context ---
        crawlerContext = crawlerImpl.crawlerImplContext().get();

//        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        //--- Store engine ---
//        dataStoreEngine = configuration.getDataStoreEngine();
//        dataStoreEngine.init(this);
        docRecordService = new CrawlDocRecordService(
                this, crawlerImpl.crawlDocRecordType());

        //--- Committers ---
        //TODO come up with committer-specific ID instead of index
        // index will be appended to committer workdir for each one
        var committerContext = CommitterContext.builder()
                .setEventManager(getEventManager())
                .setWorkDir(getWorkDir().resolve("committer"))
                .setStreamFactory(getStreamFactory())
                .build();
        committerService.init(committerContext);



//        if (initAction != null) {
//            initAction.run();
//        }

//        fire(CrawlerEvent.CRAWLER_INIT_END);
        return docRecordService.open();
    }

    //--- Life-cycle/action methods --------------------------------------------
    // Not public. Invoked from CrawlerService public equivalent.

    /**
     * START:
     * Do not invoked directly, use {@link CrawlerService#start()}.
     */
    void start() {
        try {
            //TODO check current state first and act accordingly (e.g., join
            // an already crawling cluster?)


            //--- Initialize ---
            fire(CrawlerEvent.CRAWLER_INIT_BEGIN);
            var resume = initCrawler();

            service.onSingleInstance(
                    CrawlerState.INIT_DOC_STORES,
                    () -> docRecordService.reset(resume));
            if (stopping) {
                return;
            }

            initCrawlerForStart(resume);
            service.onSingleInstance(
                    CrawlerState.INIT_QUEUE,
                    () -> initReferenceQueue(resume));
            if (stopping) {
                return;
            }

            fire(CrawlerEvent.CRAWLER_INIT_END);

            //--- Ready to crawl ---

            fire(CrawlerEvent.CRAWLER_RUN_BEGIN);

            service.onAllInstances(CrawlerState.CRAWLING, () -> crawl(resume));

            if (stopping) {
                stopped = true;
                fire(CrawlerEvent.CRAWLER_STOP_END);
            }
            fire(CrawlerEvent.CRAWLER_RUN_END);
            LOG.info("Crawler {}", (isStopped() ? "stopped." : "completed."));

            //TODO maybe do onSingleInstance finalize store or else?
        } finally {
            try {
                Optional.ofNullable(crawlerImpl.afterCrawlerExecution)
                        .ifPresent(c -> c.accept(this));
            } finally {
                if (progressLogger != null) {
                    progressLogger.stopTracking();
                    try {
                        LOG.info("Execution Summary:{}",
                                progressLogger.getExecutionSummary());
                    } finally {
                        destroyCrawler();
                    }
                }
            }
            // Note: unregistering of JMX monitor bean is done in CrawlSession.
        }
    }

    //--- Misc. ----------------------------------------------------------------

    private void initCrawlerForStart(boolean resume) {
//        importer = new Importer(
//                getConfiguration().getImporterConfig(),
//                getEventManager());
        monitor = new CrawlerMonitor(this);
        //TODO make general logging messages verbosity configurable
        progressLogger = new CrawlProgressLogger(monitor,
                getConfiguration().getMinProgressLoggingInterval());
        progressLogger.startTracking();
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMonitorJMX.register(this);
        }

        logContextInfo();

        fetcher = crawlerImpl.fetcherProvider().apply(this);
        dedupMetadataStore = resolveMetaDedupStore();
        dedupDocumentStore = resolveDocumentDedupStore();

        Optional.ofNullable(crawlerImpl.beforeCrawlerExecution)
                .ifPresent(c -> c.accept(this, resume));

        // max documents
        var cfgMaxDocs = getConfiguration().getMaxDocuments();
        var resumeMaxDocs = cfgMaxDocs;
        if (cfgMaxDocs > -1 && resume) {
            resumeMaxDocs += monitor.getProcessedCount();
            LOG.info("""
                Adding configured maximum documents ({})\s\
                to this resumed session. The combined maximum\s\
                documents for this run before stopping: {}
                """,
                cfgMaxDocs, resumeMaxDocs);
        }
        resumableMaxDocuments.setValue(resumeMaxDocs);
    }


    private void initReferenceQueue(boolean resume) {
        //--- Queue initial references ---------------------------------
        //TODO if we resume, shall we not queue again? What if it stopped
        // in the middle of initial queuing, then to be safe we have to
        // queue again and expect that those that were already processed
        // will simply be ignored (default behavior).
        // Consider persisting a flag that would tell us if we are resuming
        // with an incomplete queue initialization, or make initialization
        // more sophisticated so we can resume in the middle of it
        // (this last option would likely be very impractical).
        LOG.info("Queueing initial references...");
        queueInitialized = ofNullable(crawlerImpl.queueInitializer())
            .map(qizer -> qizer.apply(new CrawlerImpl.QueueInitContext(
                    Crawler.this, resume, rec ->
                            crawlerImpl.queuePipeline().accept(
                                    new DocRecordPipelineContext(
                                            Crawler.this, rec)))))
            .orElse(new MutableBoolean(true));
    }

    private void crawl(boolean resume) {


//        fire(CrawlerEvent.CRAWLER_RUN_BEGIN);

//        System.err.println("GLOBAL STATE: " + clusterService.getGlobalState());
//        System.err.println("LOCAL STATE: " + clusterService.getLocalState());
        //TODO for stopping, remove where we have the boolean instance
        // and rely on cluster state instead that we can obtained pretty
        // much anywhere.



        //--- Process start/queued references ------------------------------

        LOG.info("Crawling references...");
        processReferences(new ProcessFlags());

//        if (!service.isInstanceStopped()) { // how about stopping?
        if (!stopping) {
            handleOrphans();
        }

//        if (stopping || stopped) { //TODO do we have a use for "stopped"?
//            fire(CrawlerEvent.CRAWLER_STOP_END);
//        }
//        fire(CrawlerEvent.CRAWLER_RUN_END);
//        LOG.info("Crawler {}", (isStopped() ? "stopped." : "completed."));
    }

    void processReferences(final ProcessFlags flags) {
        var numThreads = getConfiguration().getNumThreads();
        final var latch = new CountDownLatch(numThreads);
        var execService = Executors.newFixedThreadPool(numThreads);
        try {
            for (var i = 0; i < numThreads; i++) {
                final var threadIndex = i + 1;
                LOG.debug("Crawler thread #{} starting...", threadIndex);
                execService.execute(CrawlerThread.builder()
                    .crawler(this)
                    .latch(latch)
                    .threadIndex(threadIndex)
                    .deleting(flags.delete)
                    .orphan(flags.orphan)
                    .build());
            }
            latch.await();
        } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
             throw new CrawlerException(e);
        } finally {
            execService.shutdown();
            // necessary to ensure thread end event is not sometimes fired
            // after crawler run end.
            try {
                execService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Failed to wait for crawler termination.", e);
            }
        }
    }

//    /**
//     * Whether this crawler instance is stopping.
//     * @return <code>true</code> if stopping
//     */
//    public boolean isStopping() {
//        return stopping;
//    }
    // needed?
    /**
     * Whether this crawler instance has stopped.
     * @return <code>true</code> if stopped
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Stops a running crawler.
     */
    void stop() {
//        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);
//        initCrawler();  // why? don't do this here.
//        fire(CrawlerEvent.CRAWLER_INIT_END);
        fire(CrawlerEvent.CRAWLER_STOP_BEGIN);
        stopping = true;
        LOG.info("Stopping the crawler.");
    }

    /**
     * Cleans the crawler cache information, leading to the next run
     * being as if the crawler was run for the first time.
     */
    public void clean() {
        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);
        initCrawler();
        fire(CrawlerEvent.CRAWLER_INIT_END);
        getEventManager().fire(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_BEGIN)
                .source(this)
                .message("Cleaning cached crawler \"" + getId() + "\" data...")
                .build());
        try {
            committerService.clean();
//            dataStoreEngine.clean();
            destroyCrawler();
            FileUtils.deleteDirectory(getWorkDir().toFile());
            getEventManager().fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.CRAWLER_CLEAN_END)
                    .source(this)
                    .message("Done cleaning crawler \"" + getId() + "\".")
                    .build());
        } catch (IOException e) {
            throw new CrawlerException("Could not clean \"" + getId()
                    + "\" crawler directory.", e);
        }
    }

    void handleOrphans() {

        var strategy = configuration.getOrphansStrategy();
        if (strategy == null) {
            // null is same as ignore
            strategy = OrphansStrategy.IGNORE;
        }

        // If PROCESS, we do not care to validate if really orphan since
        // all cache items will be reprocessed regardless
        if (strategy == OrphansStrategy.PROCESS) {
            reprocessCacheOrphans();
            return;
        }

        if (strategy == OrphansStrategy.DELETE) {
            deleteCacheOrphans();
        }
        // else, ignore (i.e. don't do anything)
        //TODO log how many where ignored (cache count)
    }

    void reprocessCacheOrphans() {
        if (isMaxDocuments()) {
            LOG.info("Max documents reached. "
                    + "Not reprocessing orphans (if any).");
            return;
        }
        LOG.info("Reprocessing any cached/orphan references...");

        var count = new MutableLong();
        docRecordService.forEachCached((ref, docInfo) -> {
            crawlerImpl.queuePipeline().accept(
                    new DocRecordPipelineContext(this, docInfo));
            count.increment();
            return true;
        });

        if (count.longValue() > 0) {
            processReferences(new ProcessFlags().orphan());
        }
        LOG.info("Reprocessed {} cached/orphan references.", count);
    }

    void deleteCacheOrphans() {
        LOG.info("Deleting orphan references (if any)...");

        var count = new MutableLong();
        docRecordService.forEachCached((k, v) -> {
            docRecordService.queue(v);
            count.increment();
            return true;
        });
        if (count.longValue() > 0) {
            processReferences(new ProcessFlags().delete());
        }
        LOG.info("Deleted {} orphan references.", count);
    }

    //TODO duplicate method, move to util class
    boolean isMaxDocuments() {
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        // Check if we merge with StopCrawlerOnMaxEventListener
        // or if we remove maxDocument in favor of the listener.
        // what about clustering?
        var maxDocs = resumableMaxDocuments.intValue();
        var isMax = maxDocs > -1 && monitor.getProcessedCount() >= maxDocs;
        if (isMax) {
            LOG.info("Maximum documents reached for this session: {}", maxDocs);
        }
        return isMax;
    }

    void destroyCrawler() {
        ofNullable(docRecordService).ifPresent(
                CrawlDocRecordService::close);
//        ofNullable(service).ifPresent(
//                CrawlerService::close);
//        ofNullable(crawlerDataStoreEngine).ifPresent(DataStoreEngine::close);

        //TODO shall we clear crawler listeners, or leave to collector impl
        // to clean all?
        // eventManager.clearListeners();
        ofNullable(committerService).ifPresent(CommitterService::close);
    }

    // store made of: checksum -> ref
    private DataStore<String> resolveMetaDedupStore() {
        if (configuration.isMetadataDeduplicate()
                && configuration.getMetadataChecksummer() != null) {
            return crawlerDataStoreEngine.openCrawlerStore(
                    "dedup-metadata", String.class);
        }
        return null;
    }
    // store made of: checksum -> ref
    private DataStore<String> resolveDocumentDedupStore() {
        if (configuration.isDocumentDeduplicate()
                && configuration.getDocumentChecksummer() != null) {
            return crawlerDataStoreEngine.openCrawlerStore(
                    "dedup-document", String.class);
        }
        return null;
    }

    private void logContextInfo() {
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as JVM argument.");
        }
    }

    public void fire(Event event) {
        getEventManager().fire(event);
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

    public boolean isQueueInitialized() {
        // exceptions aside, this is never null when start method was called
        return ofNullable(queueInitialized)
                .map(MutableBoolean::booleanValue)
                .orElse(false);
    }

    //TODO make enum if never mixed, and add "default"
    //TODO or add this to CrawlDoc
    @Getter
    public static final class ProcessFlags {
        private boolean delete;
        private boolean orphan;
        private ProcessFlags delete() {
            delete = true;
            return this;
        }
        private ProcessFlags orphan() {
            orphan = true;
            return this;
        }
    }

    private static void createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Could not create directory: " + dir, e);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append(getId())
                .build();
    }
}
