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
package com.norconex.grid.local.storage;

import java.util.Objects;
import java.util.function.Predicate;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.grid.core.storage.GridSet;

import lombok.Getter;
import lombok.NonNull;

public class LocalSet implements GridSet {
    private final MVMap<String, Boolean> map;
    @Getter
    private final Class<String> type = String.class;
    @Getter
    private String name;

    public LocalSet(
            @NonNull MVStore mvstore,
            @NonNull String name) {
        this.name = name;
        map = mvstore.openMap(name);
    }

    @Override
    public boolean contains(String id) {
        return map.containsKey(id);
    }

    @Override
    public long size() {
        return map.sizeAsLong();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public synchronized boolean add(String id) {
        return !Objects.equals(Boolean.TRUE, map.put(id, Boolean.TRUE));
    }

    @Override
    public boolean forEach(Predicate<String> predicate) {
        for (String key : map.keySet()) {
            if (!predicate.test(key)) {
                return false;
            }
        }
        return true;
    }

}
