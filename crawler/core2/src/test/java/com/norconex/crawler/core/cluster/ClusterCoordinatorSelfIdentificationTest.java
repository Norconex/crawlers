package com.norconex.crawler.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.ClusterNodesTest;
import com.norconex.crawler.core2.junit.ClusterTestUtil;

/**
 * Simple multi-node test writing each node role (coordinator / non-coordinator)
 * to a shared distributed cache and asserting we end up with exactly one of
 * each. Demonstrates test strategy of asserting cluster-visible state instead
 * of relying on local variables.
 */
@Timeout(60)
class ClusterCoordinatorSelfIdentificationTest {

    @ClusterNodesTest(nodes = { 1, 2 })
    @DisplayName(
        "ClusterNodesTest: nodes record coordinator role in shared cache"
    )
    void testCoordinatorRoleRecordedInSharedCache(int nodeCount,
            List<CrawlSession> sessions) throws Exception {
        // Unique cache name per invocation for isolation
        var cacheName = ClusterTestUtil.uniqueCacheName("coordExtTest");

        // Record role for each session's cluster
        for (var s : sessions) {
            recordRole(s.getCluster(), cacheName);
        }

        Cache<String> cache = sessions.get(0).getCluster().getCacheManager()
                .getCache(cacheName, String.class);

        // Wait until all node role entries are present in the shared cache
        ClusterTestUtil.waitForCacheSize(cache, nodeCount,
                Duration.ofSeconds(10));
        assertThat(cache.size())
                .as("Expected %s node role entries in distributed cache",
                        nodeCount)
                .isEqualTo(nodeCount);

        var coordinatorCount = new AtomicInteger();
        var nonCoordinatorCount = new AtomicInteger();
        cache.forEach((k, v) -> {
            if ("COORDINATOR".equals(v)) {
                coordinatorCount.incrementAndGet();
            } else if ("NON_COORDINATOR".equals(v)) {
                nonCoordinatorCount.incrementAndGet();
            }
        });

        assertThat(coordinatorCount.get())
                .as("Exactly one coordinator expected")
                .isEqualTo(1);
        var expectedNon = nodeCount == 1 ? 0 : 1;
        assertThat(nonCoordinatorCount.get())
                .as("Expected %s non-coordinator(s)", expectedNon)
                .isEqualTo(expectedNon);
    }

    private void recordRole(Cluster cluster, String cacheName) {
        Cache<String> cache =
                cluster.getCacheManager().getCache(cacheName, String.class);
        var node = cluster.getLocalNode();
        var role = node.isCoordinator() ? "COORDINATOR" : "NON_COORDINATOR";
        cache.put(node.getNodeName(), role);
    }
}
