package com.norconex.crawler.core.cluster.impl.infinispan;

import org.infinispan.Cache;

import com.norconex.crawler.core.cluster.Counter;

class InfinispanCounter implements Counter {
    private final Cache<String, Long> cache;
    private final String key;

    public InfinispanCounter(Cache<String, Long> cache, String key) {
        this.cache = cache;
        this.key = key;
    }

    @Override
    public long incrementAndGet() {
        return addAndGet(1);
    }

    @Override
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public long addAndGet(long delta) {
        return cache.compute(key, (k, v) -> ((v == null ? 0L : v) + delta));
    }

    @Override
    public long getAndAdd(long delta) {
        final var previous = new long[1]; // workaround for single-element mutable state
        cache.compute(key, (k, v) -> {
            var current = (v == null ? 0L : v);
            previous[0] = current;
            return current + delta;
        });
        return previous[0];
    }

    @Override
    public long decrementAndGet() {
        return addAndGet(-1);
    }

    @Override
    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public void set(long value) {
        cache.put(key, value);
    }

    @Override
    public long get() {
        return cache.getOrDefault(key, 0L);
    }

    @Override
    public void reset() {
        cache.put(key, 0L);
    }
}
