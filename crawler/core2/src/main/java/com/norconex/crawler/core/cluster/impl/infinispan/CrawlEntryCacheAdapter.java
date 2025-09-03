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
package com.norconex.crawler.core.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core2.ledger.CrawlEntry;

/**
 * A Cache adapter that automatically converts between the domain object
 * {@link CrawlEntry} and its Protostream-annotated adapter
 * {@link CrawlEntryProtoAdapter}. This allows application code to work
 * purely with the domain object while the persistence and indexing details
 * are handled transparently.
 */
public class CrawlEntryCacheAdapter implements Cache<CrawlEntry> {

    private final Cache<CrawlEntryProtoAdapter> adaptedCache;

    public CrawlEntryCacheAdapter(Cache<CrawlEntryProtoAdapter> cacheToAdapt) {
        adaptedCache = cacheToAdapt;
    }

    @Override
    public boolean isEmpty() {
        return adaptedCache.isEmpty();
    }

    @Override
    public void put(String key, CrawlEntry value) {
        adaptedCache.put(key, new CrawlEntryProtoAdapter(value));
    }

    @Override
    public Optional<CrawlEntry> get(String key) {
        return adaptedCache.get(key).map(CrawlEntryProtoAdapter::toCrawlEntry);
    }

    @Override
    public void remove(String key) {
        adaptedCache.remove(key);
    }

    @Override
    public void clear() {
        adaptedCache.clear();
    }

    @Override
    public CrawlEntry computeIfAbsent(String key,
            Function<String, ? extends CrawlEntry> mappingFunction) {
        var adapter = adaptedCache.computeIfAbsent(key,
                k -> ofNullable(mappingFunction.apply(k))
                        .map(CrawlEntryProtoAdapter::new)
                        .orElse(null));
        return adapter == null ? null : adapter.toCrawlEntry();
    }

    @Override
    public Optional<CrawlEntry> computeIfPresent(String key,
            BiFunction<String, ? super CrawlEntry,
                    ? extends CrawlEntry> remappingFunction) {
        return adaptedCache
                .computeIfPresent(key, (k, existingAdapter) -> ofNullable(
                        existingAdapter)
                                .map(CrawlEntryProtoAdapter::toCrawlEntry)
                                .map(entry -> remappingFunction.apply(k, entry))
                                .map(CrawlEntryProtoAdapter::new)
                                .orElse(null))
                .map(CrawlEntryProtoAdapter::toCrawlEntry);
    }

    @Override
    public Optional<CrawlEntry> compute(String key,
            BiFunction<String, ? super CrawlEntry,
                    ? extends CrawlEntry> remappingFunction) {
        return adaptedCache
                .compute(key, (k, existingAdapter) -> {
                    var existing = ofNullable(existingAdapter)
                            .map(CrawlEntryProtoAdapter::toCrawlEntry)
                            .orElse(null);
                    var updated = remappingFunction.apply(k, existing);
                    return updated == null
                            ? null
                            : new CrawlEntryProtoAdapter(updated);
                })
                .map(CrawlEntryProtoAdapter::toCrawlEntry);
    }

    @Override
    public CrawlEntry merge(String key, CrawlEntry value,
            BiFunction<? super CrawlEntry, ? super CrawlEntry,
                    ? extends CrawlEntry> remappingFunction) {
        var mergedAdapter = adaptedCache.merge(key,
                new CrawlEntryProtoAdapter(value),
                (a1, a2) -> {
                    var v1 = ofNullable(a1)
                            .map(CrawlEntryProtoAdapter::toCrawlEntry)
                            .orElse(null);
                    var v2 = ofNullable(a2)
                            .map(CrawlEntryProtoAdapter::toCrawlEntry)
                            .orElse(null);
                    var merged = remappingFunction.apply(v1, v2);
                    return merged == null ? null
                            : new CrawlEntryProtoAdapter(merged);
                });
        return ofNullable(mergedAdapter)
                .map(CrawlEntryProtoAdapter::toCrawlEntry)
                .orElse(null);
    }

    @Override
    public boolean containsKey(String key) {
        return adaptedCache.containsKey(key);
    }

    @Override
    public CrawlEntry getOrDefault(String key, CrawlEntry defaultValue) {
        var adapter = adaptedCache.getOrDefault(key,
                ofNullable(defaultValue).map(CrawlEntryProtoAdapter::new)
                        .orElse(null));
        return ofNullable(adapter).map(CrawlEntryProtoAdapter::toCrawlEntry)
                .orElse(null);
    }

    @Override
    public CrawlEntry putIfAbsent(String key, CrawlEntry value) {
        var previous = adaptedCache.putIfAbsent(key,
                value == null ? null : new CrawlEntryProtoAdapter(value));
        return ofNullable(previous).map(CrawlEntryProtoAdapter::toCrawlEntry)
                .orElse(null);
    }

    @Override
    public List<CrawlEntry> query(String queryExpression) {
        return adaptedCache.query(queryExpression).stream()
                .map(CrawlEntryProtoAdapter::toCrawlEntry)
                .collect(Collectors.toList());
    }

    @Override
    public boolean replace(String key, CrawlEntry oldValue,
            CrawlEntry newValue) {
        return adaptedCache.replace(key,
                ofNullable(oldValue).map(CrawlEntryProtoAdapter::new)
                        .orElse(null),
                ofNullable(newValue).map(CrawlEntryProtoAdapter::new)
                        .orElse(null));
    }

    @Override
    public Iterator<CrawlEntry> queryIterator(String queryExpression) {
        var it = adaptedCache.queryIterator(queryExpression);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public CrawlEntry next() {
                return it.next().toCrawlEntry();
            }
        };
    }

    @Override
    public List<CrawlEntry> queryPaged(String queryExpression, int startOffset,
            int maxResults) {
        return adaptedCache.queryPaged(queryExpression, startOffset, maxResults)
                .stream()
                .map(CrawlEntryProtoAdapter::toCrawlEntry)
                .collect(Collectors.toList());
    }

    @Override
    public void queryStream(String queryExpression,
            Consumer<CrawlEntry> consumer,
            int batchSize) {
        adaptedCache.queryStream(queryExpression,
                adapter -> consumer.accept(adapter.toCrawlEntry()), batchSize);
    }

    @Override
    public long count(String queryExpression) {
        return adaptedCache.count(queryExpression);
    }

    @Override
    public long size() {
        return adaptedCache.size();
    }

    @Override
    public long delete(String queryExpression) {
        return adaptedCache.delete(queryExpression);
    }

    @Override
    public void forEach(BiConsumer<String, ? super CrawlEntry> action) {
        adaptedCache.forEach((k, v) -> action.accept(k, v.toCrawlEntry()));
    }

}
