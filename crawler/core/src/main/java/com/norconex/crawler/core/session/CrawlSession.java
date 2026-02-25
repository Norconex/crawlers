/* Copyright 2025 Norconex Inc.
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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.admin.ClusterAdminServer;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * A crawl session, representing a crawl from begin to finish, which may
 * include zero to multiple "resumes" from stops, or failures. There is
 * one crawl session per cluster, each node having their own instance
 * of this class, exposing the same information.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class CrawlSession implements Closeable {

    @Getter
    private final Cluster cluster;
    private ClusterAdminServer adminServer;
    @Getter
    private final CrawlContext crawlContext;

    /** Coordinates task deduplication across session and run scopes. */
    @Getter
    private CrawlRunCoordinator runCoordinator;

    /** Manages persistent crawl state and session-scoped attributes. */
    @Getter
    private CrawlStateStore stateStore;

    /** Fires events through the underlying event manager. */
    @Getter
    private CrawlEventBus eventBus;

    private CrawlAttributes crawlerAttributes;

    private CrawlRunInfo crawlRunInfo;
    private boolean closed;
    private Runnable postCloseCleanup;

    /**
     * The crawler unique identifier, for a specific crawler setup
     * and does not change across sessions. Same as configured in the crawler
     * configuration.
     * @return crawler id
     */
    @ToString.Include(name = "crawlerId")
    public String getCrawlerId() {
        return crawlContext.getId();
    }

    public void oncePerSession(String taskId, Runnable runnable) {
        runCoordinator.oncePerSession(taskId, runnable);
    }

    public <T> T oncePerSessionAndGet(String taskId, Supplier<T> supplier) {
        return runCoordinator.oncePerSessionAndGet(taskId, supplier);
    }

    public void oncePerRun(String taskId, Runnable runnable) {
        runCoordinator.oncePerRun(taskId, runnable);
    }

    public <T> T oncePerRunAndGet(String taskId, Supplier<T> supplier) {
        return runCoordinator.oncePerRunAndGet(taskId, supplier);
    }

    /**
     * A crawl session unique identifier, which represents a
     * begin-to-end crawl, regardless how many times it may have been
     * resumed due to stops or failures. Resuming crawls will keep the
     * same session id.
     * @return crawl session id
     */
    @ToString.Include(name = "crawlSessionId")
    public String getCrawlSessionId() {
        return crawlRunInfo.getCrawlSessionId();
    }

    /**
     * A crawl run unique identifier, which is renewed every time the
     * crawler is launched, regardless whether we are resuming an existing
     * session or creating a new one.
     * @return crawl run id
     */
    @ToString.Include(name = "crawlRunId")
    public String getCrawlRunId() {
        return crawlRunInfo.getCrawlRunId();
    }

    @ToString.Include(name = "incremental")
    public boolean isIncremental() {
        return crawlRunInfo.getCrawlMode() == CrawlMode.INCREMENTAL;
    }

    @ToString.Include(name = "resumed")
    public boolean isResumed() {
        return crawlRunInfo.getCrawlResumeState() == CrawlResumeState.RESUMED;
    }

    @ToString.Include(name = "crawlState")
    public CrawlState getCrawlState() {
        return stateStore.getCrawlState();
    }

    @ToString.Include
    public boolean isClosed() {
        return closed;
    }

    public CrawlAttributes getSessionAttributes() {
        if (stateStore == null) {
            throw new IllegalStateException("CrawlSession not initialized.");
        }
        return stateStore.getSessionAttributes();
    }

    public CrawlAttributes getCrawlRunAttributes() {
        if (runCoordinator == null) {
            throw new IllegalStateException("CrawlSession not initialized.");
        }
        return runCoordinator.getCrawlRunAttributes();
    }

    public CrawlAttributes getCrawlerAttributes() {
        if (crawlerAttributes == null) {
            throw new IllegalStateException("CrawlSession not initialized.");
        }
        return crawlerAttributes;
    }

    void init() {
        if (closed) {
            throw new IllegalStateException(
                    "Cannot initialize a closed CrawlSession.");
        }
        var clusterConfig = crawlContext.getCrawlConfig().getClusterConfig();

        LOG.info("CrawlSession.init() - Creating directories...");
        createDir(crawlContext.getTempDir()); // also creates workDir

        LOG.info("CrawlSession.init() - Initializing cluster...");
        cluster.init(crawlContext.getWorkDir(), clusterConfig.isClustered());

        LOG.info("CrawlSession.init() - Getting cache managers...");
        var cacheManager = cluster.getCacheManager();
        var sessionCache = cacheManager.getCrawlSessionCache();
        var runCache = cacheManager.getCrawlRunCache();
        stateStore = new CrawlStateStore(
                sessionCache, new CrawlAttributes(sessionCache));
        runCoordinator = new CrawlRunCoordinator(
                sessionCache, runCache, new CrawlAttributes(runCache));
        eventBus = new CrawlEventBus(crawlContext.getEventManager());
        crawlerAttributes = new CrawlAttributes(cacheManager.getCrawlerCache());

        var nodeName = cluster.getLocalNode().getNodeName();
        LOG.info("CrawlSession.init() - Binding session to cluster node: {}",
                nodeName);
        cluster.bindSession(this);

        try {
            //NOTE: this resolver will also clear the session cache if needed.
            // The crawlRunCache does not need clearing as it is ephemeral
            LOG.info("CrawlSession.init() - Resolving crawl run info...");
            crawlRunInfo = CrawlRunInfoResolver.resolve(this);
            LOG.info("CrawlSession.init() - Crawl run info resolved: "
                    + "mode={}, resumeState={}",
                    crawlRunInfo.getCrawlMode(),
                    crawlRunInfo.getCrawlResumeState());

            //TODO will always setting it have an impact for non crawl commands?
            if (cluster.getLocalNode().isCoordinator()) {
                LOG.info("CrawlSession.init() - Updating crawl state to "
                        + "RUNNING (coordinator)...");
                updateCrawlState(CrawlState.RUNNING);
                LOG.info("CrawlSession.init() - Crawl state updated "
                        + "successfully");
            } else {
                LOG.info("CrawlSession.init() - Skipping state update "
                        + "(not coordinator)");
            }

            LOG.info("CrawlSession.init() - Initializing crawl context...");
            crawlContext.init(this);
            LOG.info("CrawlSession.init() - Crawl context initialized "
                    + "successfully");

            // Start the cluster admin server
            if (!clusterConfig.isAdminDisabled()) {
                LOG.info("CrawlSession.init() - Starting cluster "
                        + "admin server...");
                adminServer = new ClusterAdminServer(this);
                adminServer.start();
                LOG.info("CrawlSession.init() - Cluster admin server started");
            } else {
                LOG.info("CrawlSession.init() - Cluster admin server is "
                        + "disabled");
            }

            LOG.info("CrawlSession.init() - Initialization complete!");
        } catch (RuntimeException e) {
            LOG.error("CrawlSession.init() - Initialization failed!", e);
            cluster.bindSession(null);
            throw e;
        }
    }

    @Override
    public void close() {
        if (closed) {
            LOG.debug("CrawlSession already closed.");
            return;
        }
        closed = true;

        LOG.info("Closing CrawlSession...");

        ExceptionSwallower.runWithInterruptClear(() -> {
            ExceptionSwallower.close(crawlContext, cluster);
        });

        cluster.bindSession(null);

        if (adminServer != null) {
            adminServer.close();
        }

        LOG.info("CrawlSession closed.");

        // Run post-close cleanup (e.g., logger stop) AFTER all
        // AutoCloseable resources have been closed and all cleanup
        // events have been fired
        if (postCloseCleanup != null) {
            ExceptionSwallower.swallowQuietly(postCloseCleanup::run);
        } else {
        }
    }

    /**
     * Registers a cleanup action to run AFTER the session has closed
     * all its resources. This is useful for components like loggers
     * that need to capture all session cleanup events before shutting down.
     * @param cleanup the cleanup action to run after session close
     */
    public void setPostCloseCleanup(Runnable cleanup) {
        postCloseCleanup = cleanup;
    }

    public int getAdminPort() {
        return adminServer.getPort();
    }

    public boolean isStartRefsQueueingComplete() {
        return stateStore.isStartRefsQueueingComplete();
    }

    public void setStartRefsQueueingComplete(boolean isComplete) {
        stateStore.setStartRefsQueueingComplete(isComplete);
    }

    public void updateCrawlState(CrawlState state) {
        stateStore.updateCrawlState(state);
    }

    /**
     * Shortcut method for firing an event equivalent to:
     * <code>session.getCrawlContext().getEventManager().fire(event)</code>.
     * @param event the event to fire
     */
    public void fire(Event event) {
        eventBus.fire(event);
    }

    /**
     * Shortcut method for firing a {@link CrawlerEvent} with the supplied
     * source and with the <code>crawlSession</code> field already set.
     * Equivalent to:
     * <code>
     * session.getCrawlContext().getEventManager().fire(crawlerEvent)
     * </code>.
     * @param eventName name of the event to fire
     * @param source the event source
     */
    public void fire(String eventName, Object source) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .crawlSession(this)
                .source(source)
                .build());
    }

    //TODO make package private
    public State loadState() {
        return stateStore.loadState();
    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: " + dir, e);
        }
    }

    @Data
    @Accessors(chain = true)
    //TODO make package private
    public static class State {
        private CrawlState crawlState;
        private long lastUpdated;
    }
}
