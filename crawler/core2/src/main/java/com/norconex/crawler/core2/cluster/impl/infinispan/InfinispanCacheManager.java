package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lifecycle.ComponentStatus;
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

    private static final Map<DefaultCacheManager, AtomicInteger> REF_COUNTS =
            new ConcurrentHashMap<>();

    private final DefaultCacheManager dcm;
    private final Map<CacheEntryChangeListener<?>,
            CacheEntryChangeListenerAdapter<?>> adapterMappings =
                    new ConcurrentHashMap<>();
    // Per-cache locks to serialize first-time creation/definition
    private final Map<String, Object> cacheLocks = new ConcurrentHashMap<>();

    public InfinispanCacheManager(DefaultCacheManager cacheManager) {
        dcm = cacheManager;
        REF_COUNTS.computeIfAbsent(dcm, k -> new AtomicInteger(0))
                .incrementAndGet();
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
    public Cache<String> getCrawlerCache() {
        // Use container-provided configuration (XML/wildcards). Do NOT define
        // a blank configuration programmatically (which would be LOCAL).
        return new InfinispanCacheAdapter<>(getInfiniCache(CacheNames.CRAWLER));
    }

    @Override
    public Cache<String> getCrawlSessionCache() {
        // Use container-provided configuration (XML/wildcards). Do NOT define
        // a blank configuration programmatically (which would be LOCAL).
        return new InfinispanCacheAdapter<>(
                getInfiniCache(CacheNames.CRAWL_SESSION));
    }

    @Override
    public Cache<String> getCrawlRunCache() {
        // Use container-provided configuration (XML/wildcards). Do NOT define
        // a blank configuration programmatically (which would be LOCAL).
        return new InfinispanCacheAdapter<>(
                getInfiniCache(CacheNames.CRAWL_RUN));
    }

    @Override
    public boolean cacheExists(String name) {
        return dcm.cacheExists(name);
    }

    @Override
    public Counter getCounter(String name) {
        org.infinispan.Cache<String, Long> counterCache =
                getInfiniCache("counter-cache");
        return new InfinispanCounter(counterCache, name);
    }

    @Override
    public void close() {
        try {
            var counter = REF_COUNTS.get(dcm);
            if (counter == null) {
                // Unknown state; be conservative and attempt close
                dcm.close();
                return;
            }
            var remaining = counter.decrementAndGet();
            if (remaining <= 0) {
                REF_COUNTS.remove(dcm);
                dcm.close();
            } else {
                LOG.debug("InfinispanCacheManager underlying container still "
                        + "in use by {} manager(s); skipping close.",
                        remaining);
            }
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

    public Cache<String> getAdminCache() {
        return getCache(CacheNames.ADMIN, String.class);
    }

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
                dcm.getCache(cacheName).removeListener(adapter);
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

        var status = dcm.getStatus();
        if (status == ComponentStatus.TERMINATED
                || status == ComponentStatus.FAILED) {
            throw new PipelineException(
                    ("Cache container is closed (status=%s); "
                            + "cannot access cache '%s'.")
                                    .formatted(status, cacheName));
        }

        if (dcm.cacheExists(cacheName)) {
            return dcm.getCache(cacheName);
        }
        try {
            var lock = cacheLocks.computeIfAbsent(cacheName, k -> new Object());
            synchronized (lock) {
                if (dcm.cacheExists(cacheName)) {
                    return dcm.getCache(cacheName);
                }

                // 1) Prefer container mappings (named configs, wildcards, default)
                try {
                    LOG.info("Starting cache '{}' using container mappings.",
                            cacheName);
                    return dcm.getCache(cacheName);
                } catch (RuntimeException startErr) {
                    LOG.debug(
                            "Container mapping start failed for cache '{}': {}",
                            cacheName, startErr.toString());
                }

                // 2) Define from the cache-container default cache name, if present
                try {
                    String defaultCacheName = null;
                    try {
                        defaultCacheName = dcm.getCacheManagerConfiguration()
                                .defaultCacheName().orElse(null);
                    } catch (Throwable t) {
                        // ignore and try next fallback
                    }
                    if (defaultCacheName != null) {
                        var templateCfg =
                                dcm.getCacheConfiguration(defaultCacheName);
                        if (templateCfg != null) {
                            LOG.info(
                                    "Defining cache '{}' from container default cache '{}'.",
                                    cacheName, defaultCacheName);
                            var concrete = new ConfigurationBuilder()
                                    .read(templateCfg)
                                    .template(false)
                                    .build();
                            if (!dcm.cacheExists(cacheName)) {
                                dcm.defineConfiguration(cacheName, concrete);
                            }
                            return dcm.getCache(cacheName);
                        }
                    }
                } catch (CacheConfigurationException already) {
                    LOG.debug("Cache '{}' was defined concurrently: {}",
                            cacheName, already.getMessage());
                    return dcm.getCache(cacheName);
                }

                // 3) Fallback to container's default cache configuration, if any
                var defaultCfg = dcm.getDefaultCacheConfiguration();
                if (defaultCfg != null) {
                    LOG.info(
                            "Defining cache '{}' from DefaultCacheManager default configuration.",
                            cacheName);
                    var concrete = new ConfigurationBuilder()
                            .read(defaultCfg)
                            .template(false)
                            .build();
                    try {
                        if (!dcm.cacheExists(cacheName)) {
                            dcm.defineConfiguration(cacheName, concrete);
                        }
                    } catch (CacheConfigurationException already) {
                        LOG.debug("Cache '{}' was defined concurrently: {}",
                                cacheName, already.getMessage());
                    }
                    return dcm.getCache(cacheName);
                }

                // 4) Last-resort: programmatically define a distributed cache
                LOG.warn(
                        "No container/default template found. Defining '{}' as DIST_SYNC on the fly.",
                        cacheName);
                var distCfg = new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.DIST_SYNC)
                        .build();
                try {
                    if (!dcm.cacheExists(cacheName)) {
                        dcm.defineConfiguration(cacheName, distCfg);
                    }
                } catch (CacheConfigurationException already) {
                    LOG.debug("Cache '{}' was defined concurrently: {}",
                            cacheName, already.getMessage());
                }
                return dcm.getCache(cacheName);
            }
        } catch (RuntimeException e) {
            throw new PipelineException(
                    ("Could not create Infinispan cache %s from available configuration.")
                            .formatted(cacheName),
                    e);
        }
    }
}
