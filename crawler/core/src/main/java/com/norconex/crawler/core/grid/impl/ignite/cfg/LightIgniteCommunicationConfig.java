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
package com.norconex.crawler.core.grid.impl.ignite.cfg;

import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.communication.tcp.internal.TcpCommunicationConfiguration;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Lightweight version of Ignite {@link TcpCommunicationSpi} and
 * {@link TcpCommunicationConfiguration}.
 */
@Data
@Accessors(chain = true)
public class LightIgniteCommunicationConfig {
    private int ackSendThreshold = TcpCommunicationSpi.DFLT_ACK_SND_THRESHOLD;
    private int connectionsPerNode = TcpCommunicationSpi.DFLT_CONN_PER_NODE;
    private long connectTimeout = TcpCommunicationSpi.DFLT_CONN_TIMEOUT;
    /** Whether to disable allocating direct buffer. */
    private boolean directBufferDisabled;
    private boolean directSendBuffer;
    private boolean filterReachableAddresses =
            TcpCommunicationSpi.DFLT_FILTER_REACHABLE_ADDRESSES;
    private long idleConnectionTimeout =
            TcpCommunicationSpi.DFLT_IDLE_CONN_TIMEOUT;
    private String localAddress;
    private int localPort = TcpCommunicationSpi.DFLT_PORT;
    private int localPortRange = TcpCommunicationSpi.DFLT_PORT_RANGE;
    private long maxConnectTimeout = TcpCommunicationSpi.DFLT_MAX_CONN_TIMEOUT;
    private int messageQueueLimit = TcpCommunicationSpi.DFLT_MSG_QUEUE_LIMIT;
    private int reconnectCount = TcpCommunicationSpi.DFLT_RECONNECT_CNT;
    private int selectorsCount = TcpCommunicationSpi.DFLT_SELECTORS_CNT;
    private long selectorSpins =
            TcpCommunicationConfiguration.DFLT_SELECTOR_SPINS;
    private int slowClientQueueLimit;
    private int socketReceiveBuffer = TcpCommunicationSpi.DFLT_SOCK_BUF_SIZE;
    private int socketSendBuffer = TcpCommunicationSpi.DFLT_SOCK_BUF_SIZE;
    private long socketWriteTimeout =
            TcpCommunicationSpi.DFLT_SOCK_WRITE_TIMEOUT;
    /**
     * Whether to disable setting the <code>TCP_NO_DELAY</code>
     * when creating sockets.
     */
    private boolean tcpNoDelayDisabled; // is true by defaul in ignite
    private int unacknowledgedMessagesBufferSize;
    private boolean usePairedConnections;
}
