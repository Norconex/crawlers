package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.infinispan.commons.api.query.Query;
import org.infinispan.util.function.SerializableFunction;

import com.norconex.crawler.core2.cluster.Cache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfinispanCacheAdapter<T> implements Cache<T> {

    private final org.infinispan.Cache<String, T> delegate;
    private static final int DEFAULT_BATCH_SIZE = 100;

    public InfinispanCacheAdapter(org.infinispan.Cache<String, T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
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

    @Override
    public boolean replace(String key, T oldValue, T newValue) {
        return delegate.replace(key, oldValue, newValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<T> query(String queryExpression) {
        return (List<T>) delegate.query(queryExpression).execute().list();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<T> queryIterator(String queryExpression) {
        return (Iterator<T>) delegate.query(queryExpression).execute().list()
                .stream()
                .iterator();
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
    public void queryStream(String queryExpression, Consumer<T> consumer,
            int batchSize) {
        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }

        // In Infinispan 15.2, it's better to use the paged approach for streaming
        var totalCount = count(queryExpression);
        var offset = 0;

        LOG.debug(
                "Starting streaming query with {} total results using batch size {}",
                totalCount, batchSize);

        while (offset < totalCount) {
            // Process one batch at a time
            var batch = queryPaged(queryExpression, offset, batchSize);
            if (batch.isEmpty()) {
                break;
            }

            // Process each entry in the batch
            batch.forEach(consumer);

            offset += batch.size();

            // Log progress
            if (offset % (batchSize * 10) == 0 || offset >= totalCount) {
                LOG.debug("Processed {} entries out of {}", offset, totalCount);
            }
        }

        LOG.debug("Finished streaming query, processed {} results", offset);
    }

    @Override
    public long count(String queryExpression) {
        Query<Object> query = delegate.query(queryExpression);
        return query.execute().count().value();
    }

    @Override
    public long countAll() {
        return delegate.size();
    }

    @Override
    public long delete(String queryExpression) {
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

        // Process in batches to avoid loading too many objects into memory at once
        while (deletedCount < totalCount) {
            Query<T> query = delegate.query(queryExpression);
            query.maxResults(batchSize);

            var batch = query.execute().list();
            if (batch.isEmpty()) {
                break;
            }

            // Find and delete the keys for this batch
            List<String> keysToDelete = batch.stream()
                    .flatMap(entry -> delegate.entrySet().stream()
                            .filter(e -> e.getValue().equals(entry))
                            .map((SerializableFunction<? super Entry<String, T>,
                                    ? extends String>) Entry::getKey))
                    .collect(Collectors.toList());

            // Delete the found keys
            keysToDelete.forEach(delegate::remove);
            deletedCount += keysToDelete.size();

            LOG.debug("Deleted {} entries so far", deletedCount);
        }

        LOG.debug("Finished deleting {} entries", deletedCount);
        return deletedCount;
    }

    @Override
    public void forEach(BiConsumer<String, ? super T> action) {
        delegate.forEach((k, v) -> action.accept(k, v));
    }
}