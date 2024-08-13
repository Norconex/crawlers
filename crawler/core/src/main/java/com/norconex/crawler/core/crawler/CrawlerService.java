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
package com.norconex.crawler.core.crawler;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.mutable.MutableInt;

import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.session.CrawlSessionService;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.util.IntervalTaskRunner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * All public facing actionable methods influencing the course
 * of a crawler within a crawl session.
 * </p>
 * <p>
 * These action methods are mutually exclusive. Only one can run at any given
 * time.
 * </p>
 * <p>
 * This service also abstracts the synchronizing of actions and states when run
 * on a cluster.
 * </p>
 */
@Slf4j
public class CrawlerService {
    //TODO looks quite a bit like CrawlSession service. Consider merging?

    //NOTE ALL cluster-specific interactions are done here.

    private static final String CLUSTER_STORE_STATE_KEY = "state";

    @Getter
    @NonNull
    private final Crawler crawler;
    private final CrawlSessionConfig sessionConfig;
//    private final CrawlerConfig crawlerConfig;
    private final CrawlerDataStoreEngine crawlerStoreEngine;
    private final IntervalTaskRunner inquireRunner;
    private final IntervalTaskRunner informRunner;
    private final long livenessTimeout;
    private boolean crawlerStoresOpen;
    private boolean syncRunnersStarted;

    // each row of the cluster store is a different type of records
    private DataStore<String> crawlerClusterStore;
    // store keeping information specific to a single crawler along its
    // sibling cluster instances, if any.
    private DataStore<TimestampedState> crawlerInstancesStore;

    private CrawlerState crawlerClusterState = CrawlerState.UNDEFINED;
    private CrawlerState crawlerInstanceState = CrawlerState.UNDEFINED;

    public CrawlerService(@NonNull Crawler crawler) {
        this.crawler = crawler;
        sessionConfig = crawler.getCrawlSession().getCrawlSessionConfig();
//        crawlerConfig = crawler.getConfiguration();
        crawlerStoreEngine = crawler.getCrawlerDataStoreEngine();

        inquireRunner = new IntervalTaskRunner(runnerInterval(
                sessionConfig.getClusterInquireInterval(),
                CrawlSessionConfig.DEFAULT_CLUSTER_INQUIRE_INTERVAL));
        informRunner = new IntervalTaskRunner(runnerInterval(
                sessionConfig.getClusterInformInterval(),
                CrawlSessionConfig.DEFAULT_CLUSTER_INFORM_INTERVAL));
        livenessTimeout = sessionConfig
                .getClusterInformInterval().toMillis() * 4;
    }

    /**
     * Starts crawling.
     */
    public void start() {
        try {
            initCrawlerStores();
            initClusterSyncRunners();
            crawler.start();
        } finally {
            destroy();
        }
    }

    //TODO check if all instances are stop, mark cluster as stopped
    /**
     * Stops a running instance of this crawler. Unlike
     * {@link CrawlSessionService#stop()}, the caller is from the same
     * JVM instance.
     */
    public void stop() {
        try {

            crawler.stop();
        } finally {
            destroy();
        }
    }



    //--- Cluster Actions ------------------------------------------------------

    /**
     * Execute given {@link Runnable} on a single instance of this crawler
     * (relevant on a cluster).
     * The executing crawler will be given the supplied state while others
     * are marked as IDLE.
     * For long running tasks, consider checking for stopping requests and
     * abort the task if stopping.
     *
     * @param state the state of the executing crawler
     * @param runnable task to run
     */
    void onSingleInstance(
            // state for the lucky crawler, others will be IDLE
            @NonNull CrawlerState state,  @NonNull Runnable runnable) {

        //TODO check for expiry as well (part of wellness checks)
        //TODO check current state first and act accordingly (e.g., join
        // an already crawling cluster?)
        var chosenOne = setCrawlerClusterStoreState(state);
        if (chosenOne) {
            LOG.info("Executing this state: {}", state);
            runnable.run();
        } else {
            LOG.info("Waiting for another instance to execute state: {}",
                    state);
            waitUntilCrawlerClusterStateChange(state);
        }
        //TODO do we want to recover if that node goes down?
    }

