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
package com.norconex.crawler.core.mocks.grid;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Data;

@Data
public class MockFailingGrid implements Grid {

    private final String nodeId = UUID.randomUUID().toString();

    @Override
    public GridCompute getCompute() {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public GridStorage getStorage() {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public String getNodeName() {
        return nodeId;
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public void stop() {
        // NOOP
    }

    @Override
    public String getGridName() {
        return "some-grid";
    }

    @Override
    public void resetSession() {
        // NOOP
    }

    @Override
    public CompletableFuture<Void> awaitMinimumNodes(
            int count, Duration timeout) {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public void registerContext(String contextKey, Object context) {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public Object getContext(String contextKey) {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public Object unregisterContext(String contextKey) {
        throw new UnsupportedOperationException("IN_TEST");
    }
}
