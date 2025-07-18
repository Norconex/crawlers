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
package com.norconex.grid.core.impl.compute.task;

import com.norconex.grid.core.compute.GridTaskRequest2;
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Getter;

@Getter
public class TaskStoreManager2 {

    private final GridMap<GridTaskRequest2> taskRequests;
    private final GridMap<TaskExecutionResult> terminatedTasks;
    private final GridMap<TaskProgress> workersTaskProgress;

    public TaskStoreManager2(GridStorage storage) {
        taskRequests = storage.getMap("taskRequests", GridTaskRequest2.class);
        terminatedTasks =
                storage.getMap("terminatedTasks", TaskExecutionResult.class);
        workersTaskProgress =
                storage.getMap("workersTaskProgress", TaskProgress.class);
    }
}
