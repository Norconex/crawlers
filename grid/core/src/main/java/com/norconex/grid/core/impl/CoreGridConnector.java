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
package com.norconex.grid.core.impl;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridContext;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class CoreGridConnector
        implements GridConnector, Configurable<CoreGridConnectorConfig> {

    @Getter
    private final CoreGridConnectorConfig configuration =
            new CoreGridConnectorConfig();

    private final GridStorage storage;

    @Override
    public Grid connect(@NonNull GridContext gridContext) {
        try {
            LOG.info("🔗 Connecting to grid: \"{}\"",
                    configuration.getGridName());
            var grid = new CoreGrid(configuration, storage, gridContext);
            gridContext.init(grid);
            return grid;
        } catch (Exception e) {
            //TODO make checked exception?
            throw new GridException("Could not connect to grid.", e);
        }
    }

    @Override
    public void shutdownGrid(@NonNull GridContext gridContext) {
        try (var grid = connect(gridContext)) {
            grid.stop();
        }
    }
}