    /**
     * Execute given {@link Runnable} on all instances of this crawler
     * (relevant on a cluster).
     * All crawlers will be marked with the supplied state
     * while running. When an instance is done, it will be set to IDLE until
     * all instances are done with this task.
     * For long running tasks, consider checking for stopping requests and
     * abort the task if stopping.
     *
     * @param state the state of all crawlers when running
     * @param runnable task to run
     */
    void onAllInstances(
            @NonNull CrawlerState state,  @NonNull Runnable runnable) {

        setCrawlerClusterStoreState(state);
        crawlerInstanceState = state;
        runnable.run();
        crawlerInstanceState = CrawlerState.IDLE;
//        crawlerInstanceState = CrawlerState.IDLE_DONE_TASK;
//        LOG.info("Waiting for all instances to finish "
//                + "executing state: {}", state);
        LOG.info("Waiting for all instances to be done with "
                + "state: {}", state);

        waitUntilCrawlerInstancesStateChange(state);
//        waitUntilAllInstancesAreDone();

        //TODO do we want to recover if that node goes down?
    }

//    /**
//     * Execute given {@link Runnable} on all instances of this crawler
//     * (relevant on a cluster).
//     * All crawlers will be marked with the supplied state
//     * while running. When an instance is done, it will be set to IDLE until
//     * all instances are done with this task.
//     * For long running tasks, consider checking for stopping requests and
//     * abort the task if stopping.
//     *
//     * @param state the state of all crawlers when running
//     * @param runnable task to run
//     */
//    void onAllInstances_OLD(
//            @NonNull CrawlerState state,  @NonNull Runnable runnable) {
//
//        setCrawlerClusterStoreState(state);
//        crawlerInstanceState = state;
//        runnable.run();
//        crawlerInstanceState = CrawlerState.IDLE_DONE_TASK;
//        LOG.info("Waiting for all instances to finish "
//                + "executing state: {}", state);
//        waitUntilAllInstancesAreDone_OLD();
//
//        //TODO do we want to recover if that node goes down?
//    }


    //--- Init methods ---------------------------------------------------------

    void initCrawlerStores() {
        // Do the instance part first that each instances need
        if (crawlerStoresOpen) {
            throw new IllegalStateException("Already initialized.");
        }
        crawlerStoresOpen = true;
        crawlerClusterStore = crawlerStoreEngine.openCrawlerStore(
                "crawler-cluster", String.class);
        crawlerInstancesStore = crawlerStoreEngine.openCrawlerStore(
                "crawler-instances", TimestampedState.class);

        // Do the the part that a single crawler (the chosen one) needs to do
        // for the entire cluster.

        //TODO make sure we got the initial store state before leaving?
    }

    void initClusterSyncRunners() {
        if (syncRunnersStarted) {
            throw new IllegalStateException(
                    "Crawl session service runners already started.");
        }
        syncRunnersStarted = true;

        // <- Keep in sync with the global cluster state
        inquireRunner.start(() -> {
            LOG.debug("Crawler cluster state inquiries started.");
            var prevClusterState = crawlerClusterState;
            crawlerClusterState = CrawlerState.of(
                    crawlerClusterStore.find(
                            CLUSTER_STORE_STATE_KEY).orElse(null));
            if (prevClusterState != crawlerClusterState) {
                reactToClusterStateChange(prevClusterState, crawlerClusterState);
            }
        });

        // -> Tell others what we are up to once in a while so they
        // know we are alive
        informRunner.start(() -> {
            LOG.debug("Cluster state informing started.");
            crawlerInstancesStore.save(crawler.getInstanceId(),
                    new TimestampedState(crawlerInstanceState, Instant.now()));
        });
    }

    //--- Destroy --------------------------------------------------------------

