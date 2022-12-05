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
package com.norconex.crawler.core.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileLocker;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.crawler.Crawler;

import lombok.extern.slf4j.Slf4j;

/**
 * Base implementation of a CrawlSession.
 * Instances of this class can hold several crawler, running at once.
 * This is convenient when there are configuration setting to be shared amongst
 * crawlers.  When you have many crawler jobs defined that have nothing
 * in common, it may be best to configure and run them separately, to facilitate
 * troubleshooting.  There is no best rule for this, experimentation
 * will help you.
 */
@Slf4j
public class CrawlSession {

    /** Simple ASCI art of Norconex. */
    public static final String NORCONEX_ASCII =
            " _   _  ___  ____   ____ ___  _   _ _______  __\n"
          + "| \\ | |/ _ \\|  _ \\ / ___/ _ \\| \\ | | ____\\ \\/ /\n"
          + "|  \\| | | | | |_) | |  | | | |  \\| |  _|  \\  / \n"
          + "| |\\  | |_| |  _ <| |__| |_| | |\\  | |___ /  \\ \n"
          + "|_| \\_|\\___/|_| \\_\\\\____\\___/|_| \\_|_____/_/\\_\\\n\n"
          + "================ C R A W L E R ================\n";


    private static final InheritableThreadLocal<CrawlSession> INSTANCE =
            new InheritableThreadLocal<>();

    private final CrawlSessionConfig crawlSessionConfig;
    private final List<Crawler> crawlers = new CopyOnWriteArrayList<>();
    private final EventManager eventManager;

    private CachedStreamFactory streamFactory;
    private Path workDir;
    private Path tempDir;
    private FileLocker lock;

    //TODO make configurable
//    private final ICollectorStopper stopper = new FileBasedStopper();

    /**
     * Creates and configure a CrawlSession with the provided
     * configuration.
     * @param crawlSessionConfig CrawlSession configuration
     */
    public CrawlSession(CrawlSessionConfig crawlSessionConfig) {
        this(crawlSessionConfig, null);
    }

    /**
     * Creates and configure a CrawlSession with the provided
     * configuration.
     * @param crawlSessionConfig CrawlSession configuration
     * @param eventManager event manager
     */
    public CrawlSession(
            CrawlSessionConfig crawlSessionConfig, EventManager eventManager) {


        //TODO clone config so modifications no longer apply?
        Objects.requireNonNull(
                crawlSessionConfig, "'crawlSessionConfig' must not be null.");

        this.crawlSessionConfig = crawlSessionConfig;
        this.eventManager = new EventManager(eventManager);

        INSTANCE.set(this);
    }

    public static CrawlSession get() {
        return INSTANCE.get();
    }

    public synchronized Path getWorkDir() {
        if (workDir == null) {
            workDir = createCollectorSubDirectory(Optional.ofNullable(
                    crawlSessionConfig.getWorkDir()).orElseGet(
                            () -> CrawlSessionConfig.DEFAULT_WORK_DIR));
        }
        return workDir;
    }

//    /**
//     * Gets a temporary directory specific to this crawl session.
//     * For a crawler, it is recommended to use that crawler temporary
//     * directory instead.
//     * @return temporary directory
//     */
//    public synchronized Path getTempDir() {
//        if (tempDir == null) {
//            tempDir = getWorkDir().resolve("temp");
//            try {
//                return Files.createDirectories(tempDir);
//            } catch (IOException e) {
//                throw new CrawlSessionException(
//                        "Could not create directory: " + tempDir, e);
//            }
//        }
//        return tempDir;
//    }

