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

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridStorage;

import lombok.extern.slf4j.Slf4j;

/**
 * Grid-wide state persistence utility.
 */
@Slf4j
public class ComputeStateStore {

    private static final String COMPUTE_STATES_KEY =
            ComputeStateStore.class.getName();

    private final GridMap<ComputeStateAtTime> computeStates;
    private final GridStorage storage;

    public ComputeStateStore(Grid grid) {
        storage = grid.storage();
        computeStates =
                storage.getMap(COMPUTE_STATES_KEY, ComputeStateAtTime.class);
    }

    public Optional<GridComputeState> getComputeState(String taskName) {
        return ifStateStoreExists(
                () -> Optional.ofNullable(computeStates.get(taskName))
                        .map(ComputeStateAtTime::getState));
    }

    public Optional<ComputeStateAtTime> getComputeStateAtTime(
            String taskName) {
        return ifStateStoreExists(
                () -> Optional.ofNullable(computeStates.get(taskName)));
    }

    public void setComputeStateAtTime(String taskName, GridComputeState state) {
        ifStateStoreExists(() -> {
            computeStates.put(
                    taskName,
                    new ComputeStateAtTime(state, System.currentTimeMillis()));
            return null;
        });

    }

    public Map<String, ComputeStateAtTime> getRunningTasks() {
        Map<String, ComputeStateAtTime> tasks = new ListOrderedMap<>();
        return ifStateStoreExists(() -> {
            computeStates.forEach((name, task) -> {
                if (task.getState().isRunning()) {
                    tasks.put(name, task);
                }
                return true;
            });
            return tasks;
        }, () -> tasks);
    }

    public boolean reset() {
        return ifStateStoreExists(() -> {
            var hasStates = !computeStates.isEmpty();
            computeStates.clear();
            return hasStates;
        }, () -> false);
    }

    // This wrapper method is necessary in case we are destroying the stores
    // as part of a task and we want to set/get the task state.
    private <T> T ifStateStoreExists(Supplier<T> s) {
        return ifStateStoreExists(s, null);
    }

    private <T> T ifStateStoreExists(Supplier<T> supply, Supplier<T> orElse) {
        if (storage.storeExists(COMPUTE_STATES_KEY)) {
            return supply.get();
        }
        LOG.info("State store no longer exists.");
        return orElse != null ? orElse.get() : null;
    }
}
