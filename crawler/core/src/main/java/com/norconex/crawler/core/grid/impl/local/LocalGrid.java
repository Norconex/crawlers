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
package com.norconex.crawler.core.grid.impl.local;

import java.util.UUID;

import org.apache.commons.lang3.ObjectUtils;
import org.h2.mvstore.MVStore;

import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridServices;
import com.norconex.crawler.core.grid.GridStorage;

import lombok.EqualsAndHashCode;
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

    private final MVStore mvstore;
    private final LocalGridCompute gridCompute;
    private final LocalGridStorage gridStorage;
    private final LocalGridServices gridServices;
    private final String nodeId = UUID.randomUUID().toString();

    public LocalGrid(MVStore mvstore) {

        this.mvstore = mvstore;
        gridCompute = new LocalGridCompute(mvstore, nodeId);
        gridStorage = new LocalGridStorage(mvstore);
        gridServices = new LocalGridServices(nodeId);
    }

    @Override
    public GridServices services() {
        ensureInit();
        return gridServices;
    }

    @Override
    public GridCompute compute() {
        ensureInit();
        return gridCompute;
    }

    @Override
    public GridStorage storage() {
        ensureInit();
        return gridStorage;
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    private void ensureInit() {
        if (ObjectUtils.anyNull(gridCompute, gridStorage, gridServices)) {
            throw new IllegalStateException("LocalGrid not initialized.");
        }
    }

    @Override
    public void close() {
        if (!mvstore.isClosed()) {
            mvstore.close();
        }
    }
}
