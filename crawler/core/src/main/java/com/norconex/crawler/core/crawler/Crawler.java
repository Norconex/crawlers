/* Copyright 2014-2022 Norconex Inc.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.committer.core.CommitterContext;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.crawler.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.doc.CrawlDocRecordService;
import com.norconex.crawler.core.fetch.IFetchRequest;
import com.norconex.crawler.core.fetch.IFetchResponse;
import com.norconex.crawler.core.fetch.IFetcher;
import com.norconex.crawler.core.monitor.CrawlerMonitor;
import com.norconex.crawler.core.monitor.CrawlerMonitorJMX;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.pipeline.DocInfoPipelineContext;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionException;
import com.norconex.crawler.core.store.DataStoreExporter;
import com.norconex.crawler.core.store.DataStoreImporter;
import com.norconex.crawler.core.store.IDataStore;
import com.norconex.crawler.core.store.IDataStoreEngine;
import com.norconex.importer.Importer;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Abstract crawler implementation providing a common base to building
 * crawlers.</p>
 *
 * <p>As of 1.6.1, JMX support is disabled by default.  To enable it,
 * set the system property "enableJMX" to <code>true</code>.  You can do so
 * by adding this to your Java launch command:
 * </p>
 * <pre>
 *     -DenableJMX=true
 * </pre>
 *
 *
 * @see CrawlerConfig
 */
@Slf4j
//@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Crawler {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    // Set via builder
    @NonNull private final CrawlSession crawlSession;
    @NonNull private final CrawlerConfig crawlerConfig;
    @NonNull private final CrawlerImpl crawlerImpl;

    // From config:
    private Importer importer;
    private Path workDir;
    private Path tempDir;
//    private Path downloadDir;

    // Computed at construction time
    private CrawlerCommitterService committerService;
    private CrawlerMonitor monitor;

    // Set on start():


    // Set on init():


//
    private CrawlProgressLogger progressLogger;
    private IDataStoreEngine dataStoreEngine;
    private CrawlDocRecordService crawlDocRecordService;
    private IFetcher<IFetchRequest, IFetchResponse> fetcher;
    private IDataStore<String> dedupMetadataStore;
    private IDataStore<String> dedupDocumentStore;
    //TODO figure out how to establish the queue was initialized
    // that is, when start URLs are added asynchronously, the queue
    // might be empty for a while, but still being added.
    private MutableBoolean queueInitialized = new MutableBoolean(true);

    // State:
    private boolean stopped;

    @Builder()
    private Crawler(
            CrawlSession crawlSession,
            CrawlerConfig crawlerConfig,
            CrawlerImpl crawlerImpl) {
        this.crawlSession = crawlSession;
        this.crawlerConfig = crawlerConfig;
        this.crawlerImpl = crawlerImpl;

        committerService = new CrawlerCommitterService(this);
        workDir = crawlSession.getWorkDir().resolve(
                FileUtil.toSafeFileName(getId()));
        tempDir = workDir.resolve("temp");
//        downloadDir = workDir.resolve("downloads");
    }

    //--- Set at construction --------------------------------------------------

    public String getId() {
        return crawlerConfig.getId();
    }

    /**
     * Gets the event manager.
     * @return event manager
     */
    public EventManager getEventManager() {
          return crawlSession.getEventManager();
    }

    public CrawlerCommitterService getCommitterService() {
        return committerService;
    }

    public CrawlerImpl getCrawlerImpl() {
        return crawlerImpl;
    }

    /**
     * Gets the crawler configuration.
     * @return the crawler configuration
     */
    public CrawlerConfig getCrawlerConfig() {
        return crawlerConfig;
    }

    public CrawlSession getCrawlSession() {
        return crawlSession;
    }

    /**
     * Gets the directory where files needing to be persisted between
     * crawling sessions are kept.
     * @return working directory, never <code>null</code>
     */
    public Path getWorkDir() {
        return workDir;
    }

    /**
     * Gets the directory where most temporary files are created for the
     * duration of a crawling session. Those files are typically deleted
     * after a crawling session.
     * @return temporary directory, never <code>null</code>
     */
    public Path getTempDir() {
        return tempDir;
    }

