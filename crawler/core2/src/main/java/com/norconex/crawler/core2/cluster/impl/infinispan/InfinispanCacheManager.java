package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.BiConsumer;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cluster.impl.infinispan.StepRecord;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.CacheSet;
import com.norconex.crawler.core2.cluster.Counter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InfinispanCacheManager implements CacheManager, Closeable {

    private static final String GENERIC_CACHE_NAME = "generic_cache";

    private final DefaultCacheManager cacheManager;

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
    public Cache<StepRecord> getPipelineCurrentStepCache() {
        return getCache(CacheNames.PIPE_CURRENT_STEP, StepRecord.class);
    }

    public Cache<StepRecord> getPipelineWorkerStatusCache() {
        return getCache(CacheNames.PIPE_WORKER_STATUS,
                StepRecord.class);
    }

    public void addPipelineCurrentStepListener(Object listener) {
        cacheManager.getCache(CacheNames.PIPE_CURRENT_STEP)
                .addListener(listener);
    }

    public void removePipelineCurrentStepListener(Object listener) {
        try {
            cacheManager.getCache(CacheNames.PIPE_CURRENT_STEP)
                    .removeListener(listener);
        } catch (Exception e) {
            LOG.debug("Could not remove pipeline current step listener: {}", e.toString());
        }
    }

    public void addPipelineWorkerStatusListener(Object listener) {
        cacheManager.getCache(CacheNames.PIPE_WORKER_STATUS)
                .addListener(listener);
    }

    public void removePipelineWorkerStatusListener(Object listener) {
        try {
            cacheManager.getCache(CacheNames.PIPE_WORKER_STATUS)
                    .removeListener(listener);
        } catch (Exception e) {
            LOG.debug("Could not remove pipeline worker status listener: {}", e.toString());
        }
    }

    //TODO REMOVE LISTENERS WHEN DONE WITH PIPELINE EXECUTION

    //    public Cache<PipelineStepRecord> getXPipelineStepTrackerCache() {
    //        return getCache("pipe_step_tracker", PipelineStepRecord.class);
    //    }

}