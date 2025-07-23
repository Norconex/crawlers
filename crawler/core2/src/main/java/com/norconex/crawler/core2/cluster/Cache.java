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

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Cache<T> {

    //TODO add listener

    void put(String key, T value);

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
}
