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
package com.norconex.crawler.core.grid.impl.local;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.grid.GridStorage;
import com.norconex.crawler.core.grid.GridStore;
import com.norconex.crawler.core.util.SerialUtil;
import com.norconex.shaded.h2.mvstore.MVMap;
import com.norconex.shaded.h2.mvstore.MVStore;

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
    public <T> GridCache<T> getCache(String name, Class<? extends T> type) {
        return (GridCache<T>) getOrCreateStore(name, type, GridCache.class);
    }

    @JsonIgnore
    @Override
    public GridCache<String> getGlobalCache() {
        return (GridCache<String>) getOrCreateStore(
                GLOBAL_CACHE_KEY, String.class, GridCache.class);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(String name, Class<? extends T> type) {
        return (GridQueue<T>) getOrCreateStore(name, type, GridQueue.class);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Override
    public <T> GridSet<T> getSet(String name, Class<? extends T> type) {
        return (GridSet<T>) getOrCreateStore(name, type, GridSet.class);
    }

    @JsonIgnore
    @Override
    public Set<String> getStoreNames() {
        return getActualStoreNames()
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
                    entry.getStoreType()));
        });
    }

    GridStore<?> concreteStore(
            Class<?> storeSuperType,
            String storeName,
            Class<?> objectType) {

        @SuppressWarnings("rawtypes")
        Class<? extends GridStore> concreteType = LocalGridCache.class;
        if (storeSuperType.equals(GridQueue.class)) {
            concreteType = LocalGridQueue.class;
        } else if (storeSuperType.equals(GridSet.class)) {
            concreteType = LocalGridSet.class;
        }
        return ClassUtil.newInstance(
                concreteType,
                mvStore,
                storeName,
                objectType);
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

    private ListValuedMap<String, String> getActualStoreNames() {
        var names = new ArrayListValuedHashMap<String, String>();
        mvStore.getMapNames().forEach(
                nm -> names.put(
                        StringUtils.substringBeforeLast(nm, SUFFIX_SEPARATOR),
                        nm));
        return names;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class StoreTypes {
        private Class<?> objectType;
        private Class<?> storeType;
    }

}