    void destroy() {
        if (inquireRunner != null) {
            inquireRunner.stop();
        }
        if (informRunner != null) {
            informRunner.stop();
        }
        crawlerStoresOpen = false;
        syncRunnersStarted = false;
        crawlerInstancesStore.close();
        crawlerClusterStore.close();
    }

    //--- Misc. ----------------------------------------------------------------

    private boolean setCrawlerClusterStoreState(CrawlerState state) {
        return crawlerClusterStore.save(CLUSTER_STORE_STATE_KEY, state.name());
    }


    private void reactToClusterStateChange(
            CrawlerState oldState, CrawlerState newState) {
//        if (newState == STOPPING) {
//            handleStopRequestFromCluster();
//        }

        //TODO if STOPPED/COMPLETED, trigger some local finalizing?
    }

    // check at short interval if we should move on
    // and check less regularly if the instance doing the work
    // has expired (died).
    private void waitUntilCrawlerClusterStateChange(
            CrawlerState originalState) {
        LOG.info("Waiting until crawler state changes from: {}", originalState);
//        crawlerInstanceState = CrawlerState.IDLE_NOT_CHOSEN;
        crawlerInstanceState = CrawlerState.IDLE;
        var runner = new IntervalTaskRunner(ofNullable(
                sessionConfig.getClusterInquireInterval())
                    .orElse(CrawlSessionConfig.DEFAULT_CLUSTER_INQUIRE_INTERVAL));
        var times = new MutableInt();
        runner.start(() -> {
            if (crawlerClusterState != originalState) {
                runner.stop();
                return;
            }
            // once in a while, check the responsible crawler is still doing
            // its job.
            if (times.incrementAndGet() % 10 == 0) {
                var now = Instant.now();
                crawlerInstancesStore.forEach((k, timedState) -> {
                    if (timedState.state == originalState) {
                        if (timedState.date.plusMillis(
                                livenessTimeout).isBefore(now)) {
                            throw new CrawlerException(
                                "Instance %s of crawler '%s' doing `%s' gave "
                                + "no sign of live for too long.".formatted(
                                        k,
                                        crawler.getId(),
                                        originalState));
                        }
                        return false;
                    }
                    return true;
                });
            }
        });
        runner.waitUntilStopped();
        runner.shutdown();
    }

    // Wait until all instances are no longer of the given state
    private void waitUntilCrawlerInstancesStateChange(
            CrawlerState currentState) {
        LOG.info("Waiting until all crawler instances are no longer: {}",
                currentState);
        var runner = new IntervalTaskRunner(runnerInterval(
                sessionConfig.getClusterInquireInterval(),
                CrawlSessionConfig.DEFAULT_CLUSTER_INQUIRE_INTERVAL
                ));
        runner.start(() -> {
            var now = Instant.now();
            var allDone = crawlerInstancesStore.forEach((k, timedState) -> {
                if (timedState.date.plusMillis(
                        livenessTimeout).isBefore(now)) {
                    throw new CrawlerException(
                        "Instance %s of crawler '%s' doing `%s' gave "
                        + "no sign of live for too long.".formatted(
                                k,
                                crawler.getId(),
                                timedState.getState()));
                }
                return timedState.state != currentState;
            });
            if (allDone) {
                runner.stop();
            }
            //TODO shall we concern ourselves that this instance can be done
            // and switch state before others could start, or is this
            // a non-issue if it happens?
        });
        runner.waitUntilStopped();
        runner.shutdown();
    }

//
//    // when all instances are in "idle" state, it means they are done
//    private void waitUntilAllInstancesAreDone_OLD() {
//        LOG.info("Waiting until all crawler instances become: {}",
//                IDLE_DONE_TASK);
//        var runner = new IntervalTaskRunner(ofNullable(
//                sessionConfig.getClusterInquireInterval())
//                    .orElse(CrawlSessionConfig.DEFAULT_CLUSTER_INQUIRE_INTERVAL));
//        runner.start(() -> {
//            var now = Instant.now();
//            var allDone = crawlerInstancesStore.forEach((k, timedState) -> {
//                if (timedState.date.plusMillis(
//                        livenessTimeout).isBefore(now)) {
//                    throw new CrawlerException(
//                        "Instance %s of crawler '%s' doing `%s' gave "
//                        + "no sign of live for too long.".formatted(
//                                k,
//                                crawler.getId(),
//                                timedState.getState()));
//                }
//                return timedState.state == IDLE_DONE_TASK;
//            });
//            //TODO consider checking if we are on a cluster to find
//            // out if we should wait for the next check interval or if
//            // we want it to be more agressive?
//            if (allDone) {
//                runner.stop();
//            }
//        });
//        runner.waitUntilStopped();
//        runner.shutdown();
//    }

