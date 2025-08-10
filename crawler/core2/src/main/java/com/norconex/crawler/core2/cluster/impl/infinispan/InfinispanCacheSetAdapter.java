package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.util.Iterator;
import java.util.function.Consumer;

import com.norconex.crawler.core2.cluster.CacheSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfinispanCacheSetAdapter implements CacheSet {

    private final org.infinispan.CacheSet<String> delegate;

    public InfinispanCacheSetAdapter(
            org.infinispan.CacheSet<String> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
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
    public boolean contains(String key) {
        return delegate.contains(key);
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public void forEach(Consumer<String> action) {
        delegate.forEach(action::accept);
    }

    @Override
    public void add(String key) {
        delegate.add(key);
    }

    @Override
    public Iterator<String> iterator() {
        return delegate.iterator();
    }
}
