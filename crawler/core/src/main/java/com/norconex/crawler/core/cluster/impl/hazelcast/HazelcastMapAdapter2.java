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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import com.hazelcast.query.impl.predicates.PagingPredicateImpl;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.QueryFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * Hazelcast IMap-based implementation of the Cache interface.
 * Supports SQL-like queries by converting query syntax to
 * Hazelcast predicates.
 *
 * @param <T> the type of values stored in the cache
 */
@Slf4j
public class HazelcastMapAdapter2<T> implements CacheMap<T> {

    private final IMap<String, T> delegate;
    private final HazelcastInstance hazelcastInstance;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Set<String> CLOSED_LOGGED =
            ConcurrentHashMap.newKeySet();
    private final Class<T> type;

    public HazelcastMapAdapter2(
            IMap<String, T> delegate,
            HazelcastInstance hazelcastInstance,
            Class<T> type) {
        this.delegate = delegate;
        this.hazelcastInstance = hazelcastInstance;
        this.type = type;
    }

    @Override
    public boolean isEmpty() {
        return supplyIfCache(delegate::isEmpty, false);
    }

    @Override
    public void put(String key, T value) {
        runIfCache(() -> delegate.put(key, value));
    }

    @Override
    public void putAll(Map<String, T> entries) {
        runIfCache(() -> delegate.putAll(entries));
    }

    @Override
    public Optional<T> get(String key) {
        return Optional.ofNullable(
                supplyIfCache(() -> delegate.get(key), null));
    }

    @Override
    public void remove(String key) {
        runIfCache(() -> delegate.remove(key));
    }

    @Override
    public void clear() {
        runIfCache(delegate::clear);
    }

    @Override
    public T computeIfAbsent(String key,
            Function<String, ? extends T> mappingFunction) {
        return supplyIfCache(() -> {
            var existing = delegate.get(key);
            if (existing != null) {
                return existing;
            }
            T newVal = mappingFunction.apply(key);
            var prev = delegate.putIfAbsent(key, newVal);
            return prev != null ? prev : newVal;
        }, null);
    }

    @Override
    public Optional<T> computeIfPresent(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional.ofNullable(supplyIfCache(() -> {
            var oldValue = delegate.get(key);
            if (oldValue != null) {
                T newValue = remappingFunction.apply(key, oldValue);
                if (newValue != null) {
                    delegate.put(key, newValue);
                    return newValue;
                }
                delegate.remove(key);
            }
            return null;
        }, null));
    }

