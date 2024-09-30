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

import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.store.DataStoreException;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.StandardException;

public class IgniteGridQueue<T> implements GridQueue<T> {

    private String name;
    // we use a set to ensure uniqueness on ID.
    private final IgniteSet<String> idSet;
    private final IgniteQueue<QueueEntry<T>> queue;
    private final Ignite ignite;

    // is type needed?
    private final Class<? extends T> type;

    @NonNull
    IgniteGridQueue(Ignite ignite, String name, Class<? extends T> type) {
        this.ignite = ignite;
        this.type = type;
        this.name = name;
        queue = ignite.queue(name + IgniteGridSystem.Suffix.QUEUE,
                0, // Unbounded queue capacity.
                new CollectionConfiguration());
        idSet = ignite.set(
                name + IgniteGridSystem.Suffix.SET,
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
    public void close() {
        // we don't explicitly close them here. They'll be "closed"
        // automatically when leaving the cluster.
        //        if (ignite.cluster().state().active()
        //                && ignite.cluster().localNode().isClient()) {
        //            idSet.close();
        //            queue.close();
        //        }
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try {
            queue.forEach(en -> {
                if (!predicate.test(en.getId(), en.getObject())) {
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
        return idSet.isEmpty();
    }

    @Override
    public boolean contains(String id) {
        return idSet.contains(id);
    }

    @Override
    public long size() {
        return idSet.size();
    }

    @Override
    public boolean put(String id, T object) {
        var added = idSet.add(id);
        if (added) {
            var entry = new QueueEntry<T>();
            entry.setId(id);
            entry.setObject(object);
            try {
                if (!queue.offer(entry)) {
                    // should not happen unless Ignire queue configuration was
                    // explicitly set with a capacity
                    throw new DataStoreException(
                            "Queue '%s' has reached maximum capacity."
                                    .formatted(name));
                }
            } catch (IgniteException e) {
                idSet.remove(id);
            }
        }
        return added;
    }

    @Override
    public Optional<T> poll() {
        return ofNullable(queue.poll()).map(QueueEntry::getObject);
    }

    @Data
    static class QueueEntry<T> {
        private String id;
        private T object;
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
