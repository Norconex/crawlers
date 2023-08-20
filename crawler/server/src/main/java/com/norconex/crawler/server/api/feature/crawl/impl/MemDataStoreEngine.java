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
package com.norconex.crawler.server.api.feature.crawl.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Memory-based data store.  Meant for tiny crawl operations
 * and NOT for full crawls.
 */
@EqualsAndHashCode
@ToString
public class MemDataStoreEngine implements DataStoreEngine {

    public static final int DEFAULT_MAX_ENTRIES = 10;

    private static final String STORE_TYPES_KEY =
            MemDataStoreEngine.class.getSimpleName() + "--storetypes";

    private final Map<String, Class<?>> storeTypes = new HashMap<>();
    private final Map<String, MemDataStore<?>> stores = new HashMap<>();

    private int maxStoreEntries;

    public MemDataStoreEngine() {
        this(DEFAULT_MAX_ENTRIES);
    }
    public MemDataStoreEngine(int maxStoreEntries) {
        this.maxStoreEntries = maxStoreEntries;
    }

    @Override
    public void init(Crawler crawler) {
        //NOOP
    }

    @Override
    public boolean clean() {
        var names = getStoreNames();
        if (!names.isEmpty()) {
            names.stream().forEach(this::dropStore);
        }
        storeTypes.clear();
        return false; // mem does not support resume
    }
    @Override
    public synchronized void close() {
        //NOOP
    }
    @Override
    public synchronized <T> DataStore<T> openStore(
            String name, Class<? extends T> type) {
        storeTypes.put(name, type);
        var store = new MemDataStore<T>(name, maxStoreEntries);
        stores.put(name, store);
        return store;
    }
    @Override
    public synchronized boolean dropStore(String name) {
        if (stores.containsKey(name)) {
            stores.remove(name);
            if (STORE_TYPES_KEY.equals(name)) {
                storeTypes.clear();
            } else {
                storeTypes.remove(name);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean renameStore(DataStore<?> store, String newName) {
        MemDataStore<?> memDateStore = (MemDataStore<?>) store;
        var hadMap = false;
        if (stores.containsKey(newName)) {
            hadMap = true;
        }
        storeTypes.put(
                newName, storeTypes.remove(memDateStore.rename(newName)));
        return hadMap;
    }

    @Override
    public Set<String> getStoreNames() {
        var names = new HashSet<>(stores.keySet());
        names.remove(STORE_TYPES_KEY);
        return names;
    }
    @Override
    public Optional<Class<?>> getStoreType(String name) {
        return Optional.ofNullable(storeTypes.get(name));
    }
}