    private Duration runnerInterval(
            Duration cfgDuration, Duration defClusterDuration) {
        if (cfgDuration != null) {
            return cfgDuration;
        }
        if (crawler.getCrawlSession().getDataStoreEngine().clusterFriendly()) {
            return defClusterDuration;
        }
        return Duration.ofMillis(50);
    }


    //--- Inner classes --------------------------------------------------------

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TimestampedState {
        private CrawlerState state;
        private Instant date;
        boolean hasExpired(long millisTimeout) {
            return date.plusMillis(millisTimeout).isBefore(Instant.now());
        }
    }


}
//    private final CrawlerDataStoreEngine storeEngine;
//    private final IntervalTaskRunner inquireRunner;
//    private final IntervalTaskRunner informRunner;
//    private final long livenessTimeout;
//    private boolean sessionStoresOpen;
//    private boolean syncRunnersStarted;

//    // each row of the cluster store is a different type of records
//    private DataStore<String> clusterStore;
//    // store keeping information specific to each crawler instances.
//    private DataStore<TimestampedState> instancesStore;
//
//    private CrawlSessionState clusterState = CrawlSessionState.UNDEFINED;
//    private CrawlSessionState instanceState = CrawlSessionState.UNDEFINED;
//
//    public CrawlerService(@NonNull CrawlSession crawlSession) {
//        this.crawlSession = crawlSession;
//        crawlSessionConfig = crawlSession.getCrawlSessionConfig();
//        inquireRunner = new IntervalTaskRunner(
//                ofNullable(crawlSessionConfig.getClusterInquireInterval())
//                .orElse(CrawlSessionConfig.DEFAULT_INQUIRE_INTERVAL));
//        informRunner = new IntervalTaskRunner(
//                ofNullable(crawlSessionConfig.getClusterInformInterval())
//                .orElse(CrawlSessionConfig.DEFAULT_INFORM_INTERVAL));
//        storeEngine = crawlSession.getDataStoreEngine();
//        livenessTimeout = crawlSessionConfig
//                .getClusterInformInterval().toMillis() * 4;
//    }
//
//    /**
//     * Starts all crawlers defined in configuration.
//     */
//    public void start() {
//        try {
//            initSessionStores();
//            initClusterSyncRunners();
//            crawlSession.start();
//        } finally {
//            destroy();
//        }
//    }
//
//    //TODO check if all instances are stop, mark cluster as stopped
//
//    // Stop cluster
//    /**
//     * Stops a running instance of this crawl session. The caller can be a
//     * different JVM instance or even different server (in a cluster) than
//     * the instance triggering the stop request.
//     */
//    public void stop() {
//        try {
//            initSessionStores();
//            // Inform cluster it needs to stop and it will come back
//            // to live instances and call `handleStopRequestFromCluster` on each
//            // with cluster state being STOPPING
//            setClusterStoreState(STOPPING);
//        } finally {
//            destroy();
//        }
//    }

