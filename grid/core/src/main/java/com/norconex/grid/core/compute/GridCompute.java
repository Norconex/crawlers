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
package com.norconex.grid.core.compute;

import com.norconex.grid.core.impl.compute.TaskStatus;

import lombok.NonNull;

/**
 * Grid compute methods. All methods run synchronously. All nodes on a grid
 * wait for a task to complete before returning whether it runs on
 * one or all nodes. To have it behave differently, have your task run in a
 * thread and return right away.
 */
public interface GridCompute {

    /**
     * Execute a task on the grid.
     * @param task the task to execute
     * @return execution status (never <code>null</code>)
     */
    TaskStatus executeTask(@NonNull GridTask task);

    void executePipeline(@NonNull GridPipeline pipeline);

    void stopTask(String taskId);
}
