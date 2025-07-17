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
package com.norconex.grid.calcite;

import static com.norconex.grid.core.util.SerialUtil.toJsonString;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.core.storage.GridStore;
import com.norconex.grid.core.util.SerialUtil;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Uses Apache Jdbc to run the crawler on a cluster (grid).
 * </p>
 */
@EqualsAndHashCode
@ToString
public class CalciteGridStorage implements GridStorage {

    //TODO have: volatile-cache, session-cache, and durable-cache
    private static final String SESSION_CACHE_KEY = "session-cache";
    private static final String DURABLE_CACHE_KEY = "durable-cache";
    private static final String STORE_TYPES_KEY =
            CalciteGridStorage.class.getSimpleName() + "__storetypes";

    @NonNull
    @Getter(value = AccessLevel.PROTECTED)
    private final Db dbHelper;

    private GridMap<String> storeTypes;

    private final Map<String, GridStore<?>> openedStores = new HashMap<>();

    public CalciteGridStorage(@NonNull Db dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void init() {
        storeTypes = getMap(STORE_TYPES_KEY, String.class);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Override
    public <T> GridMap<T> getMap(String name, Class<? extends T> type) {
        return (GridMap<T>) getOrCreateStore(name, type, GridMap.class);
    }

    @JsonIgnore
    @Override
    public GridMap<String> getSessionAttributes() {
        return (GridMap<String>) getOrCreateStore(
                SESSION_CACHE_KEY, String.class, GridMap.class);
    }

    @JsonIgnore
    @Override
    public GridMap<String> getDurableAttributes() {
        return (GridMap<String>) getOrCreateStore(
                DURABLE_CACHE_KEY, String.class, GridMap.class);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(String name, Class<? extends T> type) {
        return (GridQueue<T>) getOrCreateStore(name, type, GridQueue.class);
    }

    @JsonIgnore
    @Override
    public GridSet getSet(String name) {
        return (GridSet) getOrCreateStore(name, String.class, GridSet.class);
    }

    @JsonIgnore
    @Override
    public Set<String> getStoreNames() {
        return openedStores
                .keySet()
                .stream()
                .filter(nm -> !STORE_TYPES_KEY.equals(nm))
                .collect(Collectors.toSet());
    }

    @Override
    public void forEachStore(Consumer<GridStore<?>> storeConsumer) {
        getStoreNames().forEach(name -> {
            var entry =
                    SerialUtil.fromJson(storeTypes.get(name), StoreTypes.class);
            storeConsumer.accept(concreteStore(
                    entry.getStoreType(),
                    name,
                    entry.getObjectType()));
        });
    }

    @Override
    public void destroy() {
        openedStores
                .keySet()
                .stream()
                .forEach(dbHelper::dropTableIfExists);
        openedStores.clear();
    }

    @Override
    public boolean storeExists(String name) {
        return dbHelper.tableExists(dbHelper.toSafeName(name));
    }

    @Override
    public <T> T runInTransaction(Callable<T> callable) {
        return dbHelper.<T>runInTransactionAndReturn(conn -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new GridException(e);
            }
        });
    }

    @Override
    public <T> Future<T> runInTransactionAsync(Callable<T> callable) {
        return Executors.newSingleThreadExecutor()
                .submit(() -> runInTransaction(callable));
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> GridStore<T> getOrCreateStore(
            String name, Class<T> objectType, Class<?> storeType) {
        var store = (GridStore<T>) openedStores.get(name);
        if (store == null) {
            if (storeTypes != null) {
                storeTypes.put(name,
                        toJsonString(new StoreTypes(objectType, storeType)));
            }
            store = (GridStore<T>) concreteStore(storeType, name, objectType);
            openedStores.put(name, store);
        }
        return store;
    }

    GridStore<?> concreteStore(
            Class<?> storeSuperType, String storeName, Class<?> objectType) {
        if (storeSuperType.equals(GridQueue.class)) {
            return new CalciteGridQueue<>(dbHelper, storeName, objectType);
        }
        if (storeSuperType.equals(GridSet.class)) {
            return new CalciteGridSet(dbHelper, storeName);
        }
        return new CalciteGridMap<>(dbHelper, storeName, objectType);
    }

    @Override
    public void close() throws IOException {
        try {
            dbHelper.close();
        } catch (Exception e) {
            throw new IOException(
                    "Could not close grid storage connection.", e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StoreTypes {
        private Class<?> objectType;
        private Class<?> storeType;
    }
}
