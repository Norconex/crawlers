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
package com.norconex.grid.core.impl.pipeline;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StopPipelineMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String pipelineName;

    public static void onReceive(
            Object payload,
            String pipelineName,
            Consumer<StopPipelineMessage> consumer) {
        if (payload instanceof StopPipelineMessage msg
                && (msg.pipelineName == null ||
                        Objects.equals(pipelineName, msg.getPipelineName()))) {
            consumer.accept(msg);
        }
    }
}
