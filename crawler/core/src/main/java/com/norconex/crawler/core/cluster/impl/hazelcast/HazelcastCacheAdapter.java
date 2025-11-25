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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import com.norconex.crawler.core.cluster.Cache;

import lombok.extern.slf4j.Slf4j;

/**
 * Hazelcast IMap-based implementation of the Cache interface.
 * Supports SQL-like queries by converting query syntax to
 * Hazelcast predicates.
 *
 * @param <T> the type of values stored in the cache
 */
@Slf4j
public class HazelcastCacheAdapter<T> implements Cache<T> {

    private final IMap<String, T> delegate;
    private final HazelcastInstance hazelcastInstance;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Set<String> CLOSED_LOGGED =
            ConcurrentHashMap.newKeySet();

    // Pattern to parse simple SQL-like queries:
    // "FROM ClassName WHERE field = 'value'"
    private static final Pattern QUERY_PATTERN = Pattern.compile(
            "FROM\\s+[\\w.]+\\s+WHERE\\s+(\\w+)\\s*=\\s*'([^']*)'",
            Pattern.CASE_INSENSITIVE);

    // Pattern to parse ORDER BY queries
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "ORDER\\s+BY\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    public HazelcastCacheAdapter(
            IMap<String, T> delegate,
            HazelcastInstance hazelcastInstance) {
        this.delegate = delegate;
        this.hazelcastInstance = hazelcastInstance;
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
            T oldValue = delegate.get(key);
            if (oldValue != null) {
                T newValue = remappingFunction.apply(key, oldValue);
                if (newValue != null) {
                    delegate.put(key, newValue);
                    return newValue;
                } else {
                    delegate.remove(key);
                }
            }
            return null;
        }, null));
    }

    @Override
    public Optional<T> compute(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional.ofNullable(supplyIfCache(() -> {
            T oldValue = delegate.get(key);
            T newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                delegate.put(key, newValue);
                return newValue;
            } else if (oldValue != null) {
                delegate.remove(key);
            }
            return null;
        }, null));
    }

    @Override
    public T merge(String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        return supplyIfCache(() -> {
            T oldValue = delegate.get(key);
            T newValue =
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
    public List<T> query(String queryExpression) {
        return supplyIfCache(() -> {
            var predicate = parseQueryToPredicate(queryExpression);
            if (predicate != null) {
                return new ArrayList<>(delegate.values(predicate));
            }
            // If no predicate could be parsed, return all values
            return new ArrayList<>(delegate.values());
        }, Collections.emptyList());
    }

    @Override
    public Iterator<T> queryIterator(String queryExpression) {
        return supplyIfCache(() -> query(queryExpression).iterator(),
                Collections.emptyIterator());
    }

    @Override
    public List<T> queryPaged(String queryExpression, int startOffset,
            int maxResults) {
        return supplyIfCache(() -> {
            var allResults = query(queryExpression);
            int fromIndex = Math.min(startOffset, allResults.size());
            int toIndex = Math.min(startOffset + maxResults, allResults.size());
            return allResults.subList(fromIndex, toIndex);
        }, Collections.emptyList());
    }

    @Override
    public void queryStream(
            String queryExpression, Consumer<T> consumer, int batchSize) {
        runIfCache(() -> {
            var bsize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
            var results = query(queryExpression);

            LOG.trace("Starting streaming query with {} matching entries "
                    + "using batch size {}", results.size(), bsize);

            var offset = 0;
            while (offset < results.size()) {
                int endIndex = Math.min(offset + bsize, results.size());
                var batch = results.subList(offset, endIndex);
                batch.forEach(consumer);
                offset = endIndex;

                if (LOG.isTraceEnabled() && (offset % (bsize * 10) == 0
                        || offset >= results.size())) {
                    LOG.trace("Query-streamed {} entries out of {}",
                            offset, results.size());
                }
            }

            LOG.trace("Finished streaming query, streamed {} entries", offset);
        });
    }

    @Override
    public long count(String queryExpression) {
        return supplyIfCache(() -> {
            var predicate = parseQueryToPredicate(queryExpression);
            if (predicate != null) {
                return (long) delegate.values(predicate).size();
            }
            return (long) delegate.size();
        }, -1L);
    }

    @Override
    public long size() {
        return supplyIfCache(delegate::size, -1);
    }

    @Override
    public long delete(String queryExpression) {
        return supplyIfCache(() -> {
            var predicate = parseQueryToPredicate(queryExpression);
            if (predicate == null) {
                return 0L;
            }

            var keysToDelete = delegate.keySet(predicate);
            long count = keysToDelete.size();

            LOG.debug("Deleting {} entries matching query", count);

            for (var key : keysToDelete) {
                delegate.remove(key);
            }

            LOG.debug("Finished deleting {} entries", count);
            return count;
        }, -1L);
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
     * Parses a simple SQL-like query to a Hazelcast Predicate.
     * Supports queries like:
     * - "FROM ClassName WHERE field = 'value'"
     * - "FROM ClassName WHERE field = 'value' ORDER BY otherField"
     */
    @SuppressWarnings("unchecked")
    private Predicate<String, T> parseQueryToPredicate(String queryExpression) {
        if (queryExpression == null || queryExpression.isBlank()) {
            return null;
        }

        var matcher = QUERY_PATTERN.matcher(queryExpression);
        if (matcher.find()) {
            String fieldName = matcher.group(1);
            String fieldValue = matcher.group(2);

            LOG.trace("Parsed query: field={}, value={}", fieldName,
                    fieldValue);

            return Predicates.equal(fieldName, fieldValue);
        }

        LOG.debug("Could not parse query expression: {}", queryExpression);
        return null;
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
        LifecycleService lifecycle = hazelcastInstance.getLifecycleService();
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
}
