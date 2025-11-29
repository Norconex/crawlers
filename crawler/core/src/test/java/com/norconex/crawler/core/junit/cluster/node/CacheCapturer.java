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
package com.norconex.crawler.core.junit.cluster.node;

import java.util.function.Consumer;

import com.norconex.crawler.core.junit.cluster.state.StateDbClient;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Store node events in state database. Registered by
 * {@link DriverInstrumentor}.
 */
@Slf4j
public class CacheCapturer implements Consumer<CrawlSession> {

    private StateDbClient stateDb = StateDbClient.get();

    @Override
    public void accept(CrawlSession session) {
        LOG.info("Coordinator is exporting cache to state DB...");

        var cacheManager = session.getCluster().getCacheManager();
        cacheManager
                .forEach((name, cache) -> cache.forEach((key, obj) -> stateDb
                        .saveCacheEntry(name, key, obj)));
        LOG.info("Done exporting cache.");
    }
}
