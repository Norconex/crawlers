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

import java.nio.file.Path;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Getter;
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
    public Grid connect(Path workDir) {
        try {
            LOG.info("Connecting to grid: \"{}\"", configuration.getGridName());
            return new CoreGrid(configuration, storage);
        } catch (Exception e) {
            //TODO since a lib now, make checked exception?
            throw new GridException("Could not connect to grid.", e);
        }
    }

    @Override
    public void requestStop(Path workDir) {
        try (var grid = connect(workDir)) {
            grid.stop();
        }
    }
}
