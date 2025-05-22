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

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskState state;
    private Serializable result;
    private String error;

    /**
     * Create a task status with a pending state and no result and no error.
     */
    public TaskExecutionResult() {
        this(TaskState.PENDING, null, null);
    }

    public TaskExecutionResult(TaskState state, Serializable result, String error) {
        this.state = state;
        this.result = result;
        this.error = error;
    }

    public TaskState getState() {
        return state;
    }

    public Serializable getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
