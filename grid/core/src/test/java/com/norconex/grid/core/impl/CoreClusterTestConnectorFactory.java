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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.jgroups.protocols.FD_ALL3;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.PING;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.VERIFY_SUSPECT;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.cluster.ClusterConnectorFactory;
import com.norconex.grid.core.mocks.MockStorage;
import com.norconex.grid.core.storage.GridStorage;

public class CoreClusterTestConnectorFactory
        implements ClusterConnectorFactory {

    @Override
    public GridConnector create(String gridName, String nodeName) {
        return Configurable.configure(
                new CoreGridConnector(createGridStorage()),
                cfg -> cfg.setGridName(gridName)
                        .setNodeName(nodeName)
                        .setProtocols(createProtocols()));
    }

    protected GridStorage createGridStorage() {
        return new MockStorage();
    }

    protected List<Protocol> createProtocols() {
        return new ArrayList<>(List.of(
                new SHARED_LOOPBACK()
                        .setBindAddr(localhostAddress()),
                new PING(),
                new MERGE3()
                        .setMaxInterval(30000)
                        .setMinInterval(10000),
                new FD_SOCK()
                        .setStartPort(57800),
                new FD_ALL3()
                        .setTimeout(12000)
                        .setInterval(3000),
                new VERIFY_SUSPECT()
                        .setTimeout(1500),
                new NAKACK2()
                        .useMcastXmit(false),
                new UNICAST3(),
                new STABLE()
                        .setDesiredAverageGossip(50000),
                new GMS()
                        .printLocalAddress(true)
                        .setJoinTimeout(3000),
                new UFC()
                        .setMaxCredits(20000000)
                        .setMinThreshold(0.4),
                new MFC()
                        .setMaxCredits(20000000)
                        .setMinThreshold(0.4),
                new FRAG2()
                        .setFragSize(60000)));
    }

    private static InetAddress localhostAddress() {
        try {
            return InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
