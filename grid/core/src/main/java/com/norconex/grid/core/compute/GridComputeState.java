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

import java.util.stream.Stream;

//TODO rename ComputeState and use in different context? (job, pipeline stage, etc),

public enum GridComputeState {
    IDLE, RUNNING, /*EXPIRED,*/ FAILED, COMPLETED;

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean hasRan() {
        return this == GridComputeState.COMPLETED
                || this == GridComputeState.FAILED;
    }

    public boolean isAny(GridComputeState... states) {
        if (states == null) {
            return false;
        }
        return Stream.of(states).anyMatch(st -> this == st);
    }

    /**
     * Defaults to IDLE (never null).
     * @param state state as a string
     * @return state
     */
    public static GridComputeState of(String state) {
        return Stream.of(values())
                .filter(val -> val.name().equalsIgnoreCase(state))
                .findAny()
                .orElse(GridComputeState.IDLE);
    }
}