    // parent is never null
    private Path createCollectorSubDirectory(Path parentDir) {
        String fileSafeId = FileUtil.toSafeFileName(getId());
        Path subDir = parentDir.resolve(fileSafeId);
        try {
            return Files.createDirectories(subDir);
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Could not create directory: " + subDir, e);
        }
    }
//
//    /**
//     * Starts all crawlers defined in configuration.
//     */
//    public void start() {
//        MdcUtil.setCollectorId(getId());
//        Thread.currentThread().setName(getId());
//
//        // Version intro
//        LOG.info("\n{}", getReleaseVersions());
//
//        lock();
//        try {
//            initCollector();
//            stopper.listenForStopRequest(this);
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_RUN_BEGIN, this).build());
//
//            List<Crawler> crawlerList = getCrawlers();
//            int maxConcurrent = crawlSessionConfig.getMaxConcurrentCrawlers();
//            if (maxConcurrent <= 0) {
//                maxConcurrent = crawlerList.size();
//            }
//
//            if (crawlerList.size() == 1) {
//                // no concurrent crawlers, just start
//                crawlerList.forEach(Crawler::start);
//            } else {
//                // Multilpe crawlers, run concurrently
//                startConcurrentCrawlers(maxConcurrent);
//            }
//        } finally {
//            try {
//                eventManager.fire(new CollectorEvent.Builder(
//                        COLLECTOR_RUN_END, this).build());
//                destroyCollector();
//            } finally {
//                stopper.destroy();
//            }
//        }
//    }
//
//    private void startConcurrentCrawlers(int poolSize) {
//        Duration d = crawlSessionConfig.getCrawlersStartInterval();
//        if (d == null || d.toMillis() <= 0) {
//            startConcurrentCrawlers(
//                    poolSize,
//                    Executors::newFixedThreadPool,
//                    ExecutorService::execute);
//        } else {
//            startConcurrentCrawlers(
//                    poolSize,
//                    Executors::newScheduledThreadPool,
//                    (pool, run) -> {
//                        ((ScheduledExecutorService) pool).scheduleAtFixedRate(
//                                run, 0, d.toMillis(), TimeUnit.MILLISECONDS);
//                    });
//        }
//    }
//    private void startConcurrentCrawlers(
//            int poolSize,
//            IntFunction<ExecutorService> poolSupplier,
//            BiConsumer<ExecutorService, Runnable> crawlerExecuter) {
//        final CountDownLatch latch = new CountDownLatch(crawlers.size());
//        ExecutorService pool = poolSupplier.apply(poolSize);
//        try {
//            getCrawlers().forEach(c -> crawlerExecuter.accept(pool, () -> {
//                c.start();
//                latch.countDown();
//            }));
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new CrawlSessionException(e);
//        } finally {
//            pool.shutdown();
//            try {
//                pool.awaitTermination(5, TimeUnit.SECONDS);
//            } catch (InterruptedException e) {
//                LOG.error("CrawlSession thread pool interrupted.", e);
//                Thread.currentThread().interrupt();
//            }
//        }
//    }
//
//    public void clean() {
//        MdcUtil.setCollectorId(getId());
//        Thread.currentThread().setName(getId() + "/CLEAN");
//        lock();
//        try {
//            initCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_CLEAN_BEGIN, this)
//                        .message("Cleaning cached CrawlSession data (does not "
//                               + "impact previously committed data)...")
//                        .build());
//            getCrawlers().forEach(Crawler::clean);
//            destroyCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_CLEAN_END, this)
//                        .message("Done cleaning CrawlSession.")
//                        .build());
//        } finally {
//            eventManager.clearListeners();
//            unlock();
//        }
//    }
//
//    public void importDataStore(List<Path> inFiles) {
//        MdcUtil.setCollectorId(getId());
//        Thread.currentThread().setName(getId() + "/IMPORT");
//        lock();
//        try {
//            initCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_STORE_IMPORT_BEGIN, this).build());
//            inFiles.forEach(
//                    f -> getCrawlers().forEach(c -> c.importDataStore(f)));
//            destroyCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_STORE_IMPORT_END, this).build());
//        } finally {
//            eventManager.clearListeners();
//            unlock();
//        }
//    }
//    public void exportDataStore(Path dir) {
//        MdcUtil.setCollectorId(getId());
//        Thread.currentThread().setName(getId() + "/EXPORT");
//        lock();
//        try {
//            initCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_STORE_EXPORT_BEGIN, this).build());
//            //TODO zip all exported data stores in a single file?
//            getCrawlers().forEach(c -> c.exportDataStore(dir));
//            destroyCollector();
//            eventManager.fire(new CollectorEvent.Builder(
//                    COLLECTOR_STORE_EXPORT_END, this).build());
//        } finally {
//            eventManager.clearListeners();
//            unlock();
//        }
//    }
//
//    protected void initCollector() {
//
//        // Ensure clean state
//        tempDir = null;
//        workDir = null;
//
//        crawlers.clear();
//
//        // recreate everything
//        createCrawlers();
//
//        //--- Register event listeners ---
//        eventManager.addListenersFromScan(crawlSessionConfig);
//
//        //TODO move this code to a config validator class?
//        //--- Ensure good state/config ---
//        if (StringUtils.isBlank(crawlSessionConfig.getId())) {
//            throw new CrawlSessionException("CrawlSession must be given "
//                    + "a unique identifier (id).");
//        }
//
//        //--- Stream Cache Factory ---
//        streamFactory = new CachedStreamFactory(
//                (int) crawlSessionConfig.getMaxMemoryPool(),
//                (int) crawlSessionConfig.getMaxMemoryInstance(),
//                getTempDir());
//    }
//
//    protected void destroyCollector() {
//        try {
//            FileUtil.delete(getTempDir().toFile());
//        } catch (IOException e) {
//            throw new CrawlSessionException("Could not delete temp directory", e);
//        } finally {
//            eventManager.clearListeners();
//            unlock();
//        }
//        MDC.clear();
//    }
//
//    public void fireStopRequest() {
//        stopper.fireStopRequest();
//    }
//
//    /**
//     * Stops a running instance of this CrawlSession. The caller can be a
//     * different JVM instance than the instance we want to stop.
//     */
//    public void stop() {
//        if (!isRunning()) {
//            LOG.info("CANNOT STOP: CrawlSession is not running.");
//            return;
//        }
//        MdcUtil.setCollectorId(getId());
//        Thread.currentThread().setName(getId() + "/STOP");
//        eventManager.fire(new CollectorEvent.Builder(
//                CollectorEvent.COLLECTOR_STOP_BEGIN, this).build());
//
//        try {
//            getCrawlers().forEach(Crawler::stop);
//        } finally {
//            try {
//                eventManager.fire(new CollectorEvent.Builder(
//                        CollectorEvent.COLLECTOR_STOP_END, this).build());
//                destroyCollector();
//            } finally {
//                stopper.destroy();
//            }
//        }
//    }
//
//    /**
//     * Gets the event manager.
//     * @return event manager
//     * @since 2.0.0
//     */
//    public EventManager getEventManager() {
//        return eventManager;
//    }
//
//    /**
//     * Loads all crawlers from configuration.
//     */
//    private void createCrawlers() {
//        if (getCrawlers().isEmpty()) {
//            List<CrawlerConfig> crawlerConfigs =
//                    crawlSessionConfig.getCrawlerConfigs();
//            if (crawlerConfigs != null) {
//                for (CrawlerConfig crawlerConfig : crawlerConfigs) {
//                    crawlers.add(createCrawler(crawlerConfig));
//                }
//            }
//        } else {
//            LOG.debug("Crawlers already created.");
//        }
//    }
//
//
//    /**
//     * Creates a new crawler instance.
//     * @param config crawler configuration
//     * @return new crawler
//     */
//    protected abstract Crawler createCrawler(CrawlerConfig config);
//
//    //TODO Since 3.0.0
//    public CachedStreamFactory getStreamFactory() {
//        return streamFactory;
//    }
//
//    /**
//     * Gets the CrawlSession configuration.
//     * @return CrawlSession configuration
//     */
//    public CrawlSessionConfig getCrawlSessionConfig() {
//        return crawlSessionConfig;
//    }
//
    /**
     * Gets the CrawlSession unique identifier.
     * @return CrawlSession unique identifier
     */
    public String getId() {
        return crawlSessionConfig.getId();
    }
//
//    /**
//     * Gets all crawler instances in this CrawlSession.
//     * @return crawlers
//     */
//    public List<Crawler> getCrawlers() {
//        return Collections.unmodifiableList(crawlers);
//    }
//
//    public String getVersion() {
//        return VersionUtil.getVersion(getClass(), "Undefined");
//    }
//
//    public String getReleaseVersions() {
//        StringBuilder b = new StringBuilder()
//            .append(NORCONEX_ASCII)
//            .append("\nCollector and main components:\n")
//            .append("\n");
//        releaseVersions().stream().forEach(s -> b.append(s + '\n'));
//        return b.toString();
//    }
//
//    private List<String> releaseVersions() {
//        List<String> versions = new ArrayList<>();
//        versions.add(releaseVersion("CrawlSession", getClass()));
//        versions.add(releaseVersion("CrawlSession Core", CrawlSession.class));
//        versions.add(releaseVersion("Importer", Importer.class));
//        versions.add(releaseVersion("Lang", ClassFinder.class));
//        versions.add("Committer(s):");
//        versions.add(releaseVersion("  Core", ICommitter.class));
//        for (Class<?> c : nonCoreClasspathCommitters()) {
//            versions.add(releaseVersion("  " + StringUtils.removeEndIgnoreCase(
//                    c.getSimpleName(), "Committer"), c));
//        }
//        versions.add("Runtime:");
//        versions.add("  Name:             " + SystemUtils.JAVA_RUNTIME_NAME);
//        versions.add("  Version:          " + SystemUtils.JAVA_RUNTIME_VERSION);
//        versions.add("  Vendor:           " + SystemUtils.JAVA_VENDOR);
//        return versions;
//    }
//    private String releaseVersion(String moduleName, Class<?> cls) {
//        return StringUtils.rightPad(moduleName + ": ", 20, ' ')
//                + VersionUtil.getDetailedVersion(cls, "undefined");
//    }
//
//    private Set<Class<?>> nonCoreClasspathCommitters() {
//        Set<Class<?>> classes = new HashSet<>();
//        if (crawlSessionConfig == null) {
//            return classes;
//        }
//        crawlSessionConfig.getCrawlerConfigs().forEach(crawlerConfig -> {
//            crawlerConfig.getCommitters().forEach(committer -> {
//                if (!committer.getClass().getName().startsWith(
//                        "com.norconex.committer.core")) {
//                    classes.add(committer.getClass());
//                }
//            });
//        });
//        return classes;
//    }
//
//    protected synchronized void lock() {
//        LOG.debug("Locking CrawlSession execution...");
//        lock = new FileLocker(getWorkDir().resolve(".CrawlSession-lock"));
//        try {
//            lock.lock();
//        } catch (FileAlreadyLockedException e) {
//            throw new CrawlSessionException(
//                    "The CrawlSession you are attempting to run is already "
//                  + "running or executing a command. Wait for "
//                  + "it to complete or stop it and try again.");
//        } catch (IOException e) {
//            throw new CrawlSessionException(
//                    "Could not create a CrawlSession execution lock.", e);
//        }
//        LOG.debug("CrawlSession execution locked");
//    }
//    protected synchronized void unlock() {
//        try {
//            if (lock != null) {
//                lock.unlock();
//            }
//        } catch (IOException e) {
//            throw new CrawlSessionException(
//                    "Cannot unlock CrawlSession execution.", e);
//        }
//        lock = null;
//        LOG.debug("CrawlSession execution unlocked");
//    }
//
//    public boolean isRunning() {
//        return lock != null && lock.isLocked();
//    }
//
//    @Override
//    public String toString() {
//        return getId();
//    }
}
