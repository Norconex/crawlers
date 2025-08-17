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
package com.norconex.crawler.core.cluster.impl.infinispan;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;

import lombok.Data;

/**
 * A record holding information on a pipeline execution current step.
 */
@Data
@Proto
public class PipelineStepRecord {
    @ProtoField(number = 1)
    public String pipelineId;
    @ProtoField(number = 2)
    public String stepId;
    @ProtoField(number = 3)
    public long updatedAt;
    @ProtoField(number = 4)
    public PipelineStatus status;
}
