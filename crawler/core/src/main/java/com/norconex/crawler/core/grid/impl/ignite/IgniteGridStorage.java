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

import org.apache.ignite.table.Table;

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

    private static final String STORE_TYPES_KEY =
            "ignite.object.and.store.types";

    private final IgniteGrid igniteGrid;

    private IgniteGridCache<StoreSpecs> cacheObjectAndStoreTypes;

    @JsonIgnore
    @Override
    public <T> GridCache<T> getCache(
            @NonNull String storeName, @NonNull Class<? extends T> objectType) {
        cacheStoreSpecs(storeName, objectType, GridCache.class);
        return new IgniteGridCache<>(
                igniteGrid.getIgniteApi(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public GridCache<String> getGlobalCache() {
        return new IgniteGridCache<>(
                igniteGrid.getIgniteApi(),
                IgniteGridKeys.GLOBAL_CACHE,
                String.class);
    }

    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(
            String storeName, Class<? extends T> objectType) {
        cacheStoreSpecs(storeName, objectType, GridQueue.class);
        return new IgniteGridQueue<>(
                igniteGrid.getIgniteApi(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public <T> GridSet<T> getSet(
            String storeName, Class<? extends T> objectType) {
        cacheStoreSpecs(storeName, objectType, GridSet.class);
        return new IgniteGridSet<>(
                igniteGrid.getIgniteApi(), storeName, objectType);
    }

    @JsonIgnore
    @Override
    public Set<String> getStoreNames() {
        return igniteGrid
                .getIgniteApi()
                .tables()
                .tables()
                .stream()
                .map(Table::name)
                .collect(Collectors.toSet());
    }

    @Override
    public void forEachStore(Consumer<GridStore<?>> storeConsumer) {
        getStoreNames().forEach(name -> {
            var objAndStoreTypes = storeSpecs().get(name);
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
        //TODO clear vs destroy
        var catalog = igniteGrid.getIgniteApi().catalog();
        igniteGrid
                .getIgniteApi()
                .tables()
                .tables()
                .stream()
                .map(Table::name)
                .forEach(catalog::dropTable);
        //                        .filter(nm -> StringUtils.equalsAny(
        //                                IgniteGridUtil.cacheExternalName(nm),
        //                                IgniteGridKeys.RUN_ONCE_CACHE,
        //                                IgniteGridKeys.GLOBAL_CACHE))
        //                        .toList());

        //        igniteGrid.getIgnite().destroyCaches(
        //                igniteGrid
        //                        .getIgniteApi()
        //                        .tables()
        //                        .tables()
        //                        .stream()
        //                        .map(tbl -> tbl.name())
        //                        .filter(nm -> StringUtils.equalsAny(
        //                                IgniteGridUtil.cacheExternalName(nm),
        //                                IgniteGridKeys.RUN_ONCE_CACHE,
        //                                IgniteGridKeys.GLOBAL_CACHE))
        //                        .toList());
        //        igniteGrid.storage().getCache(
        //                IgniteGridKeys.RUN_ONCE_CACHE, String.class).clear();
        //        igniteGrid.storage().getGlobalCache().clear();
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
                igniteGrid.getIgniteApi(),
                storeName,
                objectType);
    }

    private void cacheStoreSpecs(
            String storeName,
            Class<?> objectType,
            Class<?> storeType) {
        storeSpecs().put(storeName, new StoreSpecs(objectType, storeType));
    }

    private synchronized IgniteGridCache<StoreSpecs> storeSpecs() {
        if (cacheObjectAndStoreTypes == null) {
            cacheObjectAndStoreTypes = new IgniteGridCache<>(
                    igniteGrid.getIgniteApi(),
                    STORE_TYPES_KEY,
                    StoreSpecs.class);
        }
        return cacheObjectAndStoreTypes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StoreSpecs implements Serializable {
        private static final long serialVersionUID = 1L;
        private Class<?> objectType;
        private Class<?> storeType;
    }
}
