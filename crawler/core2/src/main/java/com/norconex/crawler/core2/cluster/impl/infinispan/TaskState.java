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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoEnumValue;

/**
 * Enum representing the possible states of a distributed task.
 */
@Proto
public enum TaskState {
    @ProtoEnumValue(0)
    NOT_STARTED,
    @ProtoEnumValue(1)
    RUNNING,
    @ProtoEnumValue(2)
    COMPLETED,
    @ProtoEnumValue(3)
    FAILED,
    @ProtoEnumValue(4)
    STOP_REQUESTED,
    @ProtoEnumValue(5)
    STOPPED;
}
