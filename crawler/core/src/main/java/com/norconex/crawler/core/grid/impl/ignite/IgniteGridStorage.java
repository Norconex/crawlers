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

import java.io.Serializable;
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class IgniteGridStorage implements GridStorage {

    static final String SUFFIX_SEPARATOR = "__";
    private static final String STORE_TYPES_KEY =
            "ignite.object.and.store.types";

    // caches always have one or more suffixes when stored.
    @RequiredArgsConstructor
    enum Suffix {
        CACHE(SUFFIX_SEPARATOR + "cache"),
        SET(SUFFIX_SEPARATOR + "set"),
        QUEUE(SUFFIX_SEPARATOR + "queue"),
        DICT(SUFFIX_SEPARATOR + "dict");

        private final String value;

        @Override
        public String toString() {
            return value;
        }
    }

    private final IgniteGrid igniteGrid;

    private IgniteGridCache<ObjectAndStoreTypes> cacheObjectAndStoreTypes;

    @JsonIgnore
    @Override
    public <T> GridCache<T> getCache(
            @NonNull String storeName, @NonNull Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridCache.class);
        return new IgniteGridCache<>(
                igniteGrid.getIgnite(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public GridCache<String> getGlobalCache() {
        return new IgniteGridCache<>(
                igniteGrid.getIgnite(),
                IgniteGridKeys.GLOBAL_CACHE,
                String.class);
    }

    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(
            String storeName, Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridQueue.class);
        return new IgniteGridQueue<>(
                igniteGrid.getIgnite(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public <T> GridSet<T> getSet(
            String storeName, Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridSet.class);
        return new IgniteGridSet<>(
                igniteGrid.getIgnite(), storeName, objectType);
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
            var objAndStoreTypes = objectAndStoreTypes().get(name);
            if (objAndStoreTypes != null) {
                storeConsumer.accept(concreteStore(
                        objAndStoreTypes.getStoreType(),
                        name,
                        objAndStoreTypes.getObjectType()));
            }
        });
    }

    @Override
    public void clean() {
        // We clear the "run-once" and "global-cache" and destroy others
        igniteGrid.getIgnite().destroyCaches(
                igniteGrid
                        .getIgnite()
                        .cacheNames()
                        .stream()
                        .filter(nm -> StringUtils.equalsAny(
                                IgniteGridUtil.cacheExternalName(nm),
                                IgniteGridKeys.RUN_ONCE_CACHE,
                                IgniteGridKeys.GLOBAL_CACHE))
                        .toList());
        igniteGrid.storage().getCache(
                IgniteGridKeys.RUN_ONCE_CACHE, String.class).clear();
        igniteGrid.storage().getGlobalCache().clear();
    }

    GridStore<?> concreteStore(
            Class<?> storeSuperType,
            String storeName,
            Class<?> objectType) {

        @SuppressWarnings("rawtypes")
        Class<? extends GridStore> concreteType = IgniteGridCache.class;
        if (storeSuperType.equals(GridQueue.class)) {
            concreteType = IgniteGridQueue.class;
        } else if (storeSuperType.equals(GridSet.class)) {
            concreteType = IgniteGridSet.class;
        }
        return ClassUtil.newInstance(
                concreteType,
                igniteGrid.getIgnite(),
                storeName,
                objectType);
    }

    private <T> void cacheObjectAndStoreTypes(
            String storeName,
            Class<? extends T> objectType,
            Class<?> storeType) {
        objectAndStoreTypes().put(
                storeName, new ObjectAndStoreTypes(objectType, storeType));
    }

    private synchronized IgniteGridCache<ObjectAndStoreTypes>
            objectAndStoreTypes() {
        if (cacheObjectAndStoreTypes == null) {
            cacheObjectAndStoreTypes = new IgniteGridCache<>(
                    igniteGrid.getIgnite(),
                    STORE_TYPES_KEY,
                    ObjectAndStoreTypes.class);
        }
        return cacheObjectAndStoreTypes;
    }

    private ListValuedMap<String, String> getActualStoreNames() {
        var names = new ArrayListValuedHashMap<String, String>();
        igniteGrid.getIgnite().cacheNames().forEach(
                nm -> names.put(
                        IgniteGridUtil.cacheExternalName(nm),
                        nm));
        return names;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ObjectAndStoreTypes implements Serializable {
        private static final long serialVersionUID = 1L;
        private Class<?> objectType;
        private Class<?> storeType;
    }
}
