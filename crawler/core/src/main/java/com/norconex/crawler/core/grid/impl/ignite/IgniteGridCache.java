/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.Objects;
import java.util.function.BiPredicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.configuration.CacheConfiguration;

import com.norconex.crawler.core.grid.GridCache;

import lombok.NonNull;
import lombok.experimental.StandardException;

public class IgniteGridCache<T> implements GridCache<T> {

    //TODO have a cache for the types and names in used (similar to other store engines,
    // with alt names added).

    //TODO supply "hints" to data stores at creation time, which would be
    // optional, for engines supporting them (for optimization).
    //OR: offer both implementations from engine: queue(or set) and key-value
    // for some store engines, implementation will be the same.
    // Add transaction support to datastore?
    // Add atomicity to datastore?  Do we only need it for:
    //   - get and delete first queue entry
    //   - store queue entry in active DB
    //   - or change state on same cache/queue ("status" field).

    //TODO document for a future release: make configurable number of
    // cached instances of a document to keep (only useful for gracing spoiled ones?)

    private String name;
    private final IgniteCache<String, T> cache;
    private final Ignite ignite;
    // is type needed?
    private final Class<? extends T> type;

    @NonNull
    IgniteGridCache(Ignite ignite, String name, Class<? extends T> type) {
        this.ignite = ignite;
        this.type = type;
        this.name = name;
        var cfg = new CacheConfiguration<String, T>();
        cfg.setName(name + IgniteGridSystem.Suffix.CACHE);

        cache = ignite.getOrCreateCache(cfg);

        // make configurable whether we want to enable multi-operations (transactional)
        // or operation atomicity (default) is OK.
        // yeah.. .maybe keep ATOMIC for better performance but document
        // how to change
        cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean put(String id, T object) {
        return !Objects.equals(cache.getAndPut(id, object), object);
    }

    @Override
    public T get(String id) {
        return cache.get(id);
    }

    @Override
    public boolean delete(String id) {
        return cache.remove(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void close() {
        // we don't explicitly close them here. They'll be "closed"
        // automatically when leaving the cluster.
        //        if (ignite.cluster().state().active()
        //                && ignite.cluster().localNode().isClient()) {
        //            cache.close();
        //        }
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try {
            cache.forEach(en -> {
                if (!predicate.test(en.getKey(), en.getValue())) {
                    throw new BreakException();
                }
            });
        } catch (BreakException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return cache.metrics().isEmpty();
    }

    @Override
    public boolean contains(String id) {
        return cache.containsKey(id);
    }

    @Override
    public long size() {
        return cache.metrics().getCacheSize();
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
