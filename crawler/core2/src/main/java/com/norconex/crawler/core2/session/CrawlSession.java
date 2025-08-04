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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core2.CrawlerException;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.Cluster;
import com.norconex.crawler.core2.cluster.ClusterNode;
import com.norconex.crawler.core2.context.CrawlContext;
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

    //TODO have state methods, like isQueueInitialized? or is that too specific
    // to an action (crawl action).

    private static final String SESSION_SNAPSHOT_KEY = "session.snapshot";
    private static final String SESSION_STATE_KEY = "session.state";
    private static final String START_REFS_QUEUED_KEY =
            "session.startRefsQueuingComplete";

    //TODO make these timeouts configurable?
    private static final long SESSION_HEARTBEAT_INTERVAL =
            Duration.ofMinutes(2).toMillis();
    private static final long SESSION_TIMEOUT =
            Duration.ofMinutes(4).toMillis();

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
    private Cache<String> sessionCache;
    private Snapshot snapshot;
    private State state;
    private ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(1);

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
    public String getSessionId() {
        return snapshot.getCrawlSessionId();
    }

    /**
     * A crawl run unique identifier, which is renewed every time the
     * crawler is launched, regardless whether it was resumed or not.
     * @return crawl run id
     */
    public String getRunId() {
        return snapshot.getCrawlRunId();
    }

    public boolean isExpired() {
        return System.currentTimeMillis()
                - state.getLastUpdated() > SESSION_TIMEOUT;
    }

    public CrawlMode getCrawlMode() {
        return snapshot.getCrawlMode();
    }

    public LaunchMode getLaunchMode() {
        return snapshot.getLaunchMode();
    }

    public CrawlState getCrawlState() {
        // always get it fresh when requesting it explicitly
        state = loadState();
        return state.crawlState;
    }

    @Override
    public void close() {
        LOG.info("Closing CrawlSession...");

        LOG.info("Closing heartbeat scheduler...");
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Heartbeat scheduler did not terminate in time.");
            } else {
                LOG.info("Heartbeat scheduler closed.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn(
                    "Interrupted while waiting for heartbeat scheduler to terminate.",
                    e);
        }
        ExceptionSwallower.close(crawlContext, cluster);
        if (cluster.getLocalNode() != null) {
            SESSIONS.remove(cluster.getLocalNode().getNodeName());
        }

        LOG.info("CrawlSession closed.");

    }

    void init() {
        createDir(crawlContext.getTempDir()); // also creates workDir
        cluster.init(crawlContext.getWorkDir());
        sessionCache = cluster.getCacheManager().getCache(
                "sessionCache", String.class);
        SESSIONS.put(cluster.getLocalNode().getNodeName(), this);
        try {
            state = cluster.getTaskManager().runOnOneOnceSync( //NOSONAR
                    "resolveState", sess -> resolveState()).get();
            snapshot = cluster.getTaskManager().runOnOneOnceSync( //NOSONAR
                    "resolveSession", sess -> resolveSnapshotOnce()).get();
            scheduleHeartbeat();
            crawlContext.init(this);
        } catch (RuntimeException e) {
            SESSIONS.remove(cluster.getLocalNode().getNodeName());
            throw e;
        }
    }

    private State resolveState() {
        // the first time we grab whatever existing state, then we schedule.
        var astate = loadState();
        if (astate == null) {
            // no state found, assume we're starting it
            astate = new State().setCrawlState(CrawlState.RUNNING)
                    .setLastUpdated(System.currentTimeMillis());
            saveCrawlState(astate);
        }
        return astate;

    }

    private void scheduleHeartbeat() {
        // on top of getting the latest status we define a slow hearbeat
        // scheduler in each node, but we ask that only one node updates it
        // with the latest status/date.
        // This is mainly so joining and idle nodes can know if
        // the cluster is still active,
        heartbeatScheduler.scheduleAtFixedRate(
                this::beatHeart,
                SESSION_HEARTBEAT_INTERVAL,
                SESSION_HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS);

    }

    private void beatHeart() {
        state = cluster.getTaskManager().runOnOneOnceSync( //NOSONAR
                "session.heartbeat", sess -> {
                    var st = new State().setCrawlState(state.crawlState)
                            .setLastUpdated(System.currentTimeMillis());
                    saveCrawlState(st);
                    return st;
                }).get();
    }

    private Snapshot resolveSnapshotOnce() {
        var snap = doResolveSnapshotOnce();
        sessionCache.put(SESSION_SNAPSHOT_KEY, SerialUtil.toJsonString(snap));
        return snap;
    }

    private Snapshot doResolveSnapshotOnce() {
        var id = getCrawlerId();
        var snapshotOpt = sessionCache.get(SESSION_SNAPSHOT_KEY);
        if (snapshotOpt.isEmpty()) {
            LOG.info("No previous crawl session detected for crawler {}. "
                    + "Starting a new full crawl session.", id);
            var uuid = UUID.randomUUID().toString();
            return new Snapshot()
                    .setCrawlerId(getCrawlerId())
                    .setCrawlSessionId(uuid)
                    .setCrawlRunId(uuid)
                    .setCrawlMode(CrawlMode.FULL)
                    .setLaunchMode(LaunchMode.NEW);
        }

        var snap = SerialUtil.fromJson(snapshotOpt.get(), Snapshot.class);

        return switch (state.crawlState) {
            case RUNNING -> {
                if (isExpired()) {
                    LOG.warn("A crawl session for crawler {} was "
                            + "detected but expired. Trying to resume it.", id);
                    snap.setLaunchMode(LaunchMode.RESUMED)
                            .setCrawlRunId(UUID.randomUUID().toString());
                } else {
                    LOG.info("Joining crawl session for crawler {}.", id);
                }
                yield snap;
            }
            case PAUSED -> {
                LOG.info("A previously paused crawl session was detected for "
                        + "crawler {}. Resuming it.", id);
                snap.setLaunchMode(LaunchMode.RESUMED)
                        .setCrawlRunId(UUID.randomUUID().toString());
                yield snap;
            }
            case COMPLETED -> {
                LOG.info("""
                    A previously completed crawl session was detected \
                    for crawler {}. Starting a new incremental crawl \
                    session.""", id);
                var uuid = UUID.randomUUID().toString();
                snap.setCrawlMode(CrawlMode.INCREMENTAL)
                        .setLaunchMode(LaunchMode.NEW)
                        .setCrawlSessionId(uuid)
                        .setCrawlRunId(uuid);
                yield snap;
            }
            case FAILED -> {
                LOG.warn("A crawl session for crawler {} was detected but is "
                        + "marked as failed. Trying to resume it.", id);
                snap.setLaunchMode(LaunchMode.RESUMED)
                        .setCrawlRunId(UUID.randomUUID().toString());
                yield snap;
            }
        };
    }

    /**
     * Sets a session attribute as a string.
     * @param key attribute key
     * @param value attribute value
     */
    public void setString(String key, String value) {
        sessionCache.put(key, value);
    }

    /**
     * Gets a session attribute as a string.
     * @param key attribute key
     * @return value attribute value
     */
    public Optional<String> getString(String key) {
        return sessionCache.get(key);
    }

    /**
     * Sets a session attribute as a boolean.
     * @param key attribute key
     * @param value attribute value
     */
    public void setBoolean(String key, boolean value) {
        sessionCache.put(key, Boolean.toString(value));
    }

    /**
     * Gets a session attribute as a boolean.
     * @param key attribute key
     * @return value attribute value
     */
    public boolean getBoolean(String key) {
        return sessionCache.get(key).map(Boolean::parseBoolean).orElse(false);
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

    private void saveCrawlState(State state) {
        sessionCache.put(SESSION_STATE_KEY, SerialUtil.toJsonString(state));
    }

    private State loadState() {
        return SerialUtil.fromJson(
                sessionCache.getOrDefault(SESSION_STATE_KEY, null),
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
    static class Snapshot {
        private String crawlerId;
        private String crawlSessionId;
        private String crawlRunId;
        private CrawlMode crawlMode;
        private LaunchMode launchMode;
    }

    @Data
    @Accessors(chain = true)
    static class State {
        private CrawlState crawlState;
        private long lastUpdated;
    }
}
