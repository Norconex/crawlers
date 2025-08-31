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
package com.norconex.crawler.core2.session;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.session.CrawlResumeState;
import com.norconex.crawler.core.session.CrawlRunInfo;
import com.norconex.crawler.core.session.CrawlRunInfoResolver;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.ClusterNode;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.util.ExceptionSwallower;
import com.norconex.crawler.core2.util.SerialUtil;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
public class CrawlSession implements Closeable {

    private static final String SESSION_STATE_KEY = "session.state";
    private static final String START_REFS_QUEUED_KEY =
            "session.startRefsQueuingComplete";

    //TODO make this timeouts configurable?
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
    @Getter
    private final CrawlContext crawlContext;
    private Cache<String> crawlSessionCache;
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
    public String getCrawlerId() {
        return crawlContext.getId();
    }

    /**
     * A crawl session unique identifier, which represents a
     * begin-to-end crawl, regardless how many times it may have been
     * resumed due to stops or failures. Resuming crawls will keep the
     * same session id.
     * @return crawl session id
     */
    public String getCrawlSessionId() {
        return crawlRunInfo.getCrawlSessionId();
    }

    /**
     * A crawl run unique identifier, which is renewed every time the
     * crawler is launched, regardless whether we are resuming an existing
     * session or creating a new one.
     * @return crawl run id
     */
    public String getCrawlRunId() {
        return crawlRunInfo.getCrawlRunId();
    }

    public boolean isIncremental() {
        return crawlRunInfo.getCrawlMode() == CrawlMode.INCREMENTAL;
    }

    @Deprecated
    public CrawlMode getCrawlMode() {
        return crawlRunInfo.getCrawlMode();
    }

    public boolean isResumed() {
        return crawlRunInfo.getCrawlResumeState() == CrawlResumeState.RESUMED;
    }

    @Deprecated
    public CrawlResumeState getResumeState() {
        return crawlRunInfo.getCrawlResumeState();
    }

    public CrawlState getCrawlState() {
        // always get it fresh when requesting it explicitly
        state = loadState();
        return state.crawlState;
    }

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
        SESSIONS.put(cluster.getLocalNode().getNodeName(), this);
        System.err.println("XXX session initialized.");
        try {
            //NOTE: this resolver will also clear the session cache if needed.
            // The crawlRunCache does not need clearing as it is ephemeral
            crawlRunInfo = CrawlRunInfoResolver.resolve(this);

            //TODO will always setting it have an impact for non crawl commands?
            if (cluster.getLocalNode().isCoordinator()) {
                updateCrawlState(CrawlState.RUNNING);
            }
            //            scheduleHeartbeat();
            crawlContext.init(this);
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

        //        LOG.info("Closing heartbeat scheduler...");
        //        if (heartbeatFuture != null) {
        //            heartbeatFuture.cancel(true);
        //        }
        //        heartbeatScheduler.shutdown();
        //        try {
        //            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        //                heartbeatScheduler.shutdownNow();
        //            } else {
        //                LOG.info("Heartbeat scheduler closed.");
        //            }
        //        } catch (InterruptedException e) {
        //            Thread.currentThread().interrupt();
        //            heartbeatScheduler.shutdownNow();
        //            LOG.warn(
        //                    "Interrupted while waiting for heartbeat scheduler to terminate.",
        //                    e);
        //        }
        //        heartbeatScheduler = null;
        //        heartbeatFuture = null;
        ExceptionSwallower.runWithInterruptClear(() -> {
            ExceptionSwallower.close(crawlContext, cluster);
        });
        if (cluster.getLocalNode() != null) {
            SESSIONS.remove(cluster.getLocalNode().getNodeName());
        }

        LOG.info("CrawlSession closed.");

    }

    //    private void scheduleHeartbeat() {
    //        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
    //                this::beatHeart,
    //                SESSION_HEARTBEAT_INTERVAL,
    //                SESSION_HEARTBEAT_INTERVAL,
    //                TimeUnit.MILLISECONDS);
    //    }

    //    private void beatHeart() {
    //        if (closed || cluster == null) {
    //        }
    //        // Heartbeat must execute every interval. Using runOnOneOnce would
    //        // prevent subsequent executions on the same session. We only want
    //        // exactly one node to perform each heart beat invocation, but the
    //        // task itself must be repeatable across the life of the session.
    //
    //        //TODO need to migrate this? I think it is done by cluster now
    //        //        state = cluster.getTaskManager().runOnOneSync( //NOSONAR
    //        //                "session.heartbeat", sess -> {
    //        //                    var currentState = sess.loadState();
    //        //                    var st = new State().setCrawlState(currentState.crawlState)
    //        //                            .setLastUpdated(System.currentTimeMillis());
    //        //                    sess.saveCrawlState(st);
    //        //                    return st;
    //        //                }).get();
    //    }

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

    private void saveCrawlState(State state) {
        crawlSessionCache.put(SESSION_STATE_KEY,
                SerialUtil.toJsonString(state));
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

    @Data
    @Accessors(chain = true)
    //TODO make package private
    public static class State {
        private CrawlState crawlState;
        private long lastUpdated;
    }
}
