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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.mvstore.MVStore;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute_DELETE.GridCompute;
import com.norconex.grid.core.impl_DELETE.compute_DELETE.ComputeStateStore;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.core.util.ExecutorManager;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

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

    // private final MVStore mvstore;
    private final LocalGridStorage gridStorage;
    private final LocalGridCompute gridCompute;
    private final LocalGridPipeline gridPipeline;

    private final LocalGridStopHandler stopHandler;

    @Getter
    @Accessors(fluent = true)
    private final ComputeStateStore computeStateStorage;
    @Getter(value = AccessLevel.PACKAGE)
    private final Path storagePath;
    @Getter
    private ExecutorManager nodeExecutors;
    @Getter
    private final String gridName;
    @Getter
    private final String nodeName;
    // there should ever be only one node per JVM with this local grid,
    // but in case this is attempted, we help a bit by giving a unique node
    // name.
    private static final AtomicInteger NODE_COUNT = new AtomicInteger();

    public LocalGrid(MVStore mvstore, String gridName) {
        //        this.mvstore = mvstore;
        this.gridName = gridName;
        nodeName = "local-node-" + NODE_COUNT.getAndIncrement();
        gridStorage = new LocalGridStorage(mvstore);
        gridCompute = new LocalGridCompute(this);
        gridPipeline = new LocalGridPipeline(this);
        computeStateStorage = new ComputeStateStore(this);
        storagePath = Path.of(mvstore.getFileStore().getFileName()).getParent();
        stopHandler = new LocalGridStopHandler(this);
        stopHandler.listenForStopRequest();
        nodeExecutors = new ExecutorManager("local-node");
    }

    @Override
    public GridStorage getStorage() {
        return gridStorage;
    }

    @Override
    public void close() {
        stopHandler.stopListening();
        nodeExecutors.shutdown();
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
    public GridCompute getCompute() {
        return gridCompute;
    }

    @Override
    public GridPipeline pipeline() {
        return gridPipeline;
    }

    @Override
    public boolean resetSession() {
        getStorage().getSessionAttributes().clear();
        return computeStateStorage.reset();
    }

    @Override
    public void stop() {
        stopHandler.stopListening();
        pipeline().stop(null);
        getCompute().stop(null);
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
