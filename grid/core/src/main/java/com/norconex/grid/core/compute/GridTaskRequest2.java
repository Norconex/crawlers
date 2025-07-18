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
package com.norconex.grid.core.compute;

import com.norconex.grid.core.GridException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Setter
@Getter
@Accessors(chain = true)
public class GridTaskRequest2 {

    private final String id;

    /**
     * Grid task to be executed. Must have a public constructor.
     */
    private final Class<? extends GridTask2> taskClass;

    private ExecutionMode executionMode = ExecutionMode.SINGLE_NODE;

    /**
     * Whether the task is meant to be run only once per grid session
     * (whether as a single or multiple nodes task).
     * Trying to run it again will throw a {@link GridException}.
     */
    private boolean once;
}
