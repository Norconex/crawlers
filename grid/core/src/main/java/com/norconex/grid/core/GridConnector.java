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
package com.norconex.grid.core;

import java.nio.file.Path;

/**
 * Connector to a supported Grid implementation.
 */
public interface GridConnector {

    Grid connect(Path workDir);

    /**
     * Short-lived method that requests an existing grid to stop.
     * That is, it will only perform the minimum
     * initialization required to send a stop request to the grid
     * and will not participate in existing grid activities before
     * exiting. It does not wait for the grid to stop to return.
     * In some cases, it may not even have invoke the
     * {@link #connect(Path)} method.
     * @param workDir working directory
     */
    void requestStop(Path workDir);
}
