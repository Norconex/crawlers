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
package com.norconex.crawler.core.cluster.impl.mvstore;

import java.util.Iterator;
import java.util.function.Consumer;

import org.h2.mvstore.MVMap;

import com.norconex.crawler.core.cluster.CacheSet;

/**
 * File-backed {@link CacheSet} implementation using H2 MVStore.
 * Uses an {@link MVMap}&lt;String, Boolean&gt; where only the keys
 * carry semantic meaning (keys-only set).
 */
public class MVStoreCacheSet implements CacheSet {

    private static final Boolean PRESENT = Boolean.TRUE;

    private final MVMap<String, Boolean> map;

    public MVStoreCacheSet(MVMap<String, Boolean> map) {
        this.map = map;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void add(String key) {
        map.put(key, PRESENT);
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public Iterator<String> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public long size() {
        return map.sizeAsLong();
    }

    @Override
    public void forEach(Consumer<String> action) {
        map.keySet().forEach(action);
    }
}
