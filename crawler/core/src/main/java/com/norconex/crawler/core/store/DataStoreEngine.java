/* Copyright 2019-2024 Norconex Inc.
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

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.crawler.core.Crawler;

public interface DataStoreEngine extends Closeable {

    void init(Crawler crawler);
    boolean clean();
    @Override
    void close();

    <T> DataStore<T> openStore(String name, Class<? extends T> type);
    boolean dropStore(String name);

    // returns true if target was deleted
    boolean renameStore(DataStore<?> dataStore, String newName);

    @JsonIgnore
    Set<String> getStoreNames();
    @JsonIgnore
    Optional<Class<?>> getStoreType(String name);
}
