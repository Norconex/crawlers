package com.norconex.crawler.core.cluster;

import java.util.List;
import java.util.function.Consumer;

import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

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

    /**
     * Gets the admin cache for storing administrative data.
     * @return admin cache
     */
    CacheMap<String> getAdminCache();

    /**
     * Gets the pipeline step cache for storing current pipeline steps.
     * @return pipeline step cache
     */
    CacheMap<StepRecord> getPipelineStepCache();

    /**
     * Gets the pipeline worker status cache for storing worker statuses.
     * @return pipeline worker status cache
     */
    CacheMap<StepRecord> getPipelineWorkerStatusCache();

    /**
     * Adds a cache entry change listener to the specified cache.
     * @param <T> the type of values in the cache
     * @param listener the listener to add
     * @param cacheName the name of the cache
     */
    <T> void addCacheEntryChangeListener(
            CacheEntryChangeListener<T> listener, String cacheName);

    /**
     * Removes a cache entry change listener from the specified cache.
     * @param <T> the type of values in the cache
     * @param listener the listener to remove
     * @param cacheName the name of the cache
     */
    <T> void removeCacheEntryChangeListener(
            CacheEntryChangeListener<T> listener, String cacheName);

    /**
     * Gets the vendor-specific implementation object.
     * @return vendor implementation
     */
    Object vendor();
}
