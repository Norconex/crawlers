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
package com.norconex.crawler.core.grid.impl.ignite.cfg;

import java.util.HashMap;
import java.util.Map;

import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;

import com.norconex.crawler.core.grid.impl.ignite.cfg.ip.LightIgniteIpFinder;
import com.norconex.crawler.core.grid.impl.ignite.cfg.ip.LightIgniteMulticastIpFinder;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Lightweight version of Ignite {@link DiscoverySpi}.
 */
@Data
@Accessors(chain = true)
public class LightIgniteDiscoveryConfig {

    /**
     * Mappings of socket addresses (host and port pairs) to each other
     * (e.g., for port forwarding) or mappings of a node internal address to
     * the corresponding external host (host only, port is assumed to be the
     * same). Corresponds to Ignite {@link BasicAddressResolver}.
     * @see BasicAddressResolver
     */
    private final Map<String, String> addressMappings = new HashMap<>();

    private long ackTimeout = TcpDiscoverySpi.DFLT_ACK_TIMEOUT;
    private long connectionRecoveryTimeout =
            TcpDiscoverySpi.DFLT_CONNECTION_RECOVERY_TIMEOUT;
    @NonNull
    private LightIgniteIpFinder tcpIpFinder =
            new LightIgniteMulticastIpFinder();
    private long joinTimeout = TcpDiscoverySpi.DFLT_JOIN_TIMEOUT;
    private String localAddress;
    private int localPort = TcpDiscoverySpi.DFLT_PORT;
    private int localPortRange = TcpDiscoverySpi.DFLT_PORT_RANGE;
    private long maxAckTimeout = TcpDiscoverySpi.DFLT_MAX_ACK_TIMEOUT;
    private long networkTimeout = TcpDiscoverySpi.DFLT_NETWORK_TIMEOUT;
    private int reconnectCount = TcpDiscoverySpi.DFLT_RECONNECT_CNT;
    private int reconnectDelaly = (int) TcpDiscoverySpi.DFLT_RECONNECT_DELAY;
    private long socketTimeout = TcpDiscoverySpi.DFLT_SOCK_TIMEOUT;
    private int soLinger = TcpDiscoverySpi.DFLT_SO_LINGER;
    private long statisticsPrintFrequency =
            TcpDiscoverySpi.DFLT_STATS_PRINT_FREQ;
    private int topHistorySize = TcpDiscoverySpi.DFLT_TOP_HISTORY_SIZE;

}
