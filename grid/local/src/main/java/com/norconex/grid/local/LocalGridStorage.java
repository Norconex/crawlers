/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.grid.local;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.TransactionStore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.core.storage.GridStore;
import com.norconex.grid.core.util.SerialUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local storage, using an embedded database persisted to local file system.
 */
@Slf4j
public class LocalGridStorage implements GridStorage {

    private static final String SUFFIX_SEPARATOR = "__";
    private static final String GLOBAL_CACHE_KEY = "global-cache";
    private static final String STORE_TYPES_KEY =
            LocalGridStorage.class.getSimpleName() + "__storetypes";

    private final MVStore mvStore;
    private final MVMap<String, String> storeTypes;

    private final Map<String, GridStore<?>> openedStores = new HashMap<>();

    public LocalGridStorage(MVStore mvStore) {
        this.mvStore = mvStore;
        storeTypes = mvStore.openMap(STORE_TYPES_KEY);
        mvStore.commit();
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Override
    public <T> GridMap<T> getMap(String name, Class<? extends T> type) {
        return (GridMap<T>) getOrCreateStore(name, type, GridMap.class);
    }

    @JsonIgnore
    @Override
    public GridMap<String> getGlobals() {
        return (GridMap<String>) getOrCreateStore(
                GLOBAL_CACHE_KEY, String.class, GridMap.class);
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
        return getStoreNamesAndVariations()
                .keySet()
                .stream()
                .filter(nm -> !STORE_TYPES_KEY.equals(nm))
                .collect(Collectors.toSet());
    }

    @Override
    public void forEachStore(Consumer<GridStore<?>> storeConsumer) {
        getStoreNames().forEach(name -> {
            var json = storeTypes.get(name);
            if (json != null) {
                var entry =
                        SerialUtil.fromJson(json, StoreTypes.class);
                storeConsumer.accept(concreteStore(
                        entry.getStoreType(),
                        name,
                        entry.getObjectType()));
            }
        });
    }

    @Override
    public <T> T runInTransaction(Callable<T> callable) {
        synchronized (this) {
            var txStore = new TransactionStore(mvStore);
            var tx = txStore.begin();
            try {
                var result = callable.call();
                tx.commit();
                return result;
            } catch (Exception e) {
                tx.rollback();
                throw new GridException(
                        "A problem occured during transaction execution.", e);
            }
        }
    }

    @Override
    public <T> Future<T> runInTransactionAsync(Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                var txStore = new TransactionStore(mvStore);
                var tx = txStore.begin();
                try {
                    var result = callable.call();
                    tx.commit();
                    return result;
                } catch (Exception e) {
                    tx.rollback();
                    throw new GridException(
                            "A problem occured during transaction execution.",
                            e);
                }
            }
        });
    }

    @Override
    public synchronized void destroy() {
        mvStore
                .getMapNames()
                .stream()
                .filter(name -> !name.equals(STORE_TYPES_KEY))
                .forEach(mapName -> {
                    storeTypes.remove(mapName);
                    openedStores.remove(mapName);
                    mvStore.removeMap(mapName);
                });
        storeTypes.clear();
    }

    @Override
    public boolean storeExists(String name) {
        return mvStore.hasMap(name);
    }

    GridStore<?> concreteStore(
            Class<?> storeSuperType, String storeName, Class<?> objectType) {
        if (storeSuperType.equals(GridQueue.class)) {
            return new LocalGridQueue<>(mvStore, storeName, objectType);
        }
        if (storeSuperType.equals(GridSet.class)) {
            return new LocalGridSet(mvStore, storeName);
        }
        return new LocalGridMap<>(mvStore, storeName, objectType);
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> GridStore<T> getOrCreateStore(
            String name, Class<T> objectType, Class<?> storeType) {
        var store = (GridStore<T>) openedStores.get(name);
        if (store == null) {
            storeTypes.put(name, SerialUtil
                    .toJsonString(new StoreTypes(objectType, storeType)));
            store = (GridStore<T>) concreteStore(storeType, name, objectType);
            openedStores.put(name, store);
        }
        return store;
    }

    // We return "external" names and their internal variations.
    // We don't return the one for storing types, that is an internal one.
    private ListValuedMap<String, String> getStoreNamesAndVariations() {
        var names = new ArrayListValuedHashMap<String, String>();
        mvStore.getMapNames().forEach(nm -> {
            if (!STORE_TYPES_KEY.equals(nm)) {
                names.put(
                        StringUtils.substringBeforeLast(nm, SUFFIX_SEPARATOR),
                        nm);
            }

        });
        return names;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StoreTypes {
        private Class<?> objectType;
        private Class<?> storeType;
    }
}