//    public Path getDownloadDir() {
//        return downloadDir;
//    }

    //--- Set on init() --------------------------------------------------------

    public IDataStoreEngine getDataStoreEngine() {
        return dataStoreEngine;
    }

    public CrawlDocRecordService getDocInfoService() {
        return crawlDocRecordService;
    }

    //--- Set on start() -------------------------------------------------------


    //////

    CrawlProgressLogger getProgressLogger() {
        return progressLogger;
    }


    public CrawlerMonitor getMonitor() {
        return monitor;
    }

    public IFetcher<IFetchRequest, IFetchResponse>
            getFetcher() {
        return fetcher;
    }

    public IDataStore<String> getDedupMetadataStore() {
        return dedupMetadataStore;
    }
    public IDataStore<String> getDedupDocumentStore() {
        return dedupDocumentStore;
    }

    /**
     * Gets the crawler Importer module.
     * @return the Importer
     */
    public Importer getImporter() {
        return importer;
    }

    public CachedStreamFactory getStreamFactory() {
        return crawlSession.getStreamFactory();
    }


    // invoked as the first thing for every commands.
    protected boolean initCrawler() {
        // Ensure clean slate by either replacing or clearing and adding back

        if (StringUtils.isBlank(getId())) {
            throw new CrawlerException("Crawler must be given "
                    + "a unique identifier (id).");
        }
        Thread.currentThread().setName(getId());
        MdcUtil.setCrawlerId(getId());

        //--- Directories ---
        createDirectory(workDir);
        createDirectory(tempDir);
//        downloadDir = getWorkDir().resolve("downloads");

        fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        //--- Store engine ---
        dataStoreEngine = crawlerConfig.getDataStoreEngine();
        dataStoreEngine.init(this);
        crawlDocRecordService = new CrawlDocRecordService(
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

        var resuming = crawlDocRecordService.open();
        fire(CrawlerEvent.CRAWLER_INIT_END);
        return resuming;
    }

    /**
     * Starts crawling.
     */
    public void start() {
        var resume = initCrawler();
        importer = new Importer(
                getCrawlerConfig().getImporterConfig(),
                getEventManager());
        monitor = new CrawlerMonitor(this);
        //TODO make interval configurable
        //TODO make general logging messages verbosity configurable
        progressLogger = new CrawlProgressLogger(monitor, 30 * 1000);
        progressLogger.startTracking();

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMonitorJMX.register(this);
        }

        try {
            fire(CrawlerEvent.CRAWLER_RUN_BEGIN);
            logContextInfo();

            fetcher = crawlerImpl.fetcherProvider().apply(this);
            dedupMetadataStore = resolveMetaDedupStore();
            dedupDocumentStore = resolveDocumentDedupStore();

            Optional.ofNullable(crawlerImpl.beforeCrawlerExecution)
                    .ifPresent(c -> c.accept(this, resume));

            //--- Process start/queued references ----------------------------------
            LOG.info("Crawling references...");
            processReferences(new ProcessFlags());

            if (!isStopped()) {
                handleOrphans();
            }

//            LOG.debug("Removing empty directories");
//            try {
//                FileUtil.deleteEmptyDirs(getDownloadDir().toFile());
//            } catch (IOException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
            fire((isStopped()
                    ? CrawlerEvent.CRAWLER_STOP_END
                    : CrawlerEvent.CRAWLER_RUN_END));
            LOG.info("Crawler {}", (isStopped() ? "stopped." : "completed."));
        } finally {
            try {
                Optional.ofNullable(crawlerImpl.afterCrawlerExecution)
                        .ifPresent(c -> c.accept(this));
            } finally {
                progressLogger.stopTracking();
                try {
                    LOG.info("Execution Summary:{}",
                            progressLogger.getExecutionSummary());
                } finally {
                    destroyCrawler();
                }
            }
            if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
                CrawlerMonitorJMX.unregister(this);
            }
        }
    }

    protected void processReferences(final ProcessFlags flags) {
        var numThreads = getCrawlerConfig().getNumThreads();
        final var latch = new CountDownLatch(numThreads);
        var execService = Executors.newFixedThreadPool(numThreads);
        try {
            for (var i = 0; i < numThreads; i++) {
                final var threadIndex = i + 1;
                LOG.debug("Crawler thread #{} starting...", threadIndex);
                execService.execute(new ReferencesProcessor(
                        Crawler.this, latch, flags, threadIndex));
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
                throw new CrawlerException(e);
            }
        }
    }


    /**
     * Whether the crawler job was stopped.
     * @return <code>true</code> if stopped
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Stops a running crawler.
     */
    public void stop() {
        fire(CrawlerEvent.CRAWLER_STOP_BEGIN);
        stopped = true;
        LOG.info("Stopping the crawler.");
    }

    /**
     * Cleans the crawler cache information, leading to the next run
     * being as if the crawler was run for the first time.
     */
    public void clean() {
        initCrawler();
        getEventManager().fire(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_BEGIN)
                .source(this)
                .message("Cleaning cached crawler \"" + getId() + "\" data...")
                .build());
        try {
            committerService.clean();
            dataStoreEngine.clean();
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


//    public static class CrawlerBuilder {
//
//
//        public Crawler create(CrawlerConfig crawlerConfig) {
//
//        }
//    }


    protected void handleOrphans() {

        var strategy = crawlerConfig.getOrphansStrategy();
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

    protected void reprocessCacheOrphans() {
        if (isMaxDocuments()) {
            LOG.info("Max documents reached. "
                    + "Not reprocessing orphans (if any).");
            return;
        }
        LOG.info("Reprocessing any cached/orphan references...");

        var count = new MutableLong();
        crawlDocRecordService.forEachCached((ref, docInfo) -> {

            crawlerImpl.queuePipelineExecutor().accept(
                    new DocInfoPipelineContext(this, docInfo));
//            definition.queuePipelineExecutor.accept(this, v);

//            executeQueuePipeline(v);
            count.increment();
            return true;
        });

        if (count.longValue() > 0) {
            processReferences(new ProcessFlags().orphan());
        }
        LOG.info("Reprocessed {} cached/orphan references.", count);
    }

//    protected abstract void executeQueuePipeline(CrawlDocRecord ref);

    protected void deleteCacheOrphans() {
        LOG.info("Deleting orphan references (if any)...");

        var count = new MutableLong();
        crawlDocRecordService.forEachCached((k, v) -> {
            crawlDocRecordService.queue(v);
            count.increment();
            return true;
        });
        if (count.longValue() > 0) {
            processReferences(new ProcessFlags().delete());
        }
        LOG.info("Deleted {} orphan references.", count);
    }

    //TODO duplicate method, move to util class
    private boolean isMaxDocuments() {
        var maxDocs = crawlerConfig.getMaxDocuments();
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        return maxDocs > -1
                && getMonitor().getProcessedCount() >= maxDocs;
    }

    public void importDataStore(Path inFile) {
        initCrawler();
        try {
            DataStoreImporter.importDataStore(this, inFile);
        } catch (IOException e) {
            throw new CrawlerException("Could not import data store.", e);
        } finally {
            destroyCrawler();
        }
    }
    public Path exportDataStore(Path dir) {
        initCrawler();
        try {
            return DataStoreExporter.exportDataStore(this, dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not export data store.", e);
        } finally {
            destroyCrawler();
        }
    }

    protected void destroyCrawler() {
        ofNullable(crawlDocRecordService).ifPresent(
                CrawlDocRecordService::close);
        ofNullable(dataStoreEngine).ifPresent(IDataStoreEngine::close);

        //TODO shall we clear crawler listeners, or leave to collector impl
        // to clean all?
        // eventManager.clearListeners();
        ofNullable(committerService).ifPresent(CrawlerCommitterService::close);
    }

    // store made of: checksum -> ref
    private IDataStore<String> resolveMetaDedupStore() {
        if (crawlerConfig.isMetadataDeduplicate()
                && crawlerConfig.getMetadataChecksummer() != null) {
            return getDataStoreEngine().openStore(
                    "dedup-metadata", String.class);
        }
        return null;
    }
    // store made of: checksum -> ref
    private IDataStore<String> resolveDocumentDedupStore() {
        if (crawlerConfig.isDocumentDeduplicate()
                && crawlerConfig.getDocumentChecksummer() != null) {
            return getDataStoreEngine().openStore(
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

    void fire(String eventName) {
        getEventManager().fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .build());
    }
    void fire(String eventName, Object subject) {
        getEventManager().fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .subject(subject)
                .build());
    }

    boolean getQueueInitialized() {
        return queueInitialized.booleanValue();
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

  //private static final int MINIMUM_DELAY = 1;
  //public static final String SYS_PROP_ENABLE_JMX = "enableJMX";
  //
  //private final CrawlerConfig config;
  //private final Collector collector;
  //private Importer importer;
  //private final CrawlerCommitterService committers;
  //
  //private Path workDir;
  //private Path tempDir;
  //private Path downloadDir;
  //
  //private boolean stopped;
  //
  //private CrawlerMonitor monitor;
  //private CrawlProgressLogger progressLogger;
  //private IDataStoreEngine dataStoreEngine;
  //private CrawlDocRecordService crawlDocRecordService;
  //
  //private final CrawlerDefinition definition;
  //
  //// Set only on start:
  //private IFetcher<? extends IFetchRequest, ? extends IFetchResponse>
  //private IDataStore<String> dedupMetadataStore;
  //private IDataStore<String> dedupDocumentStore;
  //private MutableBoolean queueInitialized = new MutableBoolean(true);
  //
  //
  /////**
  //// * Constructor.
  //// * @param config crawler configuration
  //// * @param collector the collector this crawler is attached to
  //// */
  //public Crawler(
//          @NonNull Collector collector,
//          @NonNull CrawlerDefinition definition) {
//      requiredDef(definition.fetcherProvider, "fetcherProvider");
//      requiredDef(definition.childDocInfoCreator, "childDocInfoCreator");
//      requiredDef(definition.queuePipelineExecutor, "queuePipelineExecutor");
//      requiredDef(definition.importerPipelineExecutor, "importerPipelineExecutor");
//      requiredDef(definition.committerPipelineExecutor, "committerPipelineExecutor");
//      requiredDef(definition.crawlerConfig, "crawlerConfig");
  //
//      this.collector = collector;
//      this.definition = definition;
//      committers = new CrawlerCommitterService(this);
//      config = definition.crawlerConfig;
  //}
  //
  //private static void requiredDef(Object obj, String property) {
//      requireNonNull(obj, String.format("The \"%s\" property of "
//              + "CrawlerDefinition must not be null.", property));
  //}
  //
  //
  //public CrawlerMonitor getMonitor() {
//      return monitor;
  //}
  //
  //public CrawlerCommitterService getCommitterService() {
//      return committers;
  //}
  //
  //public IFetcher<? extends IFetchRequest, ? extends IFetchResponse>
//          getFetcher() {
//      return fetcher;
  //}
  //
  //public String getId() {
//      return crawlerConfig.getId();
  //}
  //
  //public IDataStore<String> getDedupMetadataStore() {
//      return dedupMetadataStore;
  //}
  //public IDataStore<String> getDedupDocumentStore() {
//      return dedupDocumentStore;
  //}
  //
  ///**
  // * Whether the crawler job was stopped.
  // * @return <code>true</code> if stopped
  // */
  //public boolean isStopped() {
//      return stopped;
  //}
  //
  //public void stop() {
//      getEventManager().fire(
//              CrawlerEvent.builder().name(CrawlerEvent.CRAWLER_STOP_BEGIN, this).build());
//      stopped = true;
//      LOG.info("Stopping the crawler.");
  //}
  //
  ///**
  // * Gets the crawler Importer module.
  // * @return the Importer
  // */
  //public Importer getImporter() {
//      return importer;
  //}
  //
  //public CachedStreamFactory getStreamFactory() {
//      return collector.getStreamFactory();
  //}
  //
  //public CrawlerDefinition getCrawlerDefinition() {
//      return definition;
  //}
  //
  ///**
  // * Gets the crawler configuration.
  // * @return the crawler configuration
  // */
  //public CrawlerConfig getCrawlerConfig() {
//      return config;
  //}
  //
  //// really make public? Or have a getCollectorId() method instead?
  //public Collector getCollector() {
//      return collector;
  //}
  //
  ///**
  // * Gets the directory where files needing to be persisted between
  // * crawling sessions are kept.
  // * @return working directory, never <code>null</code>
  // */
  //public Path getWorkDir() {
//      if (workDir != null) {
//          return workDir;
//      }
  //
//      String fileSafeId = FileUtil.toSafeFileName(getId());
//      Path dir = collector.getWorkDir().resolve(fileSafeId);
//      try {
//          Files.createDirectories(dir);
//      } catch (IOException e) {
//          throw new CollectorException(
//                  "Could not create crawler working directory.", e);
//      }
//      workDir = dir;
//      return workDir;
  //}
  //
  ///**
  // * Gets the directory where most temporary files are created for the
  // * duration of a crawling session. Those files are typically deleted
  // * after a crawling session.
  // * @return temporary directory, never <code>null</code>
  // */
  //public Path getTempDir() {
//      if (tempDir != null) {
//          return tempDir;
//      }
  //
//      String fileSafeId = FileUtil.toSafeFileName(getId());
//      Path dir = collector.getTempDir().resolve(fileSafeId);
//      try {
//          Files.createDirectories(dir);
//      } catch (IOException e) {
//          throw new CollectorException(
//                  "Could not create crawler temp directory.", e);
//      }
//      tempDir = dir;
//      return tempDir;
  //}
  //
  //
  //public Path getDownloadDir() {
//      return downloadDir;
  //}
  //
  //
  //
  //
  //protected Class<? extends CrawlDocRecord> getCrawlDocInfoType() {
//      return CrawlDocRecord.class;
  //}
  //
  //public IDataStoreEngine getDataStoreEngine() {
//      return dataStoreEngine;
  //}
  //
  //public CrawlDocRecordService getDocInfoService() {
//      return crawlDocRecordService;
  //}
  //
  //
  //

  //
  //protected void doExecute() {
  //
//      //--- Process start/queued references ----------------------------------
//      LOG.info("Crawling references...");
//      processReferences(new ProcessFlags());
  //
//      if (!isStopped()) {
//          handleOrphans();
//      }
  //
//      LOG.debug("Removing empty directories");
//      try {
//          FileUtil.deleteEmptyDirs(getDownloadDir().toFile());
//      } catch (IOException e) {
//          // TODO Auto-generated catch block
//          e.printStackTrace();
//      }
//      getEventManager().fire(CrawlerEvent.builder().name(CrawlerEvent.
//              (isStopped() ? CRAWLER_STOP_END : CRAWLER_RUN_END),
//              this).build());
//      LOG.info("Crawler {}", (isStopped() ? "stopped." : "completed."));
  //}
  //
  //protected void handleOrphans() {
  //
//      OrphansStrategy strategy = crawlerConfig.getOrphansStrategy();
//      if (strategy == null) {
//          // null is same as ignore
//          strategy = OrphansStrategy.IGNORE;
//      }
  //
//      // If PROCESS, we do not care to validate if really orphan since
//      // all cache items will be reprocessed regardless
//      if (strategy == OrphansStrategy.PROCESS) {
//          reprocessCacheOrphans();
//          return;
//      }
  //
//      if (strategy == OrphansStrategy.DELETE) {
//          deleteCacheOrphans();
//      }
//      // else, ignore (i.e. don't do anything)
//      //TODO log how many where ignored (cache count)
  //}
  //
  //protected boolean isMaxDocuments() {
//      //TODO replace check for "processedCount" vs "maxDocuments"
//      // with event counts vs max committed, max processed, max etc...
//      return getCrawlerConfig().getMaxDocuments() > -1
//              && monitor.getProcessedCount()
//                      >= getCrawlerConfig().getMaxDocuments();
  //}
  //
  //protected void reprocessCacheOrphans() {
//      if (isMaxDocuments()) {
//          LOG.info("Max documents reached. "
//                  + "Not reprocessing orphans (if any).");
//          return;
//      }
//      LOG.info("Reprocessing any cached/orphan references...");
  //
//      MutableLong count = new MutableLong();
//      crawlDocRecordService.forEachCached((ref, docInfo) -> {
  //
//          definition.queuePipelineExecutor.accept(
//                  new DocInfoPipelineContext(this, docInfo));
////          definition.queuePipelineExecutor.accept(this, v);
  //
////          executeQueuePipeline(v);
//          count.increment();
//          return true;
//      });
  //
//      if (count.longValue() > 0) {
//          processReferences(new ProcessFlags().orphan());
//      }
//      LOG.info("Reprocessed {} cached/orphan references.", count);
  //}
  //
  ////protected abstract void executeQueuePipeline(CrawlDocRecord ref);
  //
  //protected void deleteCacheOrphans() {
//      LOG.info("Deleting orphan references (if any)...");
  //
//      MutableLong count = new MutableLong();
//      crawlDocRecordService.forEachCached((k, v) -> {
//          crawlDocRecordService.queue(v);
//          count.increment();
//          return true;
//      });
//      if (count.longValue() > 0) {
//          processReferences(new ProcessFlags().delete());
//      }
//      LOG.info("Deleted {} orphan references.", count);
  //}
  //
  //protected void processReferences(final ProcessFlags flags) {
//      int numThreads = getCrawlerConfig().getNumThreads();
//      final CountDownLatch latch = new CountDownLatch(numThreads);
//      ExecutorService execService = Executors.newFixedThreadPool(numThreads);
//      try {
//          for (int i = 0; i < numThreads; i++) {
//              final int threadIndex = i + 1;
//              LOG.debug("Crawler thread #{} starting...", threadIndex);
//              execService.execute(new ProcessReferencesRunnable(
//                      latch, flags, threadIndex));
//          }
//          latch.await();
//      } catch (InterruptedException e) {
//           Thread.currentThread().interrupt();
//           throw new CollectorException(e);
//      } finally {
//          execService.shutdown();
//      }
  //}
  //
  //protected enum ReferenceProcessStatus {
//      MAX_REACHED,
//      QUEUE_EMPTY,
//      OK;
  //}
  //
  //// return <code>true</code> if more references to process
  //protected ReferenceProcessStatus processNextReference(
//          final ProcessFlags flags) {
  //
//      if (!flags.delete && isMaxDocuments()) {
//          LOG.info("Maximum documents reached: {}",
//                  getCrawlerConfig().getMaxDocuments());
//          return ReferenceProcessStatus.MAX_REACHED;
//      }
//      Optional<CrawlDocRecord> queuedDocInfo =
//              crawlDocRecordService.pollQueue();
  //
//      LOG.trace("Processing next reference from Queue: {}",
//              queuedDocInfo);
//      if (queuedDocInfo.isPresent()) {
//          StopWatch watch = null;
//          if (LOG.isDebugEnabled()) {
//              watch = new StopWatch();
//              watch.start();
//          }
//          processNextQueuedCrawlData(queuedDocInfo.get(), flags);
//          if (LOG.isDebugEnabled()) {
//              watch.stop();
//              LOG.debug("{} to process: {}", watch,
//                      queuedDocInfo.get().getReference());
//          }
//      } else {
//          long activeCount = crawlDocRecordService.getActiveCount();
//          boolean queueEmpty = crawlDocRecordService.isQueueEmpty();
//          if (LOG.isTraceEnabled()) {
//              LOG.trace("Number of references currently being "
//                      + "processed: {}", activeCount);
//              LOG.trace("Is reference queue empty? {}", queueEmpty);
//          }
//          if (activeCount == 0 && queueEmpty) {
//              return ReferenceProcessStatus.QUEUE_EMPTY;
//          }
//          Sleeper.sleepMillis(MINIMUM_DELAY);
//      }
//      return ReferenceProcessStatus.OK;
  //}
  //
  ////TODO rely on events?
  //protected void initCrawlDoc(CrawlDoc document) {
//      // default does nothing
  //}
  //
  //private void processNextQueuedCrawlData(
//          CrawlDocRecord docInfo, ProcessFlags flags) {
//      String reference = docInfo.getReference();
  //
//      CrawlDocRecord cachedDocInfo =
//              crawlDocRecordService.getCached(reference).orElse(null);
  //
//      CrawlDoc doc = new CrawlDoc(
//              docInfo, cachedDocInfo, getStreamFactory().newInputStream(),
//              flags.orphan);
  //
//      ImporterPipelineContext context =
//              new ImporterPipelineContext(Crawler.this, doc);
  //
//      doc.getMetadata().set(
//              CrawlDocMetadata.IS_CRAWL_NEW,
//              cachedDocInfo == null);
  //
//      initCrawlDoc(doc);
  //
//      try {
//          if (flags.delete) {
//              deleteReference(doc);
//              finalizeDocumentProcessing(doc);
//              return;
//          }
//          LOG.debug("Processing reference: {}", reference);
  //
//          ImporterResponse response =
//                  definition.importerPipelineExecutor.apply(context);
////          ImporterResponse response = executeImporterPipeline(context);
  //
//          if (response != null) {
//              processImportResponse(response, doc);//docInfo, cachedDocInfo);
//          } else {
//              if (docInfo.getState().isNewOrModified()) {
//                  docInfo.setState(CrawlState.REJECTED);
//              }
//              //TODO Fire an event here? If we get here, the importer did
//              //not kick in,
//              //so do not fire REJECTED_IMPORT (like it used to).
//              //Errors should have fired
//              //something already so do not fire two REJECTED... but
//              //what if a previous issue did not fire a REJECTED_*?
//              //This should not happen, but keep an eye on that.
//              //OR do we want to always fire REJECTED_IMPORT on import failure
//              //(in addition to whatever) and maybe a new REJECTED_COLLECTOR
//              //when it did not reach the importer module?
//              finalizeDocumentProcessing(doc);
//          }
//      } catch (Throwable e) {
//          //TODO do we really want to catch anything other than
//          // HTTPFetchException?  In case we want special treatment to the
//          // class?
//          docInfo.setState(CrawlState.ERROR);
//          getEventManager().fire(
//                  CrawlerEvent.builder().name(CrawlerEvent.REJECTED_ERROR, this)
//                          .crawlDocInfo(docInfo)
//                          .exception(e)
//                          .build());
//          if (LOG.isDebugEnabled()) {
//              LOG.info("Could not process document: {} ({})",
//                      reference, e.getMessage(), e);
//          } else {
//              LOG.info("Could not process document: {} ({})",
//                      reference, e.getMessage());
//          }
//          finalizeDocumentProcessing(doc);
  //
//          // Rethrow exception is we want the crawler to stop
//          List<Class<? extends Exception>> exceptionClasses =
//                  crawlerConfig.getStopOnExceptions();
//          if (CollectionUtils.isNotEmpty(exceptionClasses)) {
//              for (Class<? extends Exception> c : exceptionClasses) {
//                  if (c.isAssignableFrom(e.getClass())) {
//                      throw e;
//                  }
//              }
//          }
//      }
  //}
  //
  //private void processImportResponse(
//          ImporterResponse response, CrawlDoc doc) {
  //
//      CrawlDocRecord docInfo = doc.getDocInfo();
  //
//      String msg = response.getImporterStatus().toString();
//      if (response.getNestedResponses().length > 0) {
//          msg += "(" + response.getNestedResponses().length
//                  + " nested responses.)";
//      }
  //
//      if (response.isSuccess()) {
//          getEventManager().fire(
//                  CrawlerEvent.builder().name(CrawlerEvent.DOCUMENT_IMPORTED, this)
//                      .crawlDocInfo(docInfo)
//                      .subject(response)
//                      .message(msg)
//                      .build());
//          definition.committerPipelineExecutor.accept(this, doc);
////          executeCommitterPipeline(this, doc);
//      } else {
//          docInfo.setState(CrawlState.REJECTED);
//          getEventManager().fire(
//                  CrawlerEvent.builder().name(CrawlerEvent.REJECTED_IMPORT, this)
//                      .crawlDocInfo(docInfo)
//                      .subject(response)
//                      .message(msg)
//                      .build());
//          LOG.debug("Importing unsuccessful for \"{}\": {}",
//                  docInfo.getReference(),
//                  response.getImporterStatus().getDescription());
//      }
//      finalizeDocumentProcessing(doc);
//      ImporterResponse[] children = response.getNestedResponses();
//      for (ImporterResponse childResponse : children) {
//          //TODO have a createEmbeddedDoc method instead?
//          CrawlDocRecord childDocInfo = definition.childDocInfoCreator.apply(
//                  childResponse.getReference(), docInfo);
////          CrawlDocRecord childDocInfo = createChildDocInfo(
////                  childResponse.getReference(), docInfo);
//          CrawlDocRecord childCachedDocInfo =
//                  crawlDocRecordService.getCached(
//                          childResponse.getReference()).orElse(null);
  //
//          // Here we create a CrawlDoc since the document from the response
//          // is (or can be) just a Doc, which does not hold all required
//          // properties for crawling.
//          //TODO refactor Doc vs CrawlDoc to have only one instance
//          // so we do not have to create such copy?
//          Doc childResponseDoc = childResponse.getDocument();
//          CrawlDoc childCrawlDoc = new CrawlDoc(
//                  childDocInfo, childCachedDocInfo,
//                  childResponseDoc == null
//                          ? CachedInputStream.cache(new NullInputStream(0))
//                          : childResponseDoc.getInputStream());
//          if (childResponseDoc != null) {
//              childCrawlDoc.getMetadata().putAll(
//                      childResponseDoc.getMetadata());
//          }
  //
//          processImportResponse(childResponse, childCrawlDoc);
//      }
  //}
  //
  //
  //private void finalizeDocumentProcessing(CrawlDoc doc) {
  //
//      CrawlDocRecord docInfo = doc.getDocInfo();
//      CrawlDocRecord cachedDocInfo = doc.getCachedDocInfo();
  //
//      //--- Ensure we have a state -------------------------------------------
//      if (docInfo.getState() == null) {
//          LOG.warn("Reference status is unknown for \"{}\". "
//                  + "This should not happen. Assuming bad status.",
//                  docInfo.getReference());
//          docInfo.setState(CrawlState.BAD_STATUS);
//      }
  //
//      try {
  //
//          // important to call this before copying properties further down
//          beforeFinalizeDocumentProcessing(doc);
  //
//          //--- If doc crawl was incomplete, set missing info from cache -----
//          // If document is not new or modified, it did not go through
//          // the entire crawl life cycle for a document so maybe not all info
//          // could be gathered for a reference.  Since we do not want to lose
//          // previous information when the crawl was effective/good
//          // we copy it all that is non-null from cache.
//          if (!docInfo.getState().isNewOrModified() && cachedDocInfo != null) {
//              //TODO maybe new CrawlData instances should be initialized with
//              // some of cache data available instead?
//              BeanUtil.copyPropertiesOverNulls(docInfo, cachedDocInfo);
//          }
  //
//          //--- Deal with bad states (if not already deleted) ----------------
//          if (!docInfo.getState().isGoodState()
//                  && !docInfo.getState().isOneOf(CrawlState.DELETED)) {
  //
//              //TODO If duplicate, consider it as spoiled if a cache version
//              // exists in a good state.
//              // This involves elaborating the concept of duplicate
//              // or "reference change" in this core project. Otherwise there
//              // is the slim possibility right now that a Collector
//              // implementation marking references as duplicate may
//              // generate orphans (which may be caught later based
//              // on how orphans are handled, but they should not be ever
//              // considered orphans in the first place).
//              // This could remove the need for the
//              // markReferenceVariationsAsProcessed(...) method
  //
//              SpoiledReferenceStrategy strategy =
//                      getSpoiledStateStrategy(docInfo);
  //
//              if (strategy == SpoiledReferenceStrategy.IGNORE) {
//                  LOG.debug("Ignoring spoiled reference: {}",
//                          docInfo.getReference());
//              } else if (strategy == SpoiledReferenceStrategy.DELETE) {
//                  // Delete if previous state exists and is not already
//                  // marked as deleted.
//                  if (cachedDocInfo != null
//                          && !cachedDocInfo.getState().isOneOf(
//                                  CrawlState.DELETED)) {
//                      deleteReference(doc);
//                  }
//              } else // GRACE_ONCE:
//              // Delete if previous state exists and is a bad state,
//              // but not already marked as deleted.
//              if (cachedDocInfo != null
//                      && !cachedDocInfo.getState().isOneOf(
//                              CrawlState.DELETED)) {
//                  if (!cachedDocInfo.getState().isGoodState()) {
//                      deleteReference(doc);
//                  } else {
//                      LOG.debug("This spoiled reference is "
//                              + "being graced once (will be deleted "
//                              + "next time if still spoiled): {}",
//                              docInfo.getReference());
//                  }
//              }
//          }
//      } catch (Exception e) {
//          LOG.error("Could not finalize processing of: {} ({})",
//                  docInfo.getReference(), e.getMessage(), e);
//      }
  //
//      //--- Mark reference as Processed --------------------------------------
//      try {
//          crawlDocRecordService.processed(docInfo);
//          markReferenceVariationsAsProcessed(docInfo);
  //
//          progressLogger.logProgress();
  //
  //
//      } catch (Exception e) {
//          LOG.error("Could not mark reference as processed: {} ({})",
//                  docInfo.getReference(), e.getMessage(), e);
//      }
  //
//      try {
//          doc.getInputStream().dispose();
//      } catch (Exception e) {
//          LOG.error("Could not dispose of resources.", e);
//      }
  //}
  //
  ///**
  // * Gives implementors a change to take action on a document before
  // * its processing is being finalized (cycle end-of-life for a crawled
  // * reference). Default implementation does nothing.
  // * @param doc the document
  // */
  //protected void beforeFinalizeDocumentProcessing(CrawlDoc doc) {
//      //NOOP
//      //TODO rely on event instead???
  //}
  //
  //protected void markReferenceVariationsAsProcessed(CrawlDocRecord docInfo) {
//      // Mark original URL as processed
//      String originalRef = docInfo.getOriginalReference();
//      String finalRef = docInfo.getReference();
//      if (StringUtils.isNotBlank(originalRef)
//              && ObjectUtils.notEqual(originalRef, finalRef)) {
  //
//          CrawlDocRecord originalDocInfo = docInfo.withReference(originalRef);
//          originalDocInfo.setOriginalReference(null);
//          getDocInfoService().processed(originalDocInfo);
//      }
  //}
  //
  //private SpoiledReferenceStrategy getSpoiledStateStrategy(
//          CrawlDocRecord crawlData) {
//      ISpoiledReferenceStrategizer strategyResolver =
//              crawlerConfig.getSpoiledReferenceStrategizer();
//      SpoiledReferenceStrategy strategy =
//              strategyResolver.resolveSpoiledReferenceStrategy(
//                      crawlData.getReference(), crawlData.getState());
//      if (strategy == null) {
//          // Assume the generic default (DELETE)
//          strategy =  GenericSpoiledReferenceStrategizer
//                  .DEFAULT_FALLBACK_STRATEGY;
//      }
//      return strategy;
  //}
  //
  //private void deleteReference(CrawlDoc doc) {
//      LOG.debug("Deleting reference: {}", doc.getReference());
  //
//      doc.getDocInfo().setState(CrawlState.DELETED);
  //
//      // Event triggered by service
//      committers.delete(doc);
  //}
  //
  ////TODO make enum if never mixed, and add "default"
  //private static final class ProcessFlags {
//      private boolean delete;
//      private boolean orphan;
//      private ProcessFlags delete() {
//          delete = true;
//          return this;
//      }
//      private ProcessFlags orphan() {
//          orphan = true;
//          return this;
//      }
  //}
  //
  //private final class ProcessReferencesRunnable implements Runnable {
//      private final ProcessFlags flags;
//      private final CountDownLatch latch;
//      private final int threadIndex;
  //
//      private ProcessReferencesRunnable(
//              CountDownLatch latch,
//              ProcessFlags flags,
//              int threadIndex) {
//          this.latch = latch;
//          this.flags = flags;
//          this.threadIndex = threadIndex;
//      }
  //
//      @Override
//      public void run() {
//          MdcUtil.setCrawlerId(getId());
//          Thread.currentThread().setName(getId() + "#" + threadIndex);
  //
//          LOG.debug("Crawler thread #{} started.", threadIndex);
  //
//          try {
//              getEventManager().fire(CrawlerEvent.builder().name(CrawlerEvent.
//                      CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN, Crawler.this)
//                          .subject(Thread.currentThread())
//                          .build());
//              while (!isStopped()) {
//                  try {
//                      ReferenceProcessStatus status =
//                              processNextReference(flags);
//                      if (status == MAX_REACHED) {
//                          stop();
//                          break;
//                      }
//                      if (status == QUEUE_EMPTY) {
//                          if (queueInitialized.booleanValue()) {
//                              break;
//                          }
//                          LOG.info("References are still being queued. "
//                                  + "Waiting for new references...");
//                          Sleeper.sleepSeconds(5);
//                      }
//                  } catch (Exception e) {
//                      LOG.error(
//                            "An error occured that could compromise "
//                          + "the stability of the crawler. Stopping "
//                          + "excution to avoid further issues...", e);
//                      stop();
//                  }
//              }
//          } catch (Exception e) {
//              LOG.error("Problem in thread execution.", e);
//          } finally {
//              latch.countDown();
//              getEventManager().fire(CrawlerEvent.builder().name(CrawlerEvent.
//                      CrawlerEvent.CRAWLER_RUN_THREAD_END, Crawler.this)
//                          .subject(Thread.currentThread())
//                          .build());
//          }
//      }
  //}
  //
  //@Override
  //public String toString() {
//      return getId();
  //}

    private static void createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Could not create directory: " + dir, e);
        }
    }

}


