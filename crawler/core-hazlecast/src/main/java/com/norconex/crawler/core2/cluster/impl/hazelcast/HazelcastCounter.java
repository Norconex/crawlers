/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.util.Objects;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicLong;
import com.norconex.crawler.core2.cluster.Counter;

/**
 * Hazelcast implementation of the Counter interface.
 */
public class HazelcastCounter implements Counter {

    private final IAtomicLong counter;
    private final String name;
    
    public HazelcastCounter(HazelcastInstance hazelcastInstance, String name) {
        Objects.requireNonNull(hazelcastInstance, "Hazelcast instance cannot be null");
        this.name = Objects.requireNonNull(name, "Counter name cannot be null");
        this.counter = hazelcastInstance.getCPSubsystem().getAtomicLong("nx-counter-" + name);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public long get() {
        return counter.get();
    }
    
    @Override
    public long incrementAndGet() {
        return counter.incrementAndGet();
    }
    
    @Override
    public long decrementAndGet() {
        return counter.decrementAndGet();
    }
    
    @Override
    public long addAndGet(long delta) {
        return counter.addAndGet(delta);
    }
    
    @Override
    public void set(long value) {
        counter.set(value);
    }
    
    @Override
    public void reset() {
        counter.set(0);
    }
    
    @Override
    public String toString() {
        return "HazelcastCounter [name=" + name + ", value=" + get() + "]";
    }
}