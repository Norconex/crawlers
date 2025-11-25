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

import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.CacheSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class HazelcastCacheSetAdapter implements CacheSet {

    private final IMap<String, Boolean> delegate;

    public HazelcastCacheSetAdapter(IMap<String, Boolean> delegate) {
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
        return delegate.containsKey(key);
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public void forEach(Consumer<String> action) {
        delegate.keySet().forEach(action::accept);
    }

    @Override
    public void add(String key) {
        delegate.put(key, Boolean.TRUE);
    }

    @Override
    public Iterator<String> iterator() {
        return delegate.keySet().iterator();
    }
}