    @Override
    public Optional<T> compute(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional.ofNullable(supplyIfCache(() -> {
            var oldValue = delegate.get(key);
            T newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                delegate.put(key, newValue);
                return newValue;
            }
            if (oldValue != null) {
                delegate.remove(key);
            }
            return null;
        }, null));
    }

    @Override
    public T merge(String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        return supplyIfCache(() -> {
            var oldValue = delegate.get(key);
            var newValue =
                    (oldValue == null) ? value
                            : remappingFunction.apply(oldValue, value);
            if (newValue != null) {
                delegate.put(key, newValue);
            } else {
                delegate.remove(key);
            }
            return newValue;
        }, null);
    }

    @Override
    public boolean containsKey(String key) {
        return supplyIfCache(() -> delegate.containsKey(key), false);
    }

    @Override
    public T getOrDefault(String key, T defaultValue) {
        return supplyIfCache(
                () -> delegate.getOrDefault(key, defaultValue), defaultValue);
    }

    @Override
    public T putIfAbsent(String key, T value) {
        return supplyIfCache(() -> delegate.putIfAbsent(key, value), null);
    }

    @Override
    public boolean replace(String key, T oldValue, T newValue) {
        return supplyIfCache(
                () -> delegate.replace(key, oldValue, newValue), false);
    }

    @Override
    public List<T> query(QueryFilter filter) {
        return supplyIfCache(
                () -> new ArrayList<>(delegate.values(toPredicate(filter))),
                Collections.emptyList());
    }

    @Override
    public Iterator<T> queryIterator(QueryFilter filter) {
        return supplyIfCache(() -> new PagingIterator<>(
                delegate,
                toPredicate(filter),
                DEFAULT_BATCH_SIZE), Collections.emptyIterator());
    }

    //
    //    @Override
    //    public void queryStream(QueryFilter filter, Consumer<T> consumer,
    //            int batchSize) {
    //        runIfCache(() -> {
    //            Iterator<T> it = new PagingIterator<>(delegate, toPredicate(filter),
    //                    batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE);
    //            while (it.hasNext()) {
    //                consumer.accept(it.next());
    //            }
    //        });
    //    }

    @Override
    public long count(QueryFilter filter) {
        return delegate.keySet(toPredicate(filter)).size();
    }

    @Override
    public long size() {
        return supplyIfCache(delegate::size, -1);
    }

    @Override
    public void delete(QueryFilter filter) {
        if (filter == null) {
            return;
        }
        runIfCache(() -> {
            LOG.debug("Deleting entries matching filter: {}...", filter);
            delegate.removeAll(toPredicate(filter));
            LOG.debug("Finished deleting {} entries");
        });
    }

    @Override
    public void forEach(BiConsumer<String, ? super T> action) {
        runIfCache(() -> delegate.entrySet()
                .forEach(entry -> action.accept(
                        entry.getKey(), entry.getValue())));
    }

    @Override
    public List<String> keys() {
        return supplyIfCache(
                () -> new ArrayList<>(delegate.keySet()),
                new ArrayList<>());
    }

    /**
     * Get the underlying Hazelcast IMap.
     * @return the delegate IMap
     */
    public IMap<String, T> vendor() {
        return delegate;
    }

    //--- Private methods ------------------------------------------------------

    /**
     * Convert an entry filter to an Hazelcast Predicate.
     */
    private Predicate<String, T> toPredicate(QueryFilter filter) {
        if (filter == null || StringUtils.isBlank(filter.getFieldName())) {
            return Predicates.alwaysTrue();
        }
        return Predicates.equal(
                filter.getFieldName(),
                Objects.toString(filter.getFieldValue(), ""));
    }

    private <R> R supplyIfCache(Supplier<R> supplier, R defaultValue) {
        return isCacheClosed() ? defaultValue : supplier.get();
    }

    private void runIfCache(Runnable runnable) {
        if (!isCacheClosed()) {
            runnable.run();
        }
    }

    private boolean isCacheClosed() {
        var lifecycle = hazelcastInstance.getLifecycleService();
        if (!lifecycle.isRunning()) {
            var name = delegate.getName();
            if (CLOSED_LOGGED.add(name)) {
                LOG.warn("Attempted to use cache '{}' after it was closed.",
                        name);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("(suppressed) Attempted to use closed cache '{}'.",
                        name);
            }
            return true;
        }
        return false;
    }

    /**
     * Iterator that pages results from Hazelcast IMap using PagingPredicate.
     * Only the current page is loaded in memory.
     */
    private static class PagingIterator<T> implements Iterator<T> {
        private final IMap<String, T> map;
        private final com.hazelcast.query.Predicate<String, T> predicate;
        private final int pageSize;
        private int pageIndex = 0;
        private Iterator<T> currentPageIterator;
        private boolean lastPage = false;

        PagingIterator(IMap<String, T> map,
                com.hazelcast.query.Predicate<String, T> predicate,
                int pageSize) {
            this.map = map;
            this.predicate = predicate;
            this.pageSize = pageSize;
            loadPage();
        }

        private void loadPage() {
            PagingPredicate<String, T> pagingPredicate =
                    new PagingPredicateImpl<>(predicate, pageSize);
            pagingPredicate.setPage(pageIndex);
            Iterable<Entry<String, T>> entrySet = map.entrySet(pagingPredicate);
            List<T> values = new ArrayList<>();
            for (var entry : entrySet) {
                values.add(entry.getValue());
            }
            currentPageIterator = values.iterator();
            lastPage = values.size() < pageSize;
        }

        @Override
        public boolean hasNext() {
            if (currentPageIterator == null) {
                return false;
            }
            if (currentPageIterator.hasNext()) {
                return true;
            }
            if (!lastPage) {
                pageIndex++;
                loadPage();
                return hasNext();
            }
            return false;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return currentPageIterator.next();
        }
    }
}
