package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.Closeable;
import java.io.IOException;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.Counter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InfinispanCacheManager implements CacheManager, Closeable {

    private static final String DEFAULT_CACHE_TEMPLATE = "default";
    private static final String GENERIC_CACHE_NAME = "generic-cache";

    private final DefaultCacheManager cacheManager;

    public InfinispanCacheManager(DefaultCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        defineCacheWithFallback("counter-cache");
    }

    @Override
    public <T> Cache<T> getCache(String name, Class<T> valueType) {
        defineCacheWithFallback(name);
        //        if (!cacheManager.cacheExists(name)) {
        //            cacheManager.defineConfiguration(name,
        //                    new ConfigurationBuilder().build());
        //        }
        return new InfinispanCacheAdapter<>(cacheManager.getCache(name));
    }

    @Override
    public Cache<String> getGenericCache() {
        if (!cacheManager.cacheExists(GENERIC_CACHE_NAME)) {
            cacheManager.defineConfiguration(GENERIC_CACHE_NAME,
                    new ConfigurationBuilder().build());
        }
        return new InfinispanCacheAdapter<>(
                cacheManager.getCache(GENERIC_CACHE_NAME));
    }

    @Override
    public boolean cacheExists(String name) {
        return cacheManager.cacheExists(name);
    }

    @Override
    public Counter getCounter(String name) {
        org.infinispan.Cache<String, Long> counterCache =
                cacheManager.<String, Long>getCache("counter-cache");
        return new InfinispanCounter(counterCache, name);
    }

    @Override
    public void close() {
        try {
            cacheManager.close();
        } catch (IOException e) {
            throw new CacheException("Could not close cache manager.", e);
        }
    }

    public DefaultCacheManager vendor() {
        return cacheManager;
    }

    public void defineCacheWithFallback(String cacheName) {
        var templateToUse = cacheName;
        // Check if the preferred template exists
        if (cacheManager.getCacheConfiguration(templateToUse) == null) {
            templateToUse = DEFAULT_CACHE_TEMPLATE;
            // Optionally check if fallback exists too
            if (cacheManager.getCacheConfiguration(templateToUse) == null) {
                throw new IllegalStateException(
                        "Neither explicit or default Infinspan cache "
                                + "definition exist.");
            }
        }
        if (!cacheManager.cacheExists(cacheName)) {
            LOG.info("Using Infinispan '{}' cache config definition for cache "
                    + "'{}'.",
                    templateToUse, cacheName);
            cacheManager.defineConfiguration(
                    cacheName,
                    templateToUse,
                    new ConfigurationBuilder().build());
        }
    }

}
