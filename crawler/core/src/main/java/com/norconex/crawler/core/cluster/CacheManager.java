package com.norconex.crawler.core.cluster;

import java.util.List;
import java.util.function.Consumer;

public interface CacheManager {

    /**
     * Gets a named cache storing objects of the supplied type.
     * By default the returned cache is persisted, but cache implementations
     * may decide otherwise (based on cache name, configuration, etc.).
     * @param <T> type of the value to store
     * @param name cache name
     * @param valueType class of the value to store
     * @return a named cache
     */
    <T> CacheMap<T> getCacheMap(String name, Class<T> valueType);

    /**
     * As string-based set, keeping only unique keys.
     * @param name cache name
     * @return a named cache set
     */
    CacheSet getCacheSet(String name);

    boolean cacheExists(String name);

    /**
     * Consumes every caches, where record value are serialized JSON String.
     * Entries are lazy loaded.
     * @param c serialized cache.
     *
     */
    void exportCaches(Consumer<SerializedCache> c);

    /**
     * Import caches previously exported with {@link #exportCaches(Consumer)}.
     * @param caches caches to import
     *
     */
    void importCaches(List<SerializedCache> caches);

    /**
     * Clears all caches.
     */
    void clearCaches();

    /**
     * Gets a generic shared cache bound to a crawler that persists
     * forever (never deleted).
     * @return crawler-scoped persistent cache
     */
    CacheMap<String> getCrawlerCache();

    /**
     * Gets a generic shared cache bound to a crawl session that persists
     * until a new crawl session is started (for the same crawler).
     * @return crawl session-scoped persistent cache
     */
    CacheMap<String> getCrawlSessionCache();

    /**
     * Gets a generic shared cache tied to a specific crawler run and
     * <b>does not get persisted</b> when the crawler run terminates.
     * @return crawl run non-persistent cache
     */
    CacheMap<String> getCrawlRunCache();

    /**
     * Gets a FIFO queue.
     * @param <T> the type of elements in the queue
     * @param name the queue name
     * @param valueType the value type
     * @return cache queue
     */
    <T> CacheQueue<T> getCacheQueue(String name, Class<T> valueType);
}
