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

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class GridJobCoordinator implements Runnable {

    private final CoreGrid grid;
    private final String jobName;
    private final boolean runOnce;
    private CoordJobMessageReceiver messageReceiver;
    private CoordJobMessageSender messageSender;

    public GridJobCoordinator(
            CoreGrid grid, String jobName, boolean runOnce) {
        this.grid = grid;
        this.jobName = jobName;
        this.runOnce = runOnce;
        messageReceiver = new CoordJobMessageReceiver(this);
        messageSender = new CoordJobMessageSender(this);
    }

    @Override
    public void run() {
        grid.jobStateStorage().setJobStateAtTime(
                jobName, GridJobState.RUNNING);
        grid.addListener(messageReceiver);
        var coordState = messageReceiver.waitForAllWorkersDone();
        grid.jobStateStorage().setJobStateAtTime(jobName, coordState);
        //TODO request ACK?
        messageSender.broadcastAllDone(coordState);
        grid.removeListener(messageReceiver);
    }
}
