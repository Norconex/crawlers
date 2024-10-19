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

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.configuration.CollectionConfiguration;

import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.grid.GridStore;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.StandardException;

public class IgniteGridSet<T> implements GridSet<T> {

    private String name;
    private final IgniteSet<T> set;

    @Getter
    private final Class<? extends T> type;

    @NonNull
    IgniteGridSet(Ignite ignite, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        set = ignite.set(
                name + IgniteGridStorage.Suffix.SET,
                new CollectionConfiguration());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clear() {
        set.clear();
    }

    /**
     * Loops through all elements of this set or until the predicate returns
     * <code>false</code>.
     * @param predicate the predicate applied to each items
     * @return <code>true</code> if the predicate returned <code>true</code>
     *   for all objects.
     */
    @Override
    public boolean forEach(Predicate<T> predicate) {
        try {
            set.forEach(obj -> {
                if (!predicate.test(obj)) {
                    throw new BreakException();
                }
            });
        } catch (BreakException e) {
            return false;
        }
        return true;
    }

    /**
     * Key is always <code>null</code>. This method exists to conforms to
     * {@link GridStore}. Use {@link #forEach(Predicate)} for a more
     * appropriate method.
     */
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try {
            set.forEach(obj -> {
                if (!predicate.test(null, obj)) {
                    throw new BreakException();
                }
            });
        } catch (BreakException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object key) {
        return set.contains(key);
    }

    @Override
    public long size() {
        return set.size();
    }

    @Override
    public boolean add(T object) {
        return set.add(object);
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
