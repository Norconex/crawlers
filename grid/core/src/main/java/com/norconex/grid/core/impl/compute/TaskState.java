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
package com.norconex.grid.core.impl.compute;

import java.util.stream.Stream;

// Task status enum
public enum TaskState {
    PENDING, RUNNING, COMPLETED, FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isAny(TaskState... states) {
        if (states == null) {
            return false;
        }
        return Stream.of(states).anyMatch(st -> this == st);
    }

    /**
     * Defaults to PENDING (never null).
     * @param state state as a string
     * @return state
     */
    public static TaskState of(String state) {
        return Stream.of(values())
                .filter(val -> val.name().equalsIgnoreCase(state))
                .findAny()
                .orElse(TaskState.PENDING);
    }
}
