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
package com.norconex.grid.local;

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalTaskCoordinator {

    private static final Map<String, GridTask> activeTasks =
            new ConcurrentHashMap<>();

    private final GridMap<TaskState> taskStateStore;
    private final LocalGrid grid;

    public LocalTaskCoordinator(LocalGrid grid) {
        this.grid = grid;
        taskStateStore =
                grid.getStorage().getMap("task-state", TaskState.class);
    }

    TaskExecutionResult executeTask(GridTask task) {
        // Check if ok to run
        if (activeTasks.containsKey(task.getId())) {
            throw new IllegalStateException(
                    "Task \"%s\" is already running.".formatted(task.getId()));
        }
        if (task.isOnce()) {
            var prevOrCurrentState = ofNullable(
                    taskStateStore.get(task.getId())).orElse(TaskState.PENDING);
            if (prevOrCurrentState.isTerminal()) {
                LOG.warn("""
                    Ignoring request to run task "{}" ONCE as it \
                    already ran in this crawl session with \
                    status: "{}".""",
                        task.getId(), prevOrCurrentState);
                return new TaskExecutionResult(
                        TaskState.FAILED,
                        null,
                        "Task already ran with state: " + prevOrCurrentState);
            }
        }

        try {

            activeTasks.put(task.getId(), task);
            taskStateStore.put(task.getId(), TaskState.RUNNING);
            var result = task.execute(grid);
            var status =
                    new TaskExecutionResult(TaskState.COMPLETED, result, null);
            var aggStatus = task.aggregate(List.of(status));

            //NOTE: it is possible that the executed task was about destroying
            // the storage. If so, we have to consider it may be gone and check
            // for that here before storing the result state. If we did not
            // destroy storage on purpose, it will fail down the road when used
            // again and will be reported properly then.
            if (grid.getStorage().storeExists(taskStateStore.getName())) {
                taskStateStore.put(task.getId(), TaskState.COMPLETED);
            } else {
                LOG.info("Storage used to track task state no longer exists.");
            }
            return aggStatus;
        } catch (Exception e) {
            LOG.error("task {} failed.", task.getId(), e);
            var status = new TaskExecutionResult(
                    TaskState.FAILED,
                    null,
                    ExceptionUtil.getFormattedMessages(e));
            taskStateStore.put(task.getId(), TaskState.FAILED);
            return status;
        } finally {
            activeTasks.remove(task.getId());
        }
    }

    void stopTask(String taskId) {
        for (Entry<String, GridTask> entry : activeTasks.entrySet()) {
            if ((taskId == null || entry.getKey().equals(taskId))) {
                entry.getValue().stop();
            }
        }
    }
}
