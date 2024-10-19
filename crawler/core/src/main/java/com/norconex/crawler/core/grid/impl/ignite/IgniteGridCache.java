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

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;

import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.util.SerializableUnaryOperator;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.StandardException;

public class IgniteGridCache<T> implements GridCache<T> {

    private String name;
    private final IgniteCache<String, T> cache;
    @Getter
    private final Class<? extends T> type;

    @NonNull
    IgniteGridCache(Ignite ignite, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        var cfg = new CacheConfiguration<String, T>();
        cfg.setName(name + IgniteGridStorage.Suffix.CACHE);
        cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cfg.setWriteSynchronizationMode(
                CacheWriteSynchronizationMode.FULL_SYNC);
        cfg.setReadFromBackup(false);

        // Until we have better... for size()
        cfg.setStatisticsEnabled(true);

        //        // Creating SQL table in order to get accurate row count. See getSize().
        //
        //        cfg.setSqlSchema("PUBLIC");
        //        var queryEntity = new QueryEntity(String.class, Object.class)
        //                .setTableName(name)// + IgniteGridStorage.Suffix.CACHE)
        //                .setKeyFieldName("key")
        //                .addQueryField("key", String.class.getName(), null);
        //        //                .addQueryField("value", type.getName(), null);
        //        //        queryEntity.setValueType(BinaryObject.class.getName());
        //
        //        queryEntity.setIndexes(List.of(new QueryIndex("key")));
        //        cfg.setQueryEntities(Collections.singletonList(queryEntity));

        cache = ignite.getOrCreateCache(cfg);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean put(String key, T object) {
        return !Objects.equals(cache.getAndPut(key, object), object);
    }

    @Override
    public boolean update(String key, SerializableUnaryOperator<T> updater) {
        var changed = new MutableBoolean();
        cache.invoke(key,
                (CacheEntryProcessor<String, T, T>) (entry, arguments) -> {
                    var existingValue = entry.getValue();
                    var newValue = updater.apply(existingValue);
                    changed.setValue(Objects.equals(existingValue, newValue));
                    return newValue;
                });
        return changed.booleanValue();
    }

    @Override
    public T get(String key) {
        return cache.get(key);
    }

    @Override
    public boolean delete(String key) {
        return cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
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
    public boolean contains(Object key) {
        return cache.containsKey((String) key);
    }

    @Override
    public long size() {
        //TODO rely on atomic long instead for counting. :-(
        return cache.metrics().getCacheSize();
        // Use SQL to get accurate record count. It is apparently the most
        // efficient way to get it.
        //        var query = new SqlFieldsQuery(
        //                "SELECT COUNT(*) FROM %s".formatted(name));
        //        QueryCursor<List<?>> cursor = cache.query(query);
        //        return (Long) cursor.getAll().get(0).get(0);
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
