/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.mocks.fetch.MockFetchResponse;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ConcurrentUtil;

public class ClusterJoinGateFetcher extends MockFetcher {

    private static final Duration CLUSTER_JOIN_TIMEOUT =
            Duration.ofSeconds(120);
    private static final Duration CHECK_INTERVAL = Duration.ofMillis(100);

    private transient CrawlSession session;
    private final transient AtomicBoolean gateConsumed = new AtomicBoolean();

    private int requiredNodeCount = 2;
    private List<String> gatedRefs = List.of("ref-0");

    public int getRequiredNodeCount() {
        return requiredNodeCount;
    }

    public ClusterJoinGateFetcher setRequiredNodeCount(int requiredNodeCount) {
        this.requiredNodeCount = requiredNodeCount;
        return this;
    }

    public List<String> getGatedRefs() {
        return gatedRefs;
    }

    public ClusterJoinGateFetcher setGatedRefs(List<String> gatedRefs) {
        this.gatedRefs = gatedRefs;
        return this;
    }

    @Override
    protected void fetcherStartup(CrawlSession crawler) {
        session = crawler;
    }

    @Override
    public MockFetchResponse fetch(FetchRequest fetchRequest)
            throws FetchException {
        awaitClusterJoin(fetchRequest.getDoc().getReference());
        return super.fetch(fetchRequest);
    }

    private void awaitClusterJoin(String ref) {
        if (session == null
                || !gatedRefs.contains(ref)
                || !gateConsumed.compareAndSet(false, true)) {
            return;
        }

        var joined = ConcurrentUtil.waitUntil(
                () -> session.getCluster().getNodeCount() >= requiredNodeCount,
                CLUSTER_JOIN_TIMEOUT,
                CHECK_INTERVAL);
        if (!joined) {
            throw new IllegalStateException(
                    "Cluster did not reach %d nodes before gated fetch '%s'."
                            .formatted(requiredNodeCount, ref));
        }
    }
}
