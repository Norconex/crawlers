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
package com.norconex.crawler.core.cluster.impl.memory;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.norconex.crawler.core.cluster.CacheSet;

/**
 * In-memory {@link CacheSet} backed by a concurrent hash set.
 * Not persistent.
 */
public class InMemoryCacheSet implements CacheSet {

    private final Set<String> store = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    @Override
    public void add(String key) {
        store.add(key);
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public boolean contains(String key) {
        return store.contains(key);
    }

    @Override
    public Iterator<String> iterator() {
        return store.iterator();
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public void forEach(Consumer<String> action) {
        store.forEach(action);
    }
}
