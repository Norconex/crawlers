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
package com.norconex.grid.core.impl.compute.coord;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.impl.CoreGrid;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class GridTaskCoordinator implements Runnable {

    private final CoreGrid grid;
    private final String taskName;

    public GridTaskCoordinator(CoreGrid grid, String taskName) {
        this.grid = grid;
        this.taskName = taskName;
    }

    @Override
    public void run() {
        TaskResponseReducer.map(grid, taskName, reducer -> {
            grid.computeStateStorage().setComputeStateAtTime(
                    taskName, GridComputeState.RUNNING);

            GridComputeResult<?> result = null;
            while ((result = reducer.reduce()).getState().isRunning()) {
                Sleeper.sleepMillis(250);
            }

            grid.computeStateStorage().setComputeStateAtTime(
                    taskName, result.getState());

            grid.taskPayloadMessenger().send(taskName, result);
        });
    }

}
