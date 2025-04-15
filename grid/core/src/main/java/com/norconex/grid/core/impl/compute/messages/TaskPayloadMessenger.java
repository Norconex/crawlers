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

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.jgroups.Address;
import org.jgroups.util.UUID;

import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.MessageListener;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A wrapper around grid message sending method, enhanced for tasks-related
 * messages.
 */
@Slf4j
public class TaskPayloadMessenger {

    // acknowledgement should be fast.
    private static final long ACK_TIMEOUT_MS = 5 * 60 * 1000L;

    private final CoreGrid grid;
    private final Map<String, PendingAck> pendingAcks =
            new ConcurrentHashMap<>();
    private final Executor listenerExecutor = Executors.newCachedThreadPool();

    public TaskPayloadMessenger(CoreGrid grid) {
        this.grid = grid;
        grid.addListener((payload, from) -> {
            if (payload instanceof Ack ack) {
                var pendingAck = pendingAcks.remove(ack.getId());
                if (pendingAck != null) {
                    logAck(from);
                    pendingAck.future.complete(null);
                }
            } else if ((payload instanceof TaskPayloadEnvelope envel)
                    && (envel.getAckId() != null)) {
                // TODO Should we let receiver do it on their own terms
                // after they secure/process the message?
                // Acknowledge
                grid.sendTo(from, new Ack(envel.getAckId()));
            }
            // do a sweep for expired ones.
            pendingAcks.forEach((id, entry) -> {
                var now = System.currentTimeMillis();
                if (now - entry.creationTime > ACK_TIMEOUT_MS) {
                    entry.future.completeExceptionally(
                            new TimeoutException("Ack timed out"));
                    pendingAcks.remove(id);
                }
            });
        });
    }

    /**
     * Listen for payloads of the given class type, coming from messages
     * matching a task name.
     * @param taskName task name
     * @param payloadType payload type
     * @param listener the task-specific listener
     * @return the added listener, for convenience
     */
    public MessageListener addTaskMessageListener(
            String taskName, Class<?> payloadType, MessageListener listener) {
        return addTaskMessageListener(taskName, (payload, from) -> {
            if (payloadType.isInstance(payload)) {
                listener.onMessage(payload, from);
            }
        });
    }

    /**
     * Listen for any payload coming from messages matching a task name.
     * @param taskName task name
     * @param listener the task-specific listener
     * @return the added listener, for convenience
     */
    public MessageListener addTaskMessageListener(
            String taskName, MessageListener listener) {

        MessageListener envelListener =
                (envel, from) -> TaskPayloadEnvelope.onReceive(
                        envel,
                        taskName,
                        payload -> listenerExecutor.execute(
                                () -> listener.onMessage(payload, from)));
        grid.addListener(envelListener);
        return envelListener;
    }

    public CompletableFuture<Void> sendAndAwaitAck(
            String taskName, Object payload) {
        return sendToAndAwaitAck(null, taskName, payload);
    }

    public CompletableFuture<Void> sendToCoordAndAwaitAck(
            String taskName, Object payload) {
        return sendToAndAwaitAck(grid.getCoordinator(), taskName, payload);
    }

    public CompletableFuture<Void> sendToAndAwaitAck(
            Address dest, String taskName, Object payload) {
        var id = UUID.randomUUID().toString();
        var pendingAck = new PendingAck();
        pendingAcks.put(id, pendingAck);
        doSend(dest, TaskPayloadEnvelope.of(taskName, payload).setAckId(id));
        return pendingAck.future;
    }

    public void send(String taskName, Object payload) {
        sendTo(null, taskName, payload);
    }

    public void sendToCoord(String taskName, Object payload) {
        sendTo(grid.getCoordinator(), taskName, payload);
    }

    public void sendTo(Address dest, String taskName, Object payload) {
        doSend(dest, TaskPayloadEnvelope.of(taskName, payload));
    }

    private void doSend(Address dest, TaskPayloadEnvelope envel) {
        grid.sendTo(dest, envel);
        logSent(dest);
    }

    private void logSent(Address target) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("MSG [{}] ---> ✉️ [{}]",
                    grid.getLocalAddress(), target == null ? "ALL" : target);
        }
    }

    private void logAck(Address ackSender) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("ACK [{}] ✉️ <--- [{}]",
                    grid.getLocalAddress(), ackSender);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class Ack implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
    }

    static class PendingAck {
        private final CompletableFuture<Void> future =
                new CompletableFuture<>();
        private final long creationTime = System.currentTimeMillis();
    }
}
