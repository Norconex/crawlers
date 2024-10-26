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

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.lang.IgniteBiTuple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.grid.GridStorage;
import com.norconex.crawler.core.grid.GridStore;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class IgniteGridStorage implements GridStorage {

    private static final String SUFFIX_SEPARATOR = "__";
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

    private final IgniteGridInstance igniteGridInstance;

    private IgniteGridCache<
            IgniteBiTuple<Class<?>, Class<?>>> cacheObjectAndStoreTypes;

    @JsonIgnore
    @Override
    public <T> GridCache<T> getCache(
            @NonNull String storeName, @NonNull Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridCache.class);
        return new IgniteGridCache<>(
                igniteGridInstance.get(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public GridCache<String> getGlobalCache() {
        return new IgniteGridCache<>(
                igniteGridInstance.get(), IgniteGridKeys.GLOBAL_CACHE,
                String.class);
    }

    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(
            String storeName, Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridQueue.class);
        return new IgniteGridQueue<>(igniteGridInstance.get(), storeName,
                objectType);
    }

    @JsonIgnore
    @Override
    public <T> GridSet<T> getSet(
            String storeName, Class<? extends T> objectType) {
        cacheObjectAndStoreTypes(storeName, objectType, GridSet.class);
        return new IgniteGridSet<>(igniteGridInstance.get(), storeName,
                objectType);
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
            storeConsumer.accept(concreteStore(
                    objAndStoreTypes.get2(),
                    name,
                    objAndStoreTypes.get1()));
        });
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
                igniteGridInstance.get(),
                storeName,
                objectType);
    }

    private void cacheObjectAndStoreTypes(
            String storeName, Class<?> objectType, Class<?> storeType) {
        objectAndStoreTypes().put(storeName, new IgniteBiTuple<>(
                objectType, storeType));
    }

    private synchronized IgniteGridCache<IgniteBiTuple<Class<?>, Class<?>>>
            objectAndStoreTypes() {
        if (cacheObjectAndStoreTypes == null) {
            cacheObjectAndStoreTypes = new IgniteGridCache<>(
                    igniteGridInstance.get(), STORE_TYPES_KEY,
                    IgniteBiTuple.class);
        }
        return cacheObjectAndStoreTypes;
    }

    private ListValuedMap<String, String> getActualStoreNames() {
        var names = new ArrayListValuedHashMap<String, String>();
        igniteGridInstance.get().cacheNames().forEach(
                nm -> names.put(
                        StringUtils.substringBeforeLast(nm, SUFFIX_SEPARATOR),
                        nm));
        return names;
    }
}
