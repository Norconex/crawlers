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
package com.norconex.grid.core.impl.compute.messages;

import static java.util.Optional.ofNullable;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.ObjectUtils;

import com.norconex.grid.core.util.SerialUtil;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Our own message payload abstraction, with key piece of information
 * we want with every message transfer. This class is a JGroups message
 * payload, being a wrapper around the actual payload,
 * </p>
 * <p>
 * On the implementation side, we perform JSON (de)serialization of the
 * envelope payload which eliminates the need for {@link Serializable}
 * payloads in most cases.
 * </p>
 */
@Data
@Accessors(chain = true)
public class TaskPayloadEnvelope implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Set with a unique id when acknowledgement is required. */
    private String ackId;
    private String taskName;
    private Class<?> payloadType;
    private String payload;

    public static TaskPayloadEnvelope of(String taskName, Object payload) {
        return new TaskPayloadEnvelope()
                .setTaskName(taskName)
                .setPayloadType(ofNullable(payload)
                        .map(Object::getClass)
                        .orElse(null))
                .setPayload(ofNullable(payload)
                        .map(pl -> SerialUtil.toJsonString(payload))
                        .orElse(null));
    }

    /**
     * Helper method to receive the deserialized playload matching the given
     * task name. If the envelope does not implement {@link TaskPayloadEnvelope}
     * or does not match the task name, the payload consumer argument
     * is not invoked.
     * @param envelope an object to be considered if an envelope
     * @param taskName the task for which we want to get the payload
     * @param payloadConsumer receives the deserialized payload
     * @return <code>true</code> if the sender requested acknowledgement.
     */
    //TODO needed? Move to ComputeTaskMessenger?
    public static boolean onReceive(
            @NonNull Object envelope,
            @NonNull String taskName,
            @NonNull Consumer<Object> payloadConsumer) {
        if (envelope instanceof TaskPayloadEnvelope envel
                && (envel.taskName == null ||
                        Objects.equals(taskName, envel.taskName))) {

            if (ObjectUtils.anyNull(envel.payloadType, envel.payload)) {
                payloadConsumer.accept(envel.payload);
            } else {
                payloadConsumer.accept(SerialUtil.fromJson(
                        envel.payload, envel.getPayloadType()));
            }
            return envel.ackId != null;
        }
        return false;
    }

}
