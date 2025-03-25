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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridTransactions;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreGrid implements Grid {

    @Getter
    private final String clusterName;
    @Getter
    private final String nodeName;
    @Getter
    private final JChannel channel;
    private final List<MessageListener> listeners =
            new CopyOnWriteArrayList<>();
    private final GridStorage storage;
    @Getter
    private final Address localAddress;
    private View currentView;
    private CoreStorageHelper storageHelper;

    public CoreGrid(String nodeName, String clusterName, GridStorage storage)
            throws Exception {
        this.clusterName = clusterName;
        this.nodeName = nodeName;
        this.storage = storage;
        channel = new JChannel(); // could be passed in or configured
        channel.setName(nodeName);
        channel.setReceiver(new Receiver() {
            @Override
            public void receive(Message msg) {
                var payload = msg.getObject();
                for (MessageListener listener : listeners) {
                    listener.onMessage(payload, msg.getSrc());
                }
            }

            @Override
            public void viewAccepted(View view) {
                currentView = view;
                LOG.info("Grid now has {} nodes.", view.size());
            }
        });
        //TODO make configurable
        channel.getProtocolStack().getTransport().setBindPort(0);
        channel.connect(clusterName);
        localAddress = channel.getAddress();
        storageHelper = new CoreStorageHelper(this);
    }

    public boolean isCoordinator() {
        if (currentView == null) {
            return false;
        }
        return currentView.getMembers().get(0).equals(localAddress);
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

    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    public List<Address> getClusterMembers() {
        return channel.getView().getMembers();
    }

    public CoreStorageHelper storageHelper() {
        return storageHelper;
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
    public GridTransactions transactions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        channel.close();
    }

}
