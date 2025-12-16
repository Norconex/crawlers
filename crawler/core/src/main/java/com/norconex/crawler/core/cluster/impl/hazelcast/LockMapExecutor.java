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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.ClusterException;

import lombok.extern.slf4j.Slf4j;

//TODO make reusable, probably best to store in hazelcast user context
@Slf4j
public class LockMapExecutor {

    private final IMap<String, Long> lockMap;
    private final Duration expiry;

    public LockMapExecutor(
            HazelcastInstance hz, String mapName, Duration expiry) {
        lockMap = hz.getMap(mapName);
        this.expiry = expiry;
    }

    public <T> T executeWithLock(String key, Callable<T> action) {
        return doExecuteWithLock(key, action, false);
    }

    public void executeWithLock(String key, Runnable action) {
        doExecuteWithLock(key, () -> {
            action.run();
            return null;
        }, false);
    }

    public void executeWithLockOrSkip(String key, Runnable action) {
        doExecuteWithLock(key, () -> {
            action.run();
            return null;
        }, true);
    }

    private <T> T doExecuteWithLock(
            String key, Callable<T> action, boolean skipIfAlreadyLocked) {
        var lockKey = key;
        var now = Instant.now().toEpochMilli();

        // Remove expired lock if present
        var ts = lockMap.get(lockKey);
        if (ts != null && now - ts > expiry.toMillis()) {
            lockMap.remove(lockKey, ts);
        }

        var acquired = lockMap.putIfAbsent(lockKey, now) == null;
        if (!acquired) {
            if (skipIfAlreadyLocked) {
                LOG.info("Lock already held: {}", lockKey);
                return null;
            }
            throw new IllegalStateException("Lock already held: " + lockKey);
        }
        try {
            return action.call();
        } catch (Exception e) {
            throw new ClusterException("Could not execute with lock.", e);
        } finally {
            lockMap.remove(lockKey);
        }
    }
}