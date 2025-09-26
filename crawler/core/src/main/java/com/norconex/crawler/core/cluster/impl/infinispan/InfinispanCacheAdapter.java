package com.norconex.crawler.core.cluster.impl.infinispan;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.infinispan.commons.api.query.Query;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.util.function.SerializableFunction;

import com.norconex.crawler.core.cluster.Cache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InfinispanCacheAdapter<T> implements Cache<T> {

    private final org.infinispan.Cache<String, T> delegate;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final java.util.Set<String> CLOSED_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public InfinispanCacheAdapter(org.infinispan.Cache<String, T> delegate) {
        this.delegate = delegate;
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
            // Avoid serializing non-serializable lambdas across the cluster.
            // Use a safe get/putIfAbsent pattern which remains atomic for insertion
            // and returns the winning value.
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
        return Optional.ofNullable(supplyIfCache(
                () -> delegate.computeIfPresent(key, remappingFunction), null));
    }

    @Override
    public Optional<T> compute(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        return Optional.ofNullable(supplyIfCache(
                () -> delegate.compute(key, remappingFunction), null));
    }

    @Override
    public T merge(String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        return supplyIfCache(
                () -> delegate.merge(key, value, remappingFunction), null);
    }

    @Override
    public boolean containsKey(String key) {
        return supplyIfCache(() -> delegate.containsKey(key), false);
    }

    @Override
    public T getOrDefault(String key, T defaultValue) {
        return supplyIfCache(
                () -> delegate.getOrDefault(key, defaultValue), null);
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

    @SuppressWarnings("unchecked")
    @Override
    public List<T> query(String queryExpression) {
        return (List<T>) supplyIfCache(
                () -> delegate.query(queryExpression).execute().list(),
                Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<T> queryIterator(String queryExpression) {
        return (Iterator<T>) supplyIfCache(
                () -> delegate.query(queryExpression).execute().list()
                        .stream()
                        .iterator(),
                Collections.emptyIterator());
    }

    @Override
    public List<T> queryPaged(String queryExpression, int startOffset,
            int maxResults) {
        Query<T> query = delegate.query(queryExpression);
        query.startOffset(startOffset);
        query.maxResults(maxResults);
        return query.execute().list();
    }

    @Override
    public void queryStream(
            String queryExpression, Consumer<T> consumer, int batchSize) {
        runIfCache(() -> {
            var bsize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;

            // In Infinispan 15.2, it's better to use the paged approach for
            // streaming
            var totalCount = count(queryExpression);
            var offset = 0;

            LOG.debug("Starting streaming query with {} total results "
                    + "using batch size {}", totalCount, bsize);

            while (offset < totalCount) {
                // Process one batch at a time
                var batch = queryPaged(queryExpression, offset, bsize);
                if (batch.isEmpty()) {
                    break;
                }

                // Process each entry in the batch
                batch.forEach(consumer);

                offset += batch.size();

                // Log progress
                if (offset % (bsize * 10) == 0 || offset >= totalCount) {
                    LOG.debug("Processed {} entries out of {}", offset,
                            totalCount);
                }
            }

            LOG.debug("Finished streaming query, processed {} results", offset);
        });
    }

    @Override
    public long count(String queryExpression) {
        return supplyIfCache(
                () -> {
                    Query<Object> query = delegate.query(queryExpression);
                    return (long) query.execute().count().value();
                }, -1L);
    }

    @Override
    public long size() {
        return supplyIfCache(delegate::size, -1);
    }

    @Override
    public long delete(String queryExpression) {
        return supplyIfCache(() -> {

            // First check count to avoid unnecessary work
            var totalCount = count(queryExpression);
            if (totalCount == 0) {
                return 0;
            }

            // Use iterator-based deletion for better memory efficiency
            var batchSize = DEFAULT_BATCH_SIZE;
            var deletedCount = 0L;

            LOG.debug("Deleting approximately {} entries matching: {}",
                    totalCount, queryExpression);

            // Process in batches to avoid loading too many objects into
            // memory at once
            while (deletedCount < totalCount) {
                Query<T> query = delegate.query(queryExpression);
                query.maxResults(batchSize);

                var batch = query.execute().list();
                if (batch.isEmpty()) {
                    break;
                }

                // Find and delete the keys for this batch
                var keysToDelete = batch.stream().flatMap(
                        entry -> delegate.entrySet().stream()
                                .filter(e -> e.getValue().equals(entry))
                                .map((SerializableFunction<
                                        ? super Entry<String, T>,
                                        String>) Entry::getKey))
                        .toList();

                // Delete the found keys
                keysToDelete.forEach(delegate::remove);
                deletedCount += keysToDelete.size();

                LOG.debug("Deleted {} entries so far", deletedCount);
            }

            LOG.debug("Finished deleting {} entries", deletedCount);
            return deletedCount;
        }, -1).longValue();
    }

    @Override
    public void forEach(BiConsumer<String, ? super T> action) {
        runIfCache(() -> delegate.forEach(action::accept));
    }

    public org.infinispan.Cache<String, T> vendor() {
        return delegate;
    }

    private <R> R supplyIfCache(Supplier<R> supplier, R defaultValue) {
        return isCacheClosed() ? defaultValue : supplier.get();
    }

    private void runIfCache(Runnable runnalbe) {
        if (!isCacheClosed()) {
            runnalbe.run();
        }
    }

    private boolean isCacheClosed() {
        var status = delegate.getStatus();
        if (status == ComponentStatus.TERMINATED
                || status == ComponentStatus.FAILED) {
            var name = delegate.getName();
            if (CLOSED_LOGGED.add(name)) {
                LOG.warn("Attempted to use cache '{}' after it was closed.",
                        name);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("(suppressed) Attempted to use closed cache '{}'.",
                        name);
            }
            return true; // skip operation
        }
        return false;
    }

}