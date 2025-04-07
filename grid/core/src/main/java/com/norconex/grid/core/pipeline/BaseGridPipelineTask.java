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
package com.norconex.grid.core.pipeline;

import lombok.Getter;

/**
 * Implements the stop method to set a "stopRequested" flag obtained via
 * {@link #isStopRequested()}.
 *
 * Alternatively, once can override {@link #stop()} to react instantly
 * to the stop request.
 *
 * @param <T> context object
 */
public abstract class BaseGridPipelineTask<T> implements GridPipelineTask<T> {

    @Getter
    private boolean stopRequested;

    @Override
    public void stop() {
        stopRequested = true;
    }
}
