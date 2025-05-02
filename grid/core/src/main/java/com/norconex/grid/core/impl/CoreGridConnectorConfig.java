/* Copyright 2024-2025 Norconex Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link CoreGridConnector}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class CoreGridConnectorConfig {

    /**
     * Logical grid name to connect to. All nodes joining on the same name
     * makes up the grid.  Cannot be <code>null</code>.
     */
    @NonNull
    private String gridName = "grid_core";

    /**
     * Name unique to a grid node. Leave <code>null</code> to have it
     * auto-assigned. Default is <code>null</code>.
     */
    private String nodeName = null;

    /**
     * Protocol layers used by JGroups to handle event messages between nodes.
     */
    private final List<Protocol> protocols = new ArrayList<>(List.of(
            new UDP(),
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
            new FRAG2()));

    public List<Protocol> getProtocols() {
        return Collections.unmodifiableList(protocols);
    }

    public CoreGridConnectorConfig setProtocols(List<Protocol> protocols) {
        CollectionUtil.setAll(this.protocols, protocols);
        return this;
    }

}
