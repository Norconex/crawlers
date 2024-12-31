/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite.cfg.ip;

import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;

import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Lightweight version of Ignite {@link TcpDiscoveryMulticastIpFinder}.
 */
@Data
@Accessors(chain = true)
@JsonTypeName("MulticastIpFinder")
public class LightIgniteMulticastIpFinder extends LightIgniteVmIpFinder {

    private int addressRequestAttempts =
            TcpDiscoveryMulticastIpFinder.DFLT_ADDR_REQ_ATTEMPTS;
    private String localAddress;
    private String multicastGroup =
            TcpDiscoveryMulticastIpFinder.DFLT_MCAST_GROUP;
    private int multicastPort = TcpDiscoveryMulticastIpFinder.DFLT_MCAST_PORT;
    private int responseWaitTime =
            TcpDiscoveryMulticastIpFinder.DFLT_RES_WAIT_TIME;
    private int timeToLive = -1;
}
