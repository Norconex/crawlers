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
package com.norconex.grid.core.impl.compute.worker;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.jgroups.NullAddress;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.messages.GridJobDoneMessage;
import com.norconex.grid.core.impl.compute.messages.JobStateMessageAck;
import com.norconex.grid.core.junit.WithLogLevel;
import com.norconex.grid.core.mocks.MockStorage;

@WithLogLevel(value = "TRACE", classes = NodeJobMessageReceiver.class)
class NodeJobMessageReceiverTest {

    @Test
    void testOnMessage() throws Exception {

        var grid = new CoreGrid("someNode", "someCluster", new MockStorage());
        var worker = new NodeJobWorker(grid, "someJob", false);
        var receiver = new NodeJobMessageReceiver(worker);

        assertThatNoException().isThrownBy(() -> receiver.onMessage(
                new JobStateMessageAck("someJob"),
                new NullAddress()));

        assertThatNoException().isThrownBy(() -> receiver.onMessage(
                new GridJobDoneMessage("someJob", GridJobState.COMPLETED),
                new NullAddress()));

        assertThatNoException().isThrownBy(() -> {
            receiver.waitForWorkerDoneAckMsg();
            receiver.waitForCoordDoneMsg();
        });
    }
}
