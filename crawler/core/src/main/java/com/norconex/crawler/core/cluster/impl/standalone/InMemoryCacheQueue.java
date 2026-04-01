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
package com.norconex.crawler.core.cluster.impl.standalone;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.norconex.crawler.core.cluster.CacheQueue;

/**
 * In-memory {@link CacheQueue} backed by a {@link ConcurrentLinkedQueue}.
 * Not persistent.
 */
public class InMemoryCacheQueue<T> implements CacheQueue<T> {

    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final String name;

    public InMemoryCacheQueue(String name) {
        this.name = name;
    }

    @Override
    public void add(T item) {
        queue.offer(item);
    }

    @Override
    public T poll() {
        return queue.poll();
    }

    @Override
    public List<T> pollBatch(int batchSize) {
        var batch = new ArrayList<T>(batchSize);
        T item;
        while (batch.size() < batchSize && (item = queue.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }
}
