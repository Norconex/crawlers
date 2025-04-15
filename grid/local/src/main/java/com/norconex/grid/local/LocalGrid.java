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

import java.nio.file.Path;

import org.h2.mvstore.MVStore;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.impl.compute.ComputeStateStore;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;

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

    private final MVStore mvstore;
    private final LocalGridStorage gridStorage;
    private final LocalGridCompute gridCompute;
    private final LocalGridPipeline gridPipeline;
    @Getter
    @Accessors(fluent = true)
    private final ComputeStateStore computeStateStorage;
    @Getter(value = AccessLevel.PACKAGE)
    private final Path storagePath;
    private final LocalGridStopHandler stopHandler;

    public LocalGrid(MVStore mvstore) {
        this.mvstore = mvstore;
        gridStorage = new LocalGridStorage(mvstore);
        gridCompute = new LocalGridCompute(this);
        gridPipeline = new LocalGridPipeline(this);
        computeStateStorage = new ComputeStateStore(this);
        storagePath = Path.of(mvstore.getFileStore().getFileName()).getParent();
        stopHandler = new LocalGridStopHandler(this);
        stopHandler.listenForStopRequest();
    }

    @Override
    public GridStorage storage() {
        return gridStorage;
    }

    @Override
    public String getNodeName() {
        return "local-node";
    }

    @Override
    public void close() {
        stopHandler.stopListening();
        if (!isClosed()) {
            mvstore.close();
        }
    }

    boolean isClosed() {
        return mvstore.isClosed();
    }

    @Override
    public GridCompute compute() {
        return gridCompute;
    }

    @Override
    public GridPipeline pipeline() {
        return gridPipeline;
    }

    @Override
    public String getGridName() {
        return "local-grid";
    }

    @Override
    public boolean resetSession() {
        storage().getSessionAttributes().clear();
        return computeStateStorage.reset();
    }

    @Override
    public void stop() {
        stopHandler.stopListening();
        pipeline().stop(null);
        compute().stop(null);
    }
}
