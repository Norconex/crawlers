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
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterNode;
import com.norconex.crawler.core.cluster.SerializedEnvelope;
import com.norconex.crawler.core.cluster.admin.ClusterAdminServer;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.util.ExceptionSwallower;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
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

    private static final String SESSION_STATE_KEY = "session.state";
    private static final String START_REFS_QUEUED_KEY =
            "session.startRefsQueuingComplete";

    // Storage markers for oncePerCacheAndGet encoded values.
    // - #NULL#: explicit null
    // - #B64#: Java-Serializable value, Base64 encoded
    // - #SSO#: JSON-wrapped {className, serializedJson}
    private static final String MARKER_NULL = "#NULL#";
    private static final String MARKER_B64 = "#B64#";
    private static final String MARKER_WRAPPED_JSON = "#SSO#";

    //    private static final long SESSION_HEARTBEAT_INTERVAL =
    //            Duration.ofMinutes(2).toMillis();

    // <address, ...>
    private static final Map<String, CrawlSession> SESSIONS =
            new ConcurrentHashMap<>();

    // Shall we have such a class that has the Cluster instance and the crawl
    // CrawlContext instance and pass that accross as opposed to have a circular
    // reference in both crawlcontext and cluster?

    // It could also have key elements on it, like QueueInitialized... and
    // interfaces to add features should ...

    @Getter
    private final Cluster cluster;
    private ClusterAdminServer adminServer;
    @Getter
    private final CrawlContext crawlContext;
    private Cache<String> crawlSessionCache;
    private Cache<String> crawlRunCache;
    private CrawlRunInfo crawlRunInfo;
    private State state;
    //    private ScheduledExecutorService heartbeatScheduler =
    //            Executors.newScheduledThreadPool(1, r -> {
    //                var t = new Thread(r, "heartbeat-scheduler");
    //                t.setDaemon(true);
    //                return t;
    //            });
    //    private java.util.concurrent.ScheduledFuture<?> heartbeatFuture;
    private boolean closed;
    private Runnable postCloseCleanup;

    /**
     * Gets the crawl session associated with the given cluster node.
     * Throws an {@link IllegalStateException} if no crawl session
     * was created and initialized for the node.
     * @param clusterNode the cluster node
     * @return the crawl session
     */
    public static CrawlSession get(@NonNull ClusterNode clusterNode) {
        var session = SESSIONS.get(clusterNode.getNodeName());
        if (session == null) {
            throw new IllegalStateException("Crawl session not created or "
                    + "initialized for node '%s'"
                            .formatted(clusterNode.getNodeName()));
        }
        return session;
    }

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
        oncePerCache(crawlSessionCache, taskId, runnable);
    }

    public <T> T oncePerSessionAndGet(String taskId, Supplier<T> supplier) {
        return oncePerCacheAndGet(crawlSessionCache, taskId, supplier);
    }

    public void oncePerRun(String taskId, Runnable runnable) {
        oncePerCache(crawlRunCache, taskId, runnable);
    }

    public <T> T oncePerRunAndGet(String taskId, Supplier<T> supplier) {
        return oncePerCacheAndGet(crawlRunCache, taskId, supplier);
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
        // always get it fresh when requesting it explicitly
        state = loadState();
        return state != null ? state.crawlState : null;
    }

    @ToString.Include
    public boolean isClosed() {
        return closed;
    }

    void init() {
        if (closed) {
            throw new IllegalStateException(
                    "Cannot initialize a closed CrawlSession.");
        }
        createDir(crawlContext.getTempDir()); // also creates workDir
        cluster.init(crawlContext.getWorkDir());
        crawlSessionCache = cluster.getCacheManager().getCrawlSessionCache();
        crawlRunCache = cluster.getCacheManager().getCrawlRunCache();
        SESSIONS.put(cluster.getLocalNode().getNodeName(), this);
        try {
            //NOTE: this resolver will also clear the session cache if needed.
            // The crawlRunCache does not need clearing as it is ephemeral
            crawlRunInfo = CrawlRunInfoResolver.resolve(this);
            //START TEST
            //            crawlContext.init(this);
            //END TEST
            //TODO will always setting it have an impact for non crawl commands?
            if (cluster.getLocalNode().isCoordinator()) {
                updateCrawlState(CrawlState.RUNNING);
            }
            //            scheduleHeartbeat();

            crawlContext.init(this);

            // Start the cluster admin server
            if (!crawlContext.getCrawlConfig().isClusterAdminDisabled()) {
                adminServer = new ClusterAdminServer(this);
                adminServer.start();
            } else {
                LOG.info("Cluster admin server is disabled.");
            }
        } catch (RuntimeException e) {
            SESSIONS.remove(cluster.getLocalNode().getNodeName());
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

        if (cluster.getLocalNode() != null) {
            SESSIONS.remove(cluster.getLocalNode().getNodeName());
        }

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

    /**
     * Sets a session attribute as a string.
     * @param key attribute key
     * @param value attribute value
     */
    public void setString(String key, String value) {
        crawlSessionCache.put(key, value);
    }

    /**
     * Gets a session attribute as a string.
     * @param key attribute key
     * @return value attribute value
     */
    public Optional<String> getString(String key) {
        return crawlSessionCache.get(key);
    }

    /**
     * Sets a session attribute as a boolean.
     * @param key attribute key
     * @param value attribute value
     */
    public void setBoolean(String key, boolean value) {
        crawlSessionCache.put(key, Boolean.toString(value));
    }

    /**
     * Gets a session attribute as a boolean.
     * @param key attribute key
     * @return value attribute value
     */
    public boolean getBoolean(String key) {
        return crawlSessionCache.get(key).map(Boolean::parseBoolean)
                .orElse(false);
    }

    /**
     * Atomically sets a session attribute as a boolean if it is not
     * already set. This is a cluster-safe operation that prevents race
     * conditions in distributed environments.
     * @param key attribute key
     * @param value attribute value to set if key is absent
     * @return true if the value was set (key was absent), false if the
     *         key already existed
     */
    public boolean setBooleanIfAbsent(String key, boolean value) {
        var previousValue =
                crawlSessionCache.putIfAbsent(
                        key, Boolean.toString(value));
        return previousValue == null;
    }

    public boolean isStartRefsQueueingComplete() {
        return getBoolean(START_REFS_QUEUED_KEY);
    }

    public void setStartRefsQueueingComplete(boolean isComplete) {
        setBoolean(START_REFS_QUEUED_KEY, isComplete);
    }

    public void updateCrawlState(CrawlState state) {
        saveCrawlState(new State().setCrawlState(state)
                .setLastUpdated(System.currentTimeMillis()));
    }

    /**
     * Shortcut method for firing an event equivalent to:
     * <code>session.getCrawlContext().getEventManager().fire(event)</code>.
     * @param event the event to fire
     */
    public void fire(Event event) {
        crawlContext.getEventManager().fire(event);
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
        return SerialUtil.fromJson(
                crawlSessionCache.getOrDefault(SESSION_STATE_KEY, null),
                State.class);
    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: " + dir, e);
        }
    }

    private void saveCrawlState(State state) {
        crawlSessionCache.put(SESSION_STATE_KEY,
                SerialUtil.toJsonString(state));
    }

    private void oncePerCache(
            Cache<String> cache, String taskId, Runnable runnable) {
        var key = "once-" + taskId;
        cache.computeIfAbsent(key, k -> {
            runnable.run();
            return "1";
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T oncePerCacheAndGet(
            Cache<String> cache, String taskId, Supplier<T> supplier) {
        var key = "once-get-" + taskId;
        var stored = cache.computeIfAbsent(key, k -> {
            var value = supplier.get();
            if (value == null) {
                return MARKER_NULL; // explicit null marker
            }
            if (value instanceof Serializable serializable) {
                return MARKER_B64 + SerialUtil.toBase64String(serializable);
            }
            // Fallback: use neutral envelope for JSON payload + class name
            var env = SerializedEnvelope.wrap(value);
            return MARKER_WRAPPED_JSON + SerialUtil.toJsonString(env);
        });
        if (stored == null || MARKER_NULL.equals(stored)) {
            return null;
        }
        if (stored.startsWith(MARKER_B64)) {
            return (T) SerialUtil.fromBase64String(
                    stored.substring(MARKER_B64.length()));
        }
        if (stored.startsWith(MARKER_WRAPPED_JSON)) {
            var json = stored.substring(MARKER_WRAPPED_JSON.length());
            var env = SerialUtil.fromJson(json, SerializedEnvelope.class);
            if (env == null) {
                return null;
            }
            try {
                return (T) env.unwrap();
            } catch (CrawlerException e) {
                LOG.warn(
                        "Could not resolve class for JSON-wrapped value of key '{}': {}. Returning null.",
                        key, e.getMessage());
                return null;
            }
        }
        throw new CrawlerException(
                ("Unknown encoding for oncePerCacheAndGet key '%s'.")
                        .formatted(key));
    }

    @Data
    @Accessors(chain = true)
    //TODO make package private
    public static class State {
        private CrawlState crawlState;
        private long lastUpdated;
    }
}
