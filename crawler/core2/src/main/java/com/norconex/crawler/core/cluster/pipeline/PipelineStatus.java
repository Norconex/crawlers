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
package com.norconex.crawler.core.cluster.pipeline;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoEnumValue;

@Proto
public enum PipelineStatus {
    @ProtoEnumValue(0)
    PENDING,
    @ProtoEnumValue(1)
    RUNNING,
    @ProtoEnumValue(2)
    COMPLETED,
    @ProtoEnumValue(3)
    FAILED,
    @ProtoEnumValue(4)
    STOPPING,
    @ProtoEnumValue(5)
    STOPPED,
    /**
     * Typically set by the coordinator on when a worker fails to give signs
     * of life.
     */
    @ProtoEnumValue(6)
    EXPIRED;

    public boolean isTerminal() {
        return this == STOPPED
                || this == COMPLETED
                || this == FAILED
                || this == EXPIRED;
    }
}