//    public void clean() {
//        try {
//            initSessionStores();
//            crawlSession.clean();
//        } finally {
//            destroy();
//        }
//    }
//
//    public void importDataStore(Collection<Path> inFiles) {
//        try {
//            initSessionStores();
//            crawlSession.importDataStore(inFiles);
//        } finally {
//            destroy();
//        }
//    }
//
//    public void exportDataStore(Path dir) {
//        try {
//            initSessionStores();
//            crawlSession.exportDataStore(dir);
//        } finally {
//            destroy();
//        }
//    }
//
//    //--- Init methods ---------------------------------------------------------
//
//    private void initSessionStores() {
//        if (sessionStoresOpen) {
//            throw new IllegalStateException("Already initialized.");
//        }
//        sessionStoresOpen = true;
//
//        clusterStore = storeEngine.openStore("session-cluster", String.class);
//        instancesStore = storeEngine.openStore(
//                "session-instances", TimestampedState.class);
//
//        //TODO make sure we got the initial store state before leaving?
//    }
//
//    private void initClusterSyncRunners() {
//        if (syncRunnersStarted) {
//            throw new IllegalStateException(
//                    "Crawl session service runners already started.");
//        }
//        syncRunnersStarted = true;
//
//        // <- Keep in sync with the global cluster state
//        inquireRunner.start(() -> {
//            LOG.debug("Cluster state inquiries started.");
//            var prevClusterState = clusterState;
//            clusterState = CrawlSessionState.of(
//                    clusterStore.find(CLUSTER_STATE_KEY).orElse(null));
//            if (prevClusterState != clusterState) {
//                reactToClusterStateChange(prevClusterState, clusterState);
//            }
//        });
//
//        // -> Tell others what we are up to once in a while so they
//        // know we are alive
//        informRunner.start(() -> {
//            LOG.debug("Cluster state informing started.");
//            instancesStore.save(crawlSession.getInstanceId(),
//                    new TimestampedState(instanceState, Instant.now()));
//        });
//    }
//
//    //--- Destroy --------------------------------------------------------------
//
//    private void destroy() {
//        if (inquireRunner != null) {
//            inquireRunner.stop();
//        }
//        if (informRunner != null) {
//            informRunner.stop();
//        }
//        sessionStoresOpen = false;
//        syncRunnersStarted = false;
//    }
//
//    //--- Misc. ----------------------------------------------------------------
//
//    private boolean setClusterStoreState(CrawlSessionState state) {
//        return clusterStore.save(CLUSTER_STATE_KEY, state.name());
//    }
//
//    private boolean setInstancesStoreState(CrawlSessionState state) {
//        return instancesStore.save(
//                crawlSession.getInstanceId(),
//                new TimestampedState(state, Instant.now()));
//    }
//
//    private void reactToClusterStateChange(
//            CrawlSessionState oldState, CrawlSessionState newState) {
//        if (newState == STOPPING) {
//            handleStopRequestFromCluster();
//        }
//
//        //TODO if STOPPED/COMPLETED, trigger some local finalizing?
//    }
//
//    private void handleStopRequestFromCluster() {
//        if (clusterState == STOPPING && !instanceState.isDoneRunning()) {
//            // Cluster is stopping, better stop myself
//            instanceState = STOPPING;
//            crawlSession.stop();
//            instanceState = STOPPED;
//            // inform others we stopped
//            setInstancesStoreState(STOPPED);
//            // If all instances stopped, also inform the cluster we're all
//            // done. It will be true for the last one stopped
//            if (instancesStore.forEach((id, inst) -> (
//                    inst.state.isDoneRunning()
//                    || inst.hasExpired(livenessTimeout)))) {
//                setClusterStoreState(STOPPED);
//            }
//        }
//    }
//
//
//    //--- Inner classes --------------------------------------------------------
//
//    @Data
//    @AllArgsConstructor
//    @NoArgsConstructor
//    static class TimestampedState {
//        private CrawlSessionState state;
//        private Instant date;
//        boolean hasExpired(long millisTimeout) {
//            return date.plusMillis(millisTimeout).isBefore(Instant.now());
//        }
//    }
//}
//
//
////TODO public start/stop/etc are here
//// and equivalent methods on crawler are package scope
//
//// rethink stop... always listen for request on port X
//// when stopped from another JVM, check if lock file... if so,
//// can the port that is in the lock.
//
//// for cluster, set it in the table.
//
//// the first method will invoke the second but not the other way around.