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

/**
 * Provides required and optional contextual properties for {@link Grid}
 * implementations to initialize properly.
 */
public interface GridContext {
    /**
     * Returns the working directory for resolving relative paths,
     * or {@code null}
     * if unspecified. Implementations should default to a suitable directory
     * (e.g., {@code System.getProperty("user.dir")}) if {@code null}.
     * @return working directory
     */
    Path getWorkDir();
}
