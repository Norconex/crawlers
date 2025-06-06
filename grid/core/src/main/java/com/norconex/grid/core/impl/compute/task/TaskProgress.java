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

import java.io.Serializable;

import com.norconex.grid.core.compute.TaskExecutionResult;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    private TaskExecutionResult status;
    private long lastHeartbeat;

    public TaskProgress() {
    }

    public TaskProgress(TaskExecutionResult status, long lastHeartbeat) {
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
    }

    public TaskExecutionResult getStatus() {
        return status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
}
