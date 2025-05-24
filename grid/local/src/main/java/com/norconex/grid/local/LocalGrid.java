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
package com.norconex.grid.local;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVStore;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridContext;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.local.storage.LocalStorage;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * A "local" grid implementation, using the host resources only and using an
 * embedded key-value store database
 * (<a href="https://www.h2database.com/html/mvstore.html">MVStore</a>).
 * This is the default grid implementation and the one recommended when you
 * do not need to run the crawler in a clustered environment.
 */
@EqualsAndHashCode
@ToString
public class LocalGrid implements Grid {

    //TODO create an abstract BaseGrid and move at least the context map?

    /**
     * Key used to store a default context. This key is used to register
     * a context under a {@code null} or blank key, or when specifying a
     * {@code null} context key in a submitted grid task.
     */
    public static final String DEFAULT_CONTEXT_KEY = "default";

    private final LocalStorage gridStorage;
    private final LocalCompute gridCompute;
    private final LocalStopHandler stopHandler;

    @Getter(value = AccessLevel.PACKAGE)
    private final Path storagePath;
    @Getter
    private final String gridName;
    @Getter
    private final String nodeName;
    // there should ever be only one node per JVM with this local grid,
    // but in case this is attempted, we help a bit by giving a unique node
    // name.
    private static final AtomicInteger NODE_COUNT = new AtomicInteger();

    private final Map<String, Object> localContexts = new ConcurrentHashMap<>();

    public LocalGrid(
            MVStore mvstore, String gridName, GridContext gridContext) {
        this.gridName = gridName;
        nodeName = "local-node-" + NODE_COUNT.getAndIncrement();
        gridStorage = new LocalStorage(mvstore);
        gridCompute = new LocalCompute(this);
        storagePath = Path.of(mvstore.getFileStore().getFileName()).getParent();
        stopHandler = new LocalStopHandler(this);
        stopHandler.listenForStopRequest();
    }

    @Override
    public GridStorage getStorage() {
        return gridStorage;
    }

    @Override
    public void close() {
        stopHandler.stopListening();
        //        nodeExecutors.shutdown();
        if (!isClosed()) {
            try {
                getStorage().close();
            } catch (IOException e) {
                throw new GridException("Cannot close local database.", e);
            }
        }
    }

    boolean isClosed() {
        return gridStorage.isClosed();
    }

    @Override
    public LocalCompute getCompute() {
        return gridCompute;
    }

    @Override
    public void resetSession() {
        getStorage().getSessionAttributes().clear();
    }

    @Override
    public void stop() {
        gridCompute.stopTask(null);
        stopHandler.stopListening();
    }

    @Override
    public void registerContext(String contextKey, Object context) {
        localContexts.put(StringUtils.isBlank(contextKey)
                ? DEFAULT_CONTEXT_KEY
                : contextKey,
                context);
    }

    @Override
    public Object getContext(String contextKey) {
        return localContexts.get(
                StringUtils.isBlank(contextKey) ? DEFAULT_CONTEXT_KEY
                        : contextKey);
    }

    @Override
    public Object unregisterContext(String contextKey) {
        return localContexts.remove(contextKey);
    }

    /**
     * <b>Not applicable to local grid.</b>
     */
    @Override
    public CompletableFuture<Void> awaitMinimumNodes(
            int count, Duration timeout) {
        return CompletableFuture.completedFuture(null);
    }
}
