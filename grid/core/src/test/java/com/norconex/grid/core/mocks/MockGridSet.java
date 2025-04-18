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
package com.norconex.grid.core.mocks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import com.norconex.grid.core.storage.GridSet;

import lombok.Getter;
import lombok.NonNull;

public class MockGridSet implements GridSet {

    private final Set<String> set =
            Collections.synchronizedSet(new HashSet<>());
    @Getter
    private final Class<String> type = String.class;
    @Getter
    private String name;

    public MockGridSet(@NonNull String name) {
        this.name = name;
    }

    @Override
    public boolean contains(String id) {
        return set.contains(id);
    }

    @Override
    public long size() {
        return set.size();
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public synchronized boolean add(String id) {
        return set.add(id);
    }

    @Override
    public boolean forEach(Predicate<String> predicate) {
        for (var value : set) {
            if (!predicate.test(value)) {
                return false;
            }
        }
        return true;
    }
}
