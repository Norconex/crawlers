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

import com.norconex.grid.core.Grid;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.With;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class Stage {

    /**
     * Provides a task meant to run on one or more server nodes (when in a
     * clustered environment).
     * Returning {@code null} skips this stage execution.
     * Unless explicitly required by design, it is recommended to return
     * new task instances to ensure thread-safety
     */
    @NonNull
    @ToString.Exclude
    private final StageTaskProvider taskProvider;

    /**
     * Always attempt to run (provided the task is not {@code null}) even
     * when resuming a stopped pipeline or joining an already executing
     * pipeline, and this stage was executed already.
     */
    @With
    private boolean always;

    /**
     * Creates a stage that will use the supplied task. Same as invoking
     * {@link #Stage(StageTaskProvider)} with a provider returning this task.
     * @param task the task to execute
     */
    public Stage(GridTask task) {
        this((g, v) -> task);
    }

    // Copy constructor for defensive use
    Stage(@NonNull Stage stage) {
        this(stage.taskProvider, stage.always);
    }

    @FunctionalInterface
    public interface StageTaskProvider {
        /**
         * Provides a task meant to run on one or more server nodes (when in a
         * clustered environment).
         * Returning a {@code null} task will skip the stage execution.
         * That previous task output survives crawl resumes (if it can
         * be serialized by the grid storage implementation).
         * @param grid the grid this task will be run on
         * @param previousResult the output value of the previous stage task
         * @return the task to execute or {@code null}
         */
        GridTask get(Grid grid, TaskExecutionResult previousResult);
    }
}
