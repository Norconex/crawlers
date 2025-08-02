/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheException;

/**
 * Hazelcast implementation of the Cache interface.
 * @param <T> Type of objects stored in the cache
 */
public class HazelcastCacheAdapter<T> implements Cache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastCacheAdapter.class);
    
    private final IMap<String, StringSerializedObject> cache;
    private final Class<T> type;
    private final String name;
    
    public HazelcastCacheAdapter(
            HazelcastInstance hazelcastInstance, String name, Class<T> type) {
        Objects.requireNonNull(hazelcastInstance, "Hazelcast instance cannot be null");
        this.name = Objects.requireNonNull(name, "Cache name cannot be null");
        this.type = Objects.requireNonNull(type, "Cache type cannot be null");
        
        this.cache = hazelcastInstance.getMap("nx-cache-" + name);
        LOG.info("Created Hazelcast cache: {}", name);
    }
    
    @Override
    public void put(String key, T value) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        if (value == null) {
            cache.remove(key);
            return;
        }
        
        try {
            String json = HazelcastUtil.toJson(value);
            cache.put(key, new StringSerializedObject(json));
        } catch (IOException e) {
            throw new CacheException(
                    "Failed to serialize value for key: " + key, e);
        }
    }

    @Override
    public void put(String key, T value, long ttl) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        if (value == null) {
            cache.remove(key);
            return;
        }
        
        try {
            String json = HazelcastUtil.toJson(value);
            cache.put(key, new StringSerializedObject(json), ttl,
                    TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            throw new CacheException(
                    "Failed to serialize value for key: " + key, e);
        }
    }
    
    @Override
    public Optional<T> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        
        StringSerializedObject serialized = cache.get(key);
        if (serialized == null) {
            return Optional.empty();
        }
        
        try {
            T value = HazelcastUtil.fromJson(serialized.getSerializedValue(), type);
            return Optional.ofNullable(value);
        } catch (IOException e) {
            throw new CacheException(
                    "Failed to deserialize value for key: " + key, e);
        }
    }
    
    @Override
    public boolean isEmpty() {
        return cache.isEmpty();
    }
    
    @Override
    public void remove(String key) {
        if (key != null) {
            cache.remove(key);
        }
    }
    
    @Override
    public void clear() {
        cache.clear();
    }
    
    @Override
    public T computeIfAbsent(String key, Function<String, ? extends T> mappingFunction) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(mappingFunction, "Mapping function cannot be null");
        
        if (cache.containsKey(key)) {
            return get(key).orElse(null);
        }
        
        T value = mappingFunction.apply(key);
        if (value != null) {
            put(key, value);
        }
        return value;
    }
    
    @Override
    public Optional<T> computeIfPresent(String key, BiFunction<String, ? super T, ? extends T> remappingFunction) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        
        Optional<T> current = get(key);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        
        T newValue = remappingFunction.apply(key, current.get());
        if (newValue == null) {
            remove(key);
            return Optional.empty();
        } else {
            put(key, newValue);
            return Optional.of(newValue);
        }
    }
    
    @Override
    public Optional<T> compute(String key, BiFunction<String, ? super T, ? extends T> remappingFunction) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        
        Optional<T> current = get(key);
        T newValue = remappingFunction.apply(key, current.orElse(null));
        
        if (newValue == null) {
            remove(key);
            return Optional.empty();
        } else {
            put(key, newValue);
            return Optional.of(newValue);
        }
    }
    
    @Override
    public T merge(String key, T value, BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        
        Optional<T> current = get(key);
        T newValue = current.isPresent() 
                ? remappingFunction.apply(current.get(), value)
                : value;
                
        if (newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        
        return newValue;
    }
    
    @Override
    public boolean containsKey(String key) {
        return key != null && cache.containsKey(key);
    }
    
    @Override
    public T getOrDefault(String key, T defaultValue) {
        return get(key).orElse(defaultValue);
    }
    
    @Override
    public T putIfAbsent(String key, T value) {
        Objects.requireNonNull(key, "Key cannot be null");
        
        if (value == null) {
            return get(key).orElse(null);
        }
        
        Optional<T> existing = get(key);
        if (existing.isEmpty()) {
            put(key, value);
            return null;
        }
        return existing.get();
    }
    
    @Override
    public List<T> query(String queryExpression) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            List<T> results = new ArrayList<>();
            
            for (Entry<String, StringSerializedObject> entry : cache.entrySet(predicate)) {
                T value = HazelcastUtil.fromJson(entry.getValue().getSerializedValue(), type);
                if (value != null) {
                    results.add(value);
                }
            }
            
            return results;
        } catch (IOException e) {
            throw new CacheException("Failed to execute query: " + queryExpression, e);
        }
    }
    
    @Override
    public Iterator<T> queryIterator(String queryExpression) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            Iterator<Entry<String, StringSerializedObject>> entries = cache.entrySet(predicate).iterator();
            
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return entries.hasNext();
                }
                
                @Override
                public T next() {
                    try {
                        Entry<String, StringSerializedObject> entry = entries.next();
                        return HazelcastUtil.fromJson(entry.getValue().getSerializedValue(), type);
                    } catch (IOException e) {
                        throw new CacheException("Failed to deserialize value during iteration", e);
                    }
                }
            };
        } catch (Exception e) {
            throw new CacheException("Failed to execute query iterator: " + queryExpression, e);
        }
    }
    
    @Override
    public List<T> queryPaged(String queryExpression, int startOffset, int maxResults) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            List<T> results = new ArrayList<>();
            
            int count = 0;
            for (Entry<String, StringSerializedObject> entry : cache.entrySet(predicate)) {
                if (count >= startOffset && results.size() < maxResults) {
                    T value = HazelcastUtil.fromJson(entry.getValue().getSerializedValue(), type);
                    if (value != null) {
                        results.add(value);
                    }
                }
                count++;
                if (results.size() >= maxResults) {
                    break;
                }
            }
            
            return results;
        } catch (IOException e) {
            throw new CacheException("Failed to execute paged query: " + queryExpression, e);
        }
    }
    
    @Override
    public void queryStream(String queryExpression, Consumer<T> consumer, int batchSize) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            int count = 0;
            List<T> batch = new ArrayList<>(batchSize);
            
            for (Entry<String, StringSerializedObject> entry : cache.entrySet(predicate)) {
                T value = HazelcastUtil.fromJson(entry.getValue().getSerializedValue(), type);
                if (value != null) {
                    batch.add(value);
                    count++;
                    
                    if (count % batchSize == 0) {
                        // Process the batch
                        batch.forEach(consumer);
                        batch.clear();
                    }
                }
            }
            
            // Process any remaining items
            if (!batch.isEmpty()) {
                batch.forEach(consumer);
            }
        } catch (IOException e) {
            throw new CacheException("Failed to execute streaming query: " + queryExpression, e);
        }
    }
    
    @Override
    public long count(String queryExpression) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            return cache.entrySet(predicate).size();
        } catch (Exception e) {
            throw new CacheException("Failed to count query results: " + queryExpression, e);
        }
    }
    
    @Override
    public long delete(String queryExpression) {
        try {
            Predicate<String, StringSerializedObject> predicate = createPredicate(queryExpression);
            Set<String> keysToDelete = cache.keySet(predicate);
            long count = keysToDelete.size();
            
            for (String key : keysToDelete) {
                cache.remove(key);
            }
            
            return count;
        } catch (Exception e) {
            throw new CacheException("Failed to delete by query: " + queryExpression, e);
        }
    }
    
    /**
     * Creates a Hazelcast predicate from the given query expression.
     * Currently supports simple key pattern matching.
     */
    private Predicate<String, StringSerializedObject> createPredicate(String queryExpression) {
        if (queryExpression == null || queryExpression.isBlank()) {
            return Predicates.alwaysTrue();
        }
        
        // For now, treat query expressions as SQL LIKE patterns for keys
        if (queryExpression.contains("%")) {
            String regex = queryExpression.replace("%", ".*");
            return Predicates.regex("__key", regex);
        } else {
            // Exact match on key
            return Predicates.equal("__key", queryExpression);
        }
    }
    
    // These methods are not part of the interface but are useful for testing and debugging
    
    /**
     * Returns the name of this cache.
     * @return cache name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the type of objects stored in this cache.
     * @return object type
     */
    public Class<T> getType() {
        return type;
    }
    
    /**
     * Returns the number of entries in this cache.
     * @return number of entries
     */
    public long size() {
        return cache.size();
    }
    
    @Override
    public String toString() {
        return "HazelcastCacheAdapter [name=" + name 
                + ", type=" + type.getSimpleName() 
                + ", size=" + size() + "]";
    }
}
