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
package com.norconex.crawler.core2.cluster;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public interface Cache<T> {

    //TODO add listener

    boolean isEmpty();

    void put(String key, T value);
    
    /**
     * Puts a value in the cache with a specified time-to-live.
     * After the TTL expires, the entry will be automatically removed from the cache.
     * 
     * @param key the key
     * @param value the value
     * @param ttl time-to-live in milliseconds
     */
    void put(String key, T value, long ttl);

    Optional<T> get(String key);

    void remove(String key);

    void clear();

    T computeIfAbsent(String key,
            Function<String, ? extends T> mappingFunction);

    Optional<T> computeIfPresent(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction);

    Optional<T> compute(String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction);

    T merge(String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction);

    boolean containsKey(String key);

    T getOrDefault(String key, T defaultValue);

    T putIfAbsent(String key, T value);
    
    /**
     * Queries the cache for entries matching the given query expression.
     * The query syntax depends on the cache implementation.
     * Note: This method loads all results into memory at once.
     * For large result sets, consider using {@link #queryIterator} instead.
     * 
     * @param queryExpression the query expression
     * @return a list of matching entries
     */
    List<T> query(String queryExpression);
    
    /**
     * Queries the cache for entries matching the given query expression
     * and returns an iterator for memory-efficient processing of results.
     * This avoids loading all results into memory at once.
     * 
     * @param queryExpression the query expression
     * @return an iterator over the matching entries
     */
    Iterator<T> queryIterator(String queryExpression);
    
    /**
     * Queries the cache for entries matching the given query expression
     * with pagination support.
     * 
     * @param queryExpression the query expression
     * @param startOffset the starting offset (0-based)
     * @param maxResults the maximum number of results to return
     * @return a list of matching entries within the specified range
     */
    List<T> queryPaged(String queryExpression, int startOffset, int maxResults);
    
    /**
     * Queries the cache and processes results in a streaming fashion
     * without loading all results into memory at once.
     * 
     * @param queryExpression the query expression
     * @param consumer the consumer that will process each entry
     * @param batchSize the number of entries to process in each batch
     */
    void queryStream(String queryExpression, Consumer<T> consumer, int batchSize);
    
    /**
     * Counts the number of entries matching the given query expression.
     * @param queryExpression the query expression
     * @return the count of matching entries
     */
    long count(String queryExpression);
    
    /**
     * Deletes entries matching the given query expression.
     * @param queryExpression the query expression
     * @return the number of entries deleted
     */
    long delete(String queryExpression);
}