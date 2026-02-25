/* Copyright 2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.Iterator;
import java.util.function.Consumer;

import com.hazelcast.collection.ISet;
import com.norconex.crawler.core.cluster.CacheSet;

import lombok.extern.slf4j.Slf4j;

/**
 * Hazelcast ISet-backed implementation of CacheSet.
 * Note: ISet is backed by IMap internally in Hazelcast, so JDBC-backed
 * persistence works automatically.
 */
@Slf4j
class HazelcastSetAdapter implements CacheSet {

    private final ISet<String> delegate;

    public HazelcastSetAdapter(ISet<String> delegate) {
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
