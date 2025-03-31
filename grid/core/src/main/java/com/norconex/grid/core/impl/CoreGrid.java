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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.protocols.FD_ALL;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.impl.compute.CoreGridCompute;
import com.norconex.grid.core.impl.compute.MessageListener;
import com.norconex.grid.core.impl.pipeline.CoreGridPipeline;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreGrid implements Grid {

    //TODO remove node name from grid interface? Or make it
    // localAddress.toString() for those who need a name but by default

    @Getter
    private final String gridName;
    //    @Getter
    //    private final String nodeName;
    @Getter
    private final JChannel channel;
    private final List<MessageListener> listeners =
            new CopyOnWriteArrayList<>();
    private final GridStorage storage;
    @Getter
    private final Address localAddress;
    @Getter
    private Address coordinator;
    private View view;
    private StorageHelper storageHelper;

    public CoreGrid(String gridName, GridStorage storage)
            throws Exception {
        this.gridName = gridName;
        this.storage = storage;

        //        // start for local testing
        //        var tcp = new TCP();
        //        var fd_sock2 = new FD_SOCK2();
        //        fd_sock2.setValue("start_port", 0);
        //        fd_sock2.setValue("end_port", 0);
        //
        //        // Create protocol list
        //        List<Protocol> protocols = new ArrayList<>();
        //        protocols.add(tcp);
        //        // ... add other protocols
        //        protocols.add(fd_sock2);
        //
        //        // Create channel with protocol array
        //        var protocolArray = protocols.toArray(new Protocol[0]);
        //        channel = new JChannel(protocolArray);
        //        // end for local testing

        channel = new JChannel(); //TODO could be passed in or configured
        //        channel.setName(nodeName);
        channel.setReceiver(createMessagesReceiver());
        //TODO make configurable, like this for unit tests right now
        channel.getProtocolStack().findProtocol(FD_ALL.class);
        channel.connect(gridName);
        localAddress = channel.getAddress();
        view = channel.getView();
        coordinator = view.getCoord();
        storageHelper = new StorageHelper(this);
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
     * @return the supplied listener, for convenience
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

    public StorageHelper storageHelper() {
        return storageHelper;
    }

    @Override
    public String getNodeName() {
        return localAddress.toString();
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
        channel.close();
    }

    //--- Private methods ------------------------------------------------------

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
