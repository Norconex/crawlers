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

import static com.norconex.grid.core.impl.compute.LogTxt.COORD;
import static com.norconex.grid.core.impl.compute.LogTxt.SEND_TO_MANY;
import static com.norconex.grid.core.impl.compute.LogTxt.SEND_TO_ONE;
import static com.norconex.grid.core.impl.compute.LogTxt.WORKER;

import org.jgroups.Address;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.compute.LogTxt;
import com.norconex.grid.core.impl.compute.messages.JobStateMessageAck;
import com.norconex.grid.core.impl.compute.messages.GridJobDoneMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class CoordJobMessageSender {

    private final GridJobCoordinator coord;

    void acknowledgeTo(Address node) {
        if (LOG.isTraceEnabled()) {
            LOG.trace(LogTxt.msg("SEND_ACK", COORD, SEND_TO_ONE, WORKER,
                    node, coord.getJobName()));
        }
        coord.getGrid().sendTo(node,
                new JobStateMessageAck(coord.getJobName()));
    }

    void broadcastAllDone(GridJobState gridJobState) {
        if (LOG.isTraceEnabled()) {
            LOG.trace(LogTxt.msg("SEND_ALLDONE", COORD, SEND_TO_MANY, WORKER,
                    coord.getJobName()));
        }
        coord.getGrid()
                .send(new GridJobDoneMessage(coord.getJobName(), gridJobState));
    }
}
