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
package com.norconex.crawler.core.grid.impl.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServer;

import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridTransactions;
import com.norconex.crawler.core.grid.impl.ignite.storage.IgniteGridStorage;
import com.norconex.crawler.core.grid.storage.GridStorage;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * Uses Apache Ignite to run the crawler on a cluster (grid).
 * </p>
 */
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class IgniteGrid implements Grid {

    @NonNull
    @Getter(value = AccessLevel.PACKAGE)
    private final IgniteServer igniteServer;

    public Ignite api() {
        return igniteServer.api();
    }

    @Override
    public GridStorage storage() {
        return new IgniteGridStorage(this);
    }

    //    @Override
    //    public GridCompute compute() {
    //        return new DefaultGridCompute(this);
    //    }

    //    @Override
    //    public GridServices services() {
    //        return new IgniteGridServices(this);
    //    }

    @Override
    public String nodeId() {
        return igniteServer.name();
    }

    @Override
    public void close() {
        igniteServer.shutdown();
    }

    @Override
    public GridTransactions transactions() {
        return new IgniteGridTransactions(this);
    }
}
