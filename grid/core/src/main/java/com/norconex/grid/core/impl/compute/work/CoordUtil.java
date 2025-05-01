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
package com.norconex.grid.core.impl.compute.work;

import static java.util.Optional.ofNullable;

import org.jgroups.util.Rsp;

import com.norconex.grid.core.compute.TaskState;
import com.norconex.grid.core.compute.TaskStatus;
import com.norconex.grid.core.impl.compute.TaskProgress;

public final class CoordUtil {

    private CoordUtil() {
    }

    static boolean isValidNodeResponse(Rsp<TaskProgress> rsp) {
        return rsp.wasReceived()
                && !rsp.hasException()
                && !rsp.wasSuspected()
                && !rsp.wasUnreachable()
                && rsp.getValue() != null;
    }

    static boolean hasNodeTaskExpired(
            long taskStartTime, TaskProgress progress) {
        var lastHeartbeat = progress != null
                ? progress.getLastHeartbeat()
                : taskStartTime;
        return System.currentTimeMillis()
                - lastHeartbeat > TaskCoordinator.HEARTBEAT_EXPIRY_MS;
    }

    static boolean isState(TaskProgress progress, TaskState state) {
        return ofNullable(progress)
                .map(TaskProgress::getStatus)
                .map(TaskStatus::getState)
                .filter(s -> s == state)
                .isPresent();
    }

}
