package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cluster.impl.infinispan.StepRecord;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CacheEntryChangeListenerAdapter;
import com.norconex.crawler.core.cluster.pipeline.PipelineException;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.CacheSet;
import com.norconex.crawler.core2.cluster.Counter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InfinispanCacheManager implements CacheManager, Closeable {

    private static final String GENERIC_CACHE_NAME = "generic_cache";

    private final DefaultCacheManager dcm;
    private final Map<CacheEntryChangeListener<?>,
            CacheEntryChangeListenerAdapter<?>> adapterMappings =
                    new ConcurrentHashMap<>();
    // Per-cache locks to serialize first-time creation/definition
    private final Map<String, Object> cacheLocks = new ConcurrentHashMap<>();

    public InfinispanCacheManager(DefaultCacheManager cacheManager) {
        dcm = cacheManager;
    }

    @Override
    public <T> Cache<T> getCache(String name, Class<T> valueType) {
        return new InfinispanCacheAdapter<>(getInfiniCache(name));
    }

    @Override
    public CacheSet getCacheSet(String name) {
        return new InfinispanCacheSetAdapter(
                getInfiniCache(name).keySet());
    }

    @Override
    public Cache<String> getGenericCache() {
        if (!dcm.cacheExists(GENERIC_CACHE_NAME)) {
            dcm.defineConfiguration(GENERIC_CACHE_NAME,
                    new ConfigurationBuilder().build());
        }
        return new InfinispanCacheAdapter<>(dcm.getCache(GENERIC_CACHE_NAME));
    }

    @Override
    public boolean cacheExists(String name) {
        return dcm.cacheExists(name);
    }

    @Override
    public Counter getCounter(String name) {
        //        org.infinispan.Cache<String, Long> counterCache =
        //                dcm.<String, Long>getCache("counter-cache");
        org.infinispan.Cache<String, Long> counterCache =
                getInfiniCache("counter-cache");
        return new InfinispanCounter(counterCache, name);
    }

    @Override
    public void close() {
        try {
            dcm.close();
        } catch (IOException e) {
            throw new CacheException("Could not close cache manager.", e);
        }
    }

    public DefaultCacheManager vendor() {
        return dcm;
    }

    @Override
    public void forEach(BiConsumer<String, Cache<?>> c) {
        dcm.getCacheNames().forEach(name -> c.accept(
                name, new InfinispanCacheAdapter<>(dcm.getCache(name, true))));
    }

    //--- Infinispan-specific custom caches ------------------------------------
    public Cache<StepRecord> getPipelineStepCache() {
        return getCache(CacheNames.PIPE_CURRENT_STEP, StepRecord.class);
    }

    public Cache<StepRecord> getPipelineWorkerStatusesCache() {
        return getCache(CacheNames.PIPE_WORKER_STATUSES, StepRecord.class);
    }

    public void addCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<?> listener, String cacheName) {
        var adapter = new CacheEntryChangeListenerAdapter<>(listener);
        adapterMappings.put(listener, adapter);
        dcm.getCache(cacheName).addListener(adapter);
    }

    public void removeCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<?> listener, String cacheName) {
        var adapter = adapterMappings.get(listener);
        if (adapter != null) {
            try {
                dcm.getCache(CacheNames.PIPE_CURRENT_STEP)
                        .removeListener(adapter);
            } catch (Exception e) {
                LOG.debug("Could not remove pipeline current step "
                        + "listener: {}", e.toString());
            }
        }
    }

    // Use the default cache in the default cache container:
    //   <cache-container name="default" default-cache="base-template">
    private <T> org.infinispan.Cache<String, T> getInfiniCache(
            String cacheName) {

        if (dcm.cacheExists(cacheName)) {
            return dcm.getCache(cacheName);
        }
        try {
            // Serialize definition/start per cache name to avoid races
            var lock = cacheLocks.computeIfAbsent(cacheName, k -> new Object());
            synchronized (lock) {
                // Double-check after acquiring the lock
                if (dcm.cacheExists(cacheName)) {
                    return dcm.getCache(cacheName);
                }

                var existingCfg = dcm.getCacheConfiguration(cacheName);
                if (existingCfg != null) {
                    // If a template exists with this name/pattern, materialize it
                    if (existingCfg.isTemplate()) {
                        LOG.info(
                                "Materializing template configuration for cache '{}'",
                                cacheName);
                        var concrete = new ConfigurationBuilder()
                                .read(existingCfg)
                                .template(false)
                                .build();
                        dcm.defineConfiguration(cacheName, concrete);
                    }
                    // If non-template config exists, nothing to define
                    return dcm.getCache(cacheName);
                }

                // No named config: try to base on default cache configuration
                var defaultCfg = dcm.getDefaultCacheConfiguration();
                if (defaultCfg != null) {
                    LOG.info("Defining cache '{}' from default configuration.",
                            cacheName);
                    var concrete = new ConfigurationBuilder()
                            .read(defaultCfg)
                            .template(false)
                            .build();
                    dcm.defineConfiguration(cacheName, concrete);
                    return dcm.getCache(cacheName);
                }

                // As a last resort, rely on container wildcard mappings
                // (e.g., *_indexed) to resolve on first getCache call.
                LOG.info(
                        "Starting cache '{}' using container mappings (no explicit/default config found).",
                        cacheName);
                return dcm.getCache(cacheName);
            }
        } catch (RuntimeException e) {
            throw new PipelineException(
                    ("Could not create Infinispan cache %s from "
                            + "default cache configuration.")
                                    .formatted(cacheName),
                    e);
        }
    }
}