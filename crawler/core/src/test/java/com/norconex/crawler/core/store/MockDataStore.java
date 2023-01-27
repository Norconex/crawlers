/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.store;

import java.util.Optional;
import java.util.function.BiPredicate;

public class MockDataStore<T> implements DataStore<T> {
    @Override
    public String getName() {
        return null;
    }
    @Override
    public void save(String id, T object) {
    }
    @Override
    public Optional<T> find(String id) {
        return Optional.empty();
    }
    @Override
    public Optional<T> findFirst() {
        return Optional.empty();
    }
    @Override
    public boolean exists(String id) {
        return false;
    }
    @Override
    public long count() {
        return 0;
    }
    @Override
    public boolean delete(String id) {
        return false;
    }
    @Override
    public Optional<T> deleteFirst() {
        return Optional.empty();
    }
    @Override
    public void clear() {
    }
    @Override
    public void close() {
    }
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return false;
    }
    @Override
    public boolean isEmpty() {
        return false;
    }
}
