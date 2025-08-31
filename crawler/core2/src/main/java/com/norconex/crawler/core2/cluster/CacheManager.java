package com.norconex.crawler.core2.cluster;

import java.util.function.BiConsumer;

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
    <T> Cache<T> getCache(String name, Class<T> valueType);

    /**
     * As string-based set, keeping only unique keys.
     * @param name cache name
     * @return a named cache set
     */
    CacheSet getCacheSet(String name);

    //    Cache<String> getGenericCache();

    boolean cacheExists(String name);

    void forEach(BiConsumer<String, Cache<?>> c);

    /**
     * Gets a generic shared cache bound to a crawler that persists
     * forever (never deleted).
     * @return crawler-scoped persistent cache
     */
    Cache<String> getCrawlerCache();

    /**
     * Gets a generic shared cache bound to a crawl session that persists
     * until a new crawl session is started (for the same crawler).
     * @return crawl session-scoped persistent cache
     */
    Cache<String> getCrawlSessionCache();

    /**
     * Gets a generic shared cache tied to a specific crawler run and
     * <b>does not get persisted</b> when the crawler run terminates.
     * @return crawl run non-persistent cache
     */
    Cache<String> getCrawlRunCache();
    //

    Counter getCounter(String name);

    //    /**
    //     * Initializes the cache manager.
    //     * @param crawlerWorkdir
    //     */
    //    void init(String crawlerWorkdir);
    //
    //    void close();
}
