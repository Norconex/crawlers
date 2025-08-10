package com.norconex.crawler.core2.cluster;

import java.util.function.BiConsumer;

public interface CacheManager {
    <T> Cache<T> getCache(String name, Class<T> valueType);

    CacheSet getCacheSet(String name);

    Cache<String> getGenericCache();

    boolean cacheExists(String name);

    void forEach(BiConsumer<String, Cache<?>> c);

    //    /**
    //     * Gets an all-purpose persistent cache for generic needs when a named
    //     * cache is not required, never deleted (except when explicitly doing it).
    //     * @return persistent cache
    //     */
    //    Cache<String> getPersistentCache();
    //
    //    /**
    //     * Gets an all-purpose persistent cache for generic needs when a named
    //     * cache is not required, wiped upon starting a new crawl run (i.e.,
    //     * resuming after stop or failure).
    //     * @return crawl run persistent cache
    //     */
    //    Cache<String> getCrawlRunCache();
    //
    //    /**
    //     * Gets an all-purpose persistent cache for generic needs when a named
    //     * cache is not required, wiped upon starting a new crawl session (i.e.,
    //     * the crawler preview run ran until completion).
    //     * @return crawl session persistent cache
    //     */
    //    Cache<String> getCrawlSessionCache();

    Counter getCounter(String name);

    //    /**
    //     * Initializes the cache manager.
    //     * @param crawlerWorkdir
    //     */
    //    void init(String crawlerWorkdir);
    //
    //    void close();
}
