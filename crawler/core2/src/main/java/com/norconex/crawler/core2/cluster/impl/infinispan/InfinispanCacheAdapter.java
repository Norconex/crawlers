package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.norconex.crawler.core2.cluster.Cache;

class InfinispanCacheAdapter<T> implements Cache<T> {

    private final org.infinispan.Cache<String, T> delegate;

    public InfinispanCacheAdapter(org.infinispan.Cache<String, T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String key, T value) {
        delegate.put(key, value);
    }

    @Override
    public Optional<T> get(String key) {
        return Optional.ofNullable(delegate.get(key));
    }

    @Override
    public void remove(String key) {
        delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public T computeIfAbsent(String key,
            Function<String, ? extends T> mappingFunction) {
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Optional<T> computeIfPresent(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional
                .ofNullable(delegate.computeIfPresent(key, remappingFunction));
    }

    @Override
    public Optional<T> compute(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional.ofNullable(delegate.compute(key, remappingFunction));
    }

    @Override
    public T merge(String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public boolean containsKey(String key) {
        return delegate.containsKey(key);
    }

    @Override
    public T getOrDefault(String key, T defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public T putIfAbsent(String key, T value) {
        return delegate.putIfAbsent(key, value);
    }
}
