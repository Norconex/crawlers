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
package com.norconex.grid.core.impl;

import static java.util.Optional.ofNullable;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.protocols.BARRIER;
import org.jgroups.protocols.FD_ALL;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.PING;
import org.jgroups.protocols.UDP;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.VERIFY_SUSPECT;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.impl.compute.ComputeStateAtTime;
import com.norconex.grid.core.impl.compute.ComputeStateStore;
import com.norconex.grid.core.impl.compute.CoreGridCompute;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.compute.messages.TaskPayloadMessenger;
import com.norconex.grid.core.impl.compute.messages.StopComputeMessage;
import com.norconex.grid.core.impl.pipeline.CoreGridPipeline;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;
import com.norconex.grid.core.util.ConcurrentUtil;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreGrid implements Grid {

    @Getter
    private final String gridName;
    private final String nodeName;
    private final JChannel channel;
    private final List<MessageListener> listeners =
            new CopyOnWriteArrayList<>();
    private final GridStorage storage;
    @Getter
    private final Address localAddress;
    @Getter
    private Address coordinator;
    private View view;
    private ComputeStateStore computeStateStorage;
    private TaskPayloadMessenger taskPayloadMessenger;

    public CoreGrid(CoreGridConnectorConfig connConfig, GridStorage storage)
            throws Exception {
        this(connConfig, null, storage);
    }

    public CoreGrid(
            CoreGridConnectorConfig connConfig,
            String nodeName,
            GridStorage storage)
            throws Exception {
        gridName = connConfig.getGridName();
        this.nodeName = nodeName;
        this.storage = storage;

        Protocol[] protStack = {
                new UDP().setValue("bind_addr",
                        InetAddress.getByName("127.0.0.1")),
                new PING(),
                new MERGE3(),
                new FD_SOCK(),
                new FD_ALL(),
                new VERIFY_SUSPECT(),
                new BARRIER(),
                new NAKACK2(),
                new UNICAST3(),
                new STABLE(),
                new GMS(),
                new UFC(),
                new MFC(),
                new FRAG2() };

        //TODO make configurable

        channel = new JChannel(protStack);
        //TODO support optional logical name        channel.setName(nodeName);
        channel.setReceiver(createMessagesReceiver());
        //TODO make configurable, like this for unit tests right now
        //        channel.getProtocolStack().findProtocol(FD_ALL.class);
        channel.connect(gridName);
        localAddress = channel.getAddress();
        view = channel.getView();
        coordinator = view.getCoord();
        computeStateStorage = new ComputeStateStore(this);
        taskPayloadMessenger = new TaskPayloadMessenger(this);
    }

    public boolean isCoordinator() {
        return Objects.equals(localAddress, coordinator);
    }

    public void send(Object payload) {
        try {
            Message msg = new ObjectMessage(null, payload);
            channel.send(msg);
        } catch (Exception e) {
            LOG.error("Could not send message: {}.", payload, e);
        }
    }

    public void sendTo(Address dest, Object payload) {
        try {
            Message msg = new ObjectMessage(dest, payload);
            channel.send(msg);
        } catch (Exception e) {
            LOG.error("Could not send message: {}.", payload, e);
        }
    }

    /**
     * Adds a grid message listener.
     * @param listener the listener
     * @return the added listener, for convenience
     */
    public MessageListener addListener(@NotNull MessageListener listener) {
        listeners.add(listener);
        return listener;
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    public List<Address> getClusterMembers() {
        return ofNullable(channel.getView())
                .map(View::getMembers)
                .orElse(List.of());
    }

    public ComputeStateStore computeStateStorage() {
        return computeStateStorage;
    }

    public TaskPayloadMessenger taskPayloadMessenger() {
        return taskPayloadMessenger;
    }

    @Override
    public String getNodeName() {
        if (StringUtils.isBlank(nodeName)) {
            return localAddress.toString();
        }
        return nodeName;
    }

    @Override
    public GridCompute compute() {
        return new CoreGridCompute(this);
    }

    @Override
    public GridPipeline pipeline() {
        return new CoreGridPipeline(this);
    }

    @Override
    public GridStorage storage() {
        return storage;
    }

    @Override
    public void close() {
        stopRunningTasks();
        channel.close();
    }

    //TODO move to storage?
    @Override
    public boolean resetSession() {
        storage().getSessionAttributes().clear();
        return computeStateStorage().reset();
    }

    //--- Private methods ------------------------------------------------------

    private void stopRunningTasks() {
        send(new StopComputeMessage(null));
        var pendingStop = CompletableFuture.runAsync(() -> {
            Map<String, ComputeStateAtTime> tasks = null;
            while (!(tasks = computeStateStorage().getRunningTasks())
                    .isEmpty()) {
                LOG.info("The following tasks are still running: \n" + (tasks
                        .entrySet()
                        .stream()
                        .map(en -> ("    - " + en.getKey() + " -> "
                                + DurationFormatter.COMPACT
                                        .format(en.getValue().elapsed())))
                        .collect(Collectors.joining("\n"))));
                Sleeper.sleepMillis(500);
            }
        });
        //TODO make configurable
        ConcurrentUtil.get(pendingStop, 1, TimeUnit.MINUTES);
    }

    private Receiver createMessagesReceiver() {
        return new Receiver() {
            @Override
            public void receive(Message msg) {
                var payload = msg.getObject();
                for (MessageListener listener : listeners) {
                    listener.onMessage(payload, msg.getSrc());
                }
            }

            @Override
            public void viewAccepted(View newView) {
                view = newView;
                LOG.info("Grid now has {} nodes.", view.size());
                var prevCoord = coordinator;
                var nextCoord = view.getCoord();
                coordinator = nextCoord;
                if (!Objects.equals(prevCoord, nextCoord)) {
                    // if it changed (as opposed to first set), notify all
                    if (prevCoord == null) {
                        LOG.info("Elected coordinator: {}", nextCoord);
                    } else {
                        LOG.info("New coordinator elected: {} -> {}",
                                prevCoord, nextCoord);
                        //TODO handle handling of new coordinator
                        //send(new NewCoordMessage());
                    }
                }
            }
        };
    }
}
