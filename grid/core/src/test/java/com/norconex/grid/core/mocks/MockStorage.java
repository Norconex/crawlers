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

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.core.storage.GridStore;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class MockStorage implements GridStorage {

    // Static to mock a shared DB when testing multiple nodes via threads
    // on same VM
    private static final Map<String, GridStore<?>> GRID_STORES =
            new ConcurrentHashMap<>();
    private final ReentrantLock transactionLock = new ReentrantLock();

    @Override
    public Set<String> getStoreNames() {
        return GRID_STORES.keySet();
    }

    @Override
    public GridSet getSet(String name) {
        return (GridSet) GRID_STORES.computeIfAbsent(name, MockGridSet::new);
    }

    @Override
    public <T> GridQueue<T> getQueue(String name,
            Class<? extends T> type) {
        return (GridQueue<T>) GRID_STORES.computeIfAbsent(name,
                nm -> new MockGridQueue<T>(nm, type));
    }

    @Override
    public <T> GridMap<T> getMap(String name, Class<? extends T> type) {
        return (GridMap<T>) GRID_STORES.computeIfAbsent(name,
                nm -> new MockGridMap<T>(nm, type));
    }

    @Override
    public GridMap<String> getSessionAttributes() {
        return getMap("mock_session_map", String.class);
    }

    @Override
    public GridMap<String> getDurableAttributes() {
        return getMap("mock_durable_map", String.class);
    }

    @Override
    public void forEachStore(Consumer<GridStore<?>> storeConsumer) {
        GRID_STORES.values().forEach(storeConsumer::accept);
    }

    @Override
    public void destroy() {
        GRID_STORES.clear();
    }

    @Override
    public boolean storeExists(String name) {
        return GRID_STORES.containsKey(name);
    }

    @Override
    public synchronized <T> T runInTransaction(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new GridException(e);
        }
    }

    @Override
    public <T> Future<T> runInTransactionAsync(Callable<T> callable) {
        var future = new CompletableFuture<T>();
        CompletableFuture.runAsync(() -> {
            // Ensure only one transaction modifies the map at a time
            transactionLock.lock();
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
                throw new GridException(e);
            } finally {
                transactionLock.unlock();
            }
        });
        return future;
    }

    @Override
    public void close() throws IOException {
        //NOOP
    }
}
