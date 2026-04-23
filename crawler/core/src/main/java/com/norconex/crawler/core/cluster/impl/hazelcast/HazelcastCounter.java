/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.Counter;

/**
 * A distributed counter implementation using Hazelcast IMap.
 * Uses atomic operations to ensure thread-safety across the cluster.
 */
//TODO Expose and use it or DELETE it if not used.
class HazelcastCounter implements Counter {

    private final IMap<String, Long> counterMap;
    private final String key;

    public HazelcastCounter(IMap<String, Long> counterMap, String key) {
        this.counterMap = counterMap;
        this.key = key;
        // Initialize if not present
        counterMap.putIfAbsent(key, 0L);
    }

    @Override
    public long incrementAndGet() {
        return addAndGet(1);
    }

    @Override
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public long addAndGet(long delta) {
        return counterMap.executeOnKey(key, entry -> {
            var current = entry.getValue();
            if (current == null) {
                current = 0L;
            }
            var newValue = current + delta;
            entry.setValue(newValue);
            return newValue;
        });
    }

    @Override
    public long getAndAdd(long delta) {
        return counterMap.executeOnKey(key, entry -> {
            var current = entry.getValue();
            if (current == null) {
                current = 0L;
            }
            entry.setValue(current + delta);
            return current;
        });
    }

    @Override
    public long decrementAndGet() {
        return addAndGet(-1);
    }

    @Override
    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public void set(long value) {
        counterMap.put(key, value);
    }

    @Override
    public long get() {
        var value = counterMap.get(key);
        return value != null ? value : 0L;
    }

    @Override
    public void reset() {
        counterMap.put(key, 0L);
    }
}
