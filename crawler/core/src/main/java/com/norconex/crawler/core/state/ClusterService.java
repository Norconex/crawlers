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
package com.norconex.crawler.core.state;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.store.DataStore;

import lombok.Getter;
import lombok.NonNull;

public class ClusterService implements Closeable {

    // just the state
    private static final String STATE_RECORD = "state";
    // when it last started, last ended, etc.
    private static final String META_RECORD = "meta";

    //TODO make this configurable
    private static final long PING_INTERVAL = Duration.ofSeconds(5).toMillis();

    private final Crawler crawler;
    private boolean open;
    // each row of global store is a different type of records
    private DataStore<String> globalStore;
    private DataStore<String> crawlersStore;

    //TODO Do we instead wait for it to be requested with a min time buffer
    // instead of scheduling?
    private ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    @Getter
    private Optional<ClusterSnapshot> snapshot = Optional.empty();

    @Getter
    private ClusterState state = ClusterState.UNDEFINED;

    public ClusterService(@NonNull Crawler crawler) {
        this.crawler = crawler;
    }

    // two stores? 1 for crawler cluster state
    //             1 where info on each crawler is stored
    //                 (like if they are done stopping)


    public void open() {
        if (open) {
            throw new IllegalStateException("Already open.");
        }
        var storeEngine = crawler.getDataStoreEngine();
        globalStore = storeEngine.openStore(
                "cluster_global_state", String.class);
        crawlersStore = storeEngine.openStore(
                "cluster_crawlers_state", String.class);

        executor.scheduleAtFixedRate(() -> {
            state = ClusterState.of(
                    globalStore.find(STATE_RECORD).orElse(null));
            //TODO record an "alive" ping as the same time.
        }, 0, PING_INTERVAL, TimeUnit.MILLISECONDS);

    }

    public ClusterState queueInit(Runnable runnable) {

        //TODO, hold the current thread until queue init is done
        // (which may be right away if async).
        // and return the next cluster state (CRAWLING or STOPPING)
        return ClusterState.CRAWLING;
    }

    @Override
    public void close() {
        executor.shutdown();
        open = false;
    }
}

//TODO have the store also take a status metadata field for extra
// info that accompany the status (like the number of crawlers left to
// do something)?


// introduce the concept of cluster ID, which is a UUID created at
// runtime when starting (not part of config) and used to identify each
// crawler uniquely in the crawl store or cluster.



//TODO have event listener and start a thread on init or pre-init that
// will fetch every X seconds the new state.