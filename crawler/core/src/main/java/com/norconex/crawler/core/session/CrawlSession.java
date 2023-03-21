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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileAlreadyLockedException;
import com.norconex.commons.lang.file.FileLocker;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.stop.CrawlSessionStopper;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;

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
        """
         _   _  ___  ____   ____ ___  _   _ _______  __
        | \\ | |/ _ \\|  _ \\ / ___/ _ \\| \\ | | ____\\ \\/ /
        |  \\| | | | | |_) | |  | | | |  \\| |  _|  \\  /\s
        | |\\  | |_| |  _ <| |__| |_| | |\\  | |___ /  \\\s
        |_| \\_|\\___/|_| \\_\\\\____\\___/|_| \\_|_____/_/\\_\\

        ================ C R A W L E R ================
        """;


    private static final InheritableThreadLocal<CrawlSession> INSTANCE =
            new InheritableThreadLocal<>();

    private final CrawlSessionConfig crawlSessionConfig;
    private final EventManager eventManager;
    private final BiFunction<
            CrawlSession, CrawlerConfig, Crawler> crawlerFactory;

    private final List<Crawler> crawlers = new CopyOnWriteArrayList<>();

    private CachedStreamFactory streamFactory;
    private Path workDir;


    private Path tempDir;
    private FileLocker lock;

    private final CrawlSessionStopper stopper;


    public static CrawlSessionBuilder builder() {
        return new CrawlSessionBuilder();
    }


    protected CrawlSession(CrawlSessionBuilder builder) {
        //TODO clone config so modifications no longer apply?
        crawlSessionConfig = Objects.requireNonNull(builder.crawlSessionConfig,
                "'crawlSessionConfig' must not be null.");
        eventManager = new EventManager(builder.eventManager);
        crawlerFactory = Objects.requireNonNull(builder.crawlerFactory,
                "'crawlerFactory' must not be null.");
        stopper = Optional.ofNullable(builder.crawlSessionStopper)
                .orElseGet(FileBasedStopper::new);

        //TODO create crawlers from configs, same place it was done before (in initCrawlSession)

        INSTANCE.set(this);
    }

    public static CrawlSession get() {
        var cs = INSTANCE.get();
        if (cs == null) {
            throw new IllegalStateException("Crawl session not initialized.");
        }
        return cs;
    }

    /**
     * Gets the session work directory, where generated files are stored.
     * @return work directory
     */
    public Path getWorkDir() {
        ensureDirectories();
        return workDir;
    }

    /**
     * Gets a temporary directory specific to this crawl session.
     * Crawler also offer a crawler-specific temporary directory.
     * @return temporary directory
     */
    public Path getTempDir() {
        ensureDirectories();
        return tempDir;
    }

    /**
     * Gets the event manager.
     * @return event manager
     */
    public EventManager getEventManager() {
        return eventManager;
    }

    public CachedStreamFactory getStreamFactory() {
        return streamFactory;
    }

    /**
     * Gets the CrawlSession configuration.
     * @return CrawlSession configuration
     */
    public CrawlSessionConfig getCrawlSessionConfig() {
        return crawlSessionConfig;
    }

    /**
     * Gets the CrawlSession unique identifier.
     * @return CrawlSession unique identifier
     */
    public String getId() {
        return crawlSessionConfig.getId();
    }

    /**
     * Gets all crawler instances in this CrawlSession.
     * @return crawlers
     */
    public List<Crawler> getCrawlers() {
        return Collections.unmodifiableList(crawlers);
    }


    protected void initCrawlSession() {
        // Ensure clean slate by either replacing or clearing and adding back

        //--- (Re)create crawlers
        crawlers.clear();
        createCrawlers();

        //--- (Re)register event listeners ---
        eventManager.clearListeners();
        eventManager.addListenersFromScan(crawlSessionConfig);

        //--- Stream Cache Factory ---
        streamFactory = new CachedStreamFactory(
                (int) crawlSessionConfig.getMaxStreamCachePoolSize(),
                (int) crawlSessionConfig.getMaxStreamCacheSize(),
                tempDir);

        //TODO move this code to a config validator class?
        //--- Ensure good state/config ---
        if (StringUtils.isBlank(crawlSessionConfig.getId())) {
            throw new CrawlSessionException("CrawlSession must be given "
                    + "a unique identifier (id).");
        }

    }

    /**
     * Starts all crawlers defined in configuration.
     */
    public void start() {
        MdcUtil.setCrawlSessionId(getId());
        Thread.currentThread().setName(getId());

        // Version intro
        LOG.info("\n{}\n{}",
                NORCONEX_ASCII,
                CrawlSessionUtil.getReleaseInfo(crawlSessionConfig));

        lock();
        try {
            initCrawlSession();
            stopper.listenForStopRequest(this);
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)
                    .source(this)
                    .build());

            var crawlerList = getCrawlers();
            var maxConcurrent = crawlSessionConfig.getMaxConcurrentCrawlers();
            if (maxConcurrent <= 0) {
                maxConcurrent = crawlerList.size();
            }

            if (crawlerList.size() == 1) {
                // no concurrent crawlers, just start
                crawlerList.forEach(Crawler::start);
            } else {
                // Multilpe crawlers, run concurrently
                startConcurrentCrawlers(maxConcurrent);
            }
        } finally {
            try {
                eventManager.fire(CrawlSessionEvent.builder()
                        .name(CrawlSessionEvent.CRAWLSESSION_RUN_END)
                        .source(this)
                        .build());
                destroyCrawlSession();
            } finally {
                stopper.destroy();
            }
        }
    }

    private void startConcurrentCrawlers(int poolSize) {
        var d = crawlSessionConfig.getCrawlersStartInterval();
        if (d == null || d.toMillis() <= 0) {
            startConcurrentCrawlers(
                    poolSize,
                    Executors::newFixedThreadPool,
                    ExecutorService::execute);
        } else {
            startConcurrentCrawlers(
                    poolSize,
                    Executors::newScheduledThreadPool,
                    (pool, run) ->
                        ((ScheduledExecutorService) pool).scheduleAtFixedRate(
                                run, 0, d.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    private void startConcurrentCrawlers(
            int poolSize,
            IntFunction<ExecutorService> poolSupplier,
            BiConsumer<ExecutorService, Runnable> crawlerExecuter) {
        final var latch = new CountDownLatch(crawlers.size());
        var pool = poolSupplier.apply(poolSize);
        try {
            getCrawlers().forEach(c -> crawlerExecuter.accept(pool, () -> {
                c.start();
                latch.countDown();
            }));
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlSessionException(e);
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error("CrawlSession thread pool interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void clean() {
        MdcUtil.setCrawlSessionId(getId());
        Thread.currentThread().setName(getId() + "/CLEAN");
        lock();
        try {
            initCrawlSession();
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN)
                    .source(this)
                    .message("Cleaning cached CrawlSession data (does not "
                            + "impact previously committed data)...")
                    .build());

            crawlers.forEach(Crawler::clean);
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_CLEAN_END)
                    .source(this)
                    .message("Done cleaning CrawlSession.")
                    .build());
            destroyCrawlSession();
        } finally {
            eventManager.clearListeners();
            unlock();
        }
    }

    public void importDataStore(Collection<Path> inFiles) {
        MdcUtil.setCrawlSessionId(getId());
        Thread.currentThread().setName(getId() + "/IMPORT");
        lock();
        try {
            initCrawlSession();
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_STORE_IMPORT_BEGIN)
                    .source(this)
                    .build());
            inFiles.forEach(
                    f -> getCrawlers().forEach(c -> c.importDataStore(f)));
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_STORE_IMPORT_END)
                    .source(this)
                    .build());
            destroyCrawlSession();
        } finally {
            eventManager.clearListeners();
            unlock();
        }
    }
    public void exportDataStore(Path dir) {
        MdcUtil.setCrawlSessionId(getId());
        Thread.currentThread().setName(getId() + "/EXPORT");
        lock();
        try {
            initCrawlSession();
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_STORE_EXPORT_BEGIN)
                    .source(this)
                    .build());
            //TODO zip all exported data stores in a single file?
            getCrawlers().forEach(c -> c.exportDataStore(dir));
            eventManager.fire(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_STORE_EXPORT_END)
                    .source(this)
                    .build());
            destroyCrawlSession();
        } finally {
            eventManager.clearListeners();
            unlock();
        }
    }

    protected void destroyCrawlSession() {
        try {
            FileUtil.delete(getTempDir().toFile());
        } catch (IOException e) {
            throw new CrawlSessionException("Could not delete temp directory", e);
        } finally {
            eventManager.clearListeners();
            unlock();
        }
        MDC.clear();
    }

    public void fireStopRequest() {
        stopper.fireStopRequest(this);
    }

    /**
     * Stops a running instance of this CrawlSession. The caller can be a
     * different JVM instance than the instance we want to stop.
     */
    public void stop() {
        if (!isRunning()) {
            LOG.info("CANNOT STOP: CrawlSession is not running.");
            return;
        }
        MdcUtil.setCrawlSessionId(getId());
        Thread.currentThread().setName(getId() + "/STOP");
        eventManager.fire(CrawlSessionEvent.builder()
                .name(CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN)
                .source(this)
                .build());
        try {
            getCrawlers().forEach(Crawler::stop);
        } finally {
            try {
                eventManager.fire(CrawlSessionEvent.builder()
                        .name(CrawlSessionEvent.CRAWLSESSION_STOP_END)
                        .source(this)
                        .build());
                destroyCrawlSession();
            } finally {
                stopper.destroy();
            }
        }
    }

    /**
     * Loads all crawlers from configuration.
     */
    private void createCrawlers() {
        if (getCrawlers().isEmpty()) {
            var crawlerConfigs =
                    crawlSessionConfig.getCrawlerConfigs();
            if (crawlerConfigs != null) {
                for (CrawlerConfig crawlerConfig : crawlerConfigs) {
                    crawlers.add(crawlerFactory.apply(this, crawlerConfig));
                }
            }
        } else {
            LOG.debug("Crawlers already created.");
        }
    }


    private synchronized void ensureDirectories() {
        // Done lazily here instead of in init method as we need directories
        // to exist prior to lock() being invoked (which is invoked before
        // init).
        if (workDir == null || tempDir == null) {
            var cfgWorkDir = Optional
                    .ofNullable(crawlSessionConfig.getWorkDir())
                    .orElseGet(() -> CrawlSessionConfig.DEFAULT_WORK_DIR);
            workDir = cfgWorkDir.resolve(FileUtil.toSafeFileName(getId()));
            tempDir = workDir.resolve("temp");
            try {
                Files.createDirectories(tempDir);
                    // will also create workdir parent
            } catch (IOException e) {
                throw new CrawlSessionException(
                        "Could not create directory: " + workDir, e);
            }
        }
    }

    //TODO rename sessionInfo()??
    //TODO used in a few palces... Move to generic/util class
    public static String getReleaseInfo(CrawlSessionConfig crawlSessionConfig) {
        return new StringBuilder()
            .append(NORCONEX_ASCII)
            .append("\nCrawler and main components:\n")
            .append("\n")
            .append(CrawlSessionUtil.getReleaseInfo(crawlSessionConfig))
            .toString();
    }

    protected synchronized void lock() {
        LOG.debug("Locking CrawlSession execution...");
        lock = new FileLocker(getWorkDir().resolve(".CrawlSession-lock"));
        try {
            lock.lock();
        } catch (FileAlreadyLockedException e) {
            throw new CrawlSessionException("""
                    The CrawlSession you are attempting to run is already\s\
                    running or executing a command. Wait for\s\
                    it to complete or stop it and try again.""");
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Could not create a CrawlSession execution lock.", e);
        }
        LOG.debug("CrawlSession execution locked");
    }

    protected synchronized void unlock() {
        try {
            if (lock != null) {
                lock.unlock();
                LOG.debug("CrawlSession execution unlocked");
            }
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Cannot unlock CrawlSession execution.", e);
        }
        lock = null;
    }

    public boolean isRunning() {
        return lock != null && lock.isLocked();
    }

    @Override
    public String toString() {
        return getId();
    }
}
