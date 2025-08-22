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

    private final DefaultCacheManager cacheManager;
    private final Map<CacheEntryChangeListener<?>,
            CacheEntryChangeListenerAdapter<?>> adapterMappings =
                    new ConcurrentHashMap<>();

    public InfinispanCacheManager(DefaultCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public <T> Cache<T> getCache(String name, Class<T> valueType) {
        return new InfinispanCacheAdapter<>(cacheManager.getCache(name));
    }

    @Override
    public CacheSet getCacheSet(String name) {
        return new InfinispanCacheSetAdapter(
                cacheManager.<String, Object>getCache(name)
                        .keySet());
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

    @Override
    public void forEach(BiConsumer<String, Cache<?>> c) {
        cacheManager.getCacheNames()
                .forEach(name -> c.accept(name, new InfinispanCacheAdapter<>(
                        cacheManager.getCache(name, true))));
    }

    //--- Infinispan-specific custom caches ------------------------------------
    public Cache<StepRecord> getPipelineStepCache() {
        return getCache(CacheNames.PIPE_CURRENT_STEP, StepRecord.class);
    }

    public Cache<StepRecord> getPipelineWorkerStatusesCache() {
        return getCache(CacheNames.PIPE_WORKER_STATUSES,
                StepRecord.class);
    }

    public void addCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<?> listener, String cacheName) {
        var adapter = new CacheEntryChangeListenerAdapter<>(listener);
        adapterMappings.put(listener, adapter);
        cacheManager.getCache(cacheName).addListener(adapter);
    }

    public void removeCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<?> listener, String cacheName) {
        var adapter = adapterMappings.get(listener);
        if (adapter != null) {
            try {
                cacheManager.getCache(CacheNames.PIPE_CURRENT_STEP)
                        .removeListener(adapter);
            } catch (Exception e) {
                LOG.debug("Could not remove pipeline current step "
                        + "listener: {}", e.toString());
            }
        }
    }

    //    /**
    //     * Adds a listener that will be triggered with the current step when
    //     * the current step changes. The current step changes prior to adding
    //     * this listener are not passed.
    //     * @param listener the listener to add
    //     */
    //    public void addPipelineCurrentStepListener(
    //            CacheEntryChangeListener<StepRecord> listener) {
    //        addCacheEntryChangeListener(listener, CacheNames.PIPE_CURRENT_STEP);
    //    }
    //
    //    public void removePipelineCurrentStepListener(
    //            CacheEntryChangeListener<StepRecord> listener) {
    //        removeCacheEntryChangeListener(listener, CacheNames.PIPE_CURRENT_STEP);
    //
    //    }
    //
    //    public void addPipelineWorkerStatusesListener(Object listener) {
    //        cacheManager.getCache(CacheNames.PIPE_WORKER_STATUSES)
    //                .addListener(listener);
    //    }
    //
    //    public void removePipelineWorkerStatusesListener(Object listener) {
    //        try {
    //            cacheManager.getCache(CacheNames.PIPE_WORKER_STATUSES)
    //                    .removeListener(listener);
    //        } catch (Exception e) {
    //            LOG.debug("Could not remove pipeline worker status listener: {}",
    //                    e.toString());
    //        }
    //    }

}
