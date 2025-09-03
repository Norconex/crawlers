package com.norconex.crawler.core.junit;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.session.CrawlSession;

/** Utility helpers for cluster-related tests. */
public final class ClusterTestUtil {
    private ClusterTestUtil() {
    }

    /**
     * Creates or get a String cache with the given name for a test.
     * @param session crawl session
     * @param cacheName name of the cache to create or get
     * @return cache
     */
    public static Cache<String> stringCache(
            CrawlSession session, String cacheName) {
        return cache(session, String.class, cacheName);
    }

    /**
     * Creates or get a cache with the given name for a test.
     * @param session crawl session
     * @param cacheName name of the cache to create or get
     * @param type class of cache type
     * @param <T> cache type
     * @return cache
     */
    public static <T> Cache<T> cache(
            CrawlSession session, Class<T> type, String cacheName) {
        return session.getCluster().getCacheManager().getCache(cacheName, type);
    }

    /**
     * Creates a unique String cache for a test run.
     * @param session crawl session
     * @return unique String cache
     */
    public static Cache<String> uniqueStringCache(CrawlSession session) {
        return uniqueStringCache(session, null);
    }

    /**
     * Creates a unique String cache for a test run with an optional cache
     * name prefix.
     * @param session crawl session
     * @param namePrefix cache name prefix or descriptive hint
     *     (may be null or blank)
     * @return unique String cache
     */
    public static Cache<String> uniqueStringCache(
            CrawlSession session, String namePrefix) {
        return uniqueCache(session, String.class, namePrefix);
    }

    /**
     * Creates a unique cache for a test run.
     * @param session crawl session
     * @param type class of cache type
     * @param <T> cache type
     * @return unique cache
     */
    public static <T> Cache<T> uniqueCache(
            CrawlSession session, Class<T> type) {
        return uniqueCache(session, type, null);
    }

    /**
     * Creates a unique cache for a test run with an optional cache
     * name prefix.
     * @param session crawl session
     * @param type class of cache type
     * @param <T> cache type
     * @param namePrefix cache name prefix or descriptive hint
     *     (may be null or blank)
     * @return unique cache
     */
    public static <T> Cache<T> uniqueCache(
            CrawlSession session, Class<T> type, String namePrefix) {
        return session.getCluster().getCacheManager()
                .getCache(uniqueCacheName(namePrefix), type);
    }

    /**
     * Generates a unique cache name for a test run.
     * @return unique cache name
     */
    public static String uniqueCacheName() {
        return uniqueCacheName(null);
    }

    /**
     * Generates a unique cache name for a test run with an optional prefix.
     * @param prefix prefix or descriptive hint (may be null or blank)
     * @return unique cache name
     */
    public static String uniqueCacheName(String prefix) {
        var base = StringUtils.isBlank(prefix) ? "cache" : prefix;
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

    /**
     * Returns distinct node names across sessions (best effort).
     * @param sessions sessions for nodes in this test
     * @return node names
     */
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

    /**
     * Waits until the cache size reaches expected or timeout.
     * Can be used to ensures eventual-consistency safety.
     * @param cache cache to wait on
     * @param expected expected cache size
     * @param timeout maximum time to wait
     */
    public static void waitForCacheSize(
            Cache<?> cache, long expected, Duration timeout) {
        waitForCacheSize(cache, expected, timeout, Duration.ofMillis(100));
    }

    /**
     * Waits until the cache size reaches expected or timeout with custom poll interval.
     * @param cache cache to wait on
     * @param expected expected cache size
     * @param timeout maximum time to wait
     * @param pollInterval interval used to check if timed out
     */
    public static void waitForCacheSize(
            Cache<?> cache,
            long expected,
            Duration timeout,
            Duration pollInterval) {
        var deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (cache.size() >= expected) {
                return;
            }
            Sleeper.sleepMillis(pollInterval.toMillis());
        }
        throw new IllegalStateException("Timeout waiting for cache size "
                + expected + " (saw=" + cache.size() + ")");
    }
}
