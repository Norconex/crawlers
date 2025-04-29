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
package com.norconex.grid.core;

import java.nio.file.Path;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class BaseGridContext implements GridContext {

    private final Path workDir;

    private Grid grid;

    /**
     * {@inheritDoc}
     * Sets the grid on this grid context and invoke {@link #doInit()}.
     */
    @Override
    public final void init(Grid grid) {
        if (this.grid != null) {
            throw new IllegalArgumentException(
                    "Grid context already initialized.");
        }
        this.grid = grid;
        doInit();
    }

    /**
     * Invoked after the grid has been set, which can be obtained
     * via {@link #getGrid()}. Default implementation does nothing.
     */
    protected void doInit() {
        // NOOP
    }
}
