package com.norconex.crawler.core2.junit;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.session.CrawlSession;

/** Utility helpers for cluster-related tests. */
public final class ClusterTestUtil {
    private ClusterTestUtil() {
    }

    /**
     * Generates a unique cache name for a test run with an optional prefix.
     * @param hint prefix or descriptive hint (may be null or blank)
     * @return unique cache name
     */
    public static String uniqueCache(String hint) {
        var base = (hint == null || hint.isBlank()) ? "cache" : hint;
        return base + "-" + Long.toHexString(System.nanoTime()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Waits until the visible cluster membership (union of node names across provided sessions)
     * reaches the expected size or timeout elapses.
     * Uses a reflective lookup of a public method named <code>getAllNodeNames()</code> returning a List<String>
     * when present (e.g., InfinispanCluster) else falls back to each session's local node name.
     * @param sessions sessions participating
     * @param expected expected number of distinct nodes
     * @param timeout timeout duration
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if timeout reached without expected size
     */
    public static void waitForClusterSize(List<CrawlSession> sessions,
            int expected, Duration timeout) throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (distinctNodeNames(sessions).size() >= expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException(
                "Timeout waiting for cluster size " + expected + " (saw="
                        + distinctNodeNames(sessions).size() + ")");
    }

    /** Returns distinct node names across sessions (best effort). */
    public static Set<String> distinctNodeNames(List<CrawlSession> sessions) {
        Set<String> names = new HashSet<>();
        for (var s : sessions) {
            var c = s.getCluster();
            names.addAll(getAllNodeNamesReflectively(c));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getAllNodeNamesReflectively(Cluster cluster) {
        try {
            var m = cluster.getClass().getMethod("getAllNodeNames");
            var r = m.invoke(cluster);
            if (r instanceof List<?>) {
                return (List<String>) r; // unchecked on purpose
            }
        } catch (Exception e) {
            // ignore and fallback
        }
        return List.of(cluster.getLocalNode().getNodeName());
    }

    /** Waits until the cache size reaches expected or timeout. */
    public static void waitForCacheSize(Cache<?> cache, long expected,
            Duration timeout) throws InterruptedException {
        waitForCacheSize(cache, expected, timeout, Duration.ofMillis(100));
    }

    /** Waits until the cache size reaches expected or timeout with custom poll interval. */
    public static void waitForCacheSize(Cache<?> cache, long expected,
            Duration timeout, Duration pollInterval)
            throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (cache.size() >= expected) {
                return;
            }
            Thread.sleep(pollInterval.toMillis());
        }
        throw new IllegalStateException("Timeout waiting for cache size "
                + expected + " (saw=" + cache.size() + ")");
    }
}