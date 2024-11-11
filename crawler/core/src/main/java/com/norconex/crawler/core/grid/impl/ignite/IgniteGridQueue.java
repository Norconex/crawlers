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

import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.function.BiPredicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.configuration.CollectionConfiguration;

import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridQueue;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.StandardException;

public class IgniteGridQueue<T> implements GridQueue<T> {

    private String name;
    // we use a set to ensure uniqueness on key.
    private final IgniteSet<String> idSet;
    private final IgniteQueue<QueueEntry<T>> queue;

    @Getter
    private final Class<? extends T> type;

    @NonNull
    IgniteGridQueue(Ignite ignite, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        queue = ignite.queue(name + IgniteGridStorage.Suffix.QUEUE,
                0, // Unbounded queue capacity.
                new CollectionConfiguration());
        idSet = ignite.set(
                name + IgniteGridStorage.Suffix.SET,
                new CollectionConfiguration());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clear() {
        idSet.clear();
        queue.clear();
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try {
            queue.forEach(en -> {
                if (!predicate.test(en.getKey(), en.getObject())) {
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
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object key) {
        return idSet.contains((String) key);
    }

    @Override
    public long size() {
        return queue.size();
    }

    @Override
    public boolean put(String key, T object) {
        var added = idSet.add(key);
        if (added) {
            var entry = new QueueEntry<T>();
            entry.setKey(key);
            entry.setObject(object);
            try {
                if (!queue.offer(entry)) {
                    // should not happen unless Ignire queue configuration was
                    // explicitly set with a capacity
                    throw new GridException(
                            "Queue '%s' has reached maximum capacity."
                                    .formatted(name));
                }
            } catch (IgniteException e) {
                idSet.remove(key);
            }
        }
        return added;
    }

    @Override
    public Optional<T> poll() {
        var queueEntry = queue.poll();
        if (queueEntry != null) {
            idSet.remove(queueEntry.key);
        }
        return ofNullable(queueEntry).map(QueueEntry::getObject);
    }

    @Data
    static class QueueEntry<T> {
        private String key;
        private T object;
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
