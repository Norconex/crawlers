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
package com.norconex.crawler.core.grid.impl.ignite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Function;

import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheRebalanceMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.PartitionLossPolicy;
import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.DiskPageCompression;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.sharedfs.TcpDiscoverySharedFsIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.map.MapUtil;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteConfigAdapter;
import com.norconex.crawler.core.junit.CrawlTest;

class IgniteGridConnectorTest {

    //--- Light config fixtures ------------------------------------------------

    private static final String LIGHT_IP_FINDER_VM_CFG = """
              tcpIpFinder:
                class: VmIpFinder
                addresses:
                  - 192.168.0.3
                  - 192.168.0.4
                  - 192.168.0.5
                shared: true
        """;
    private static final String LIGHT_IP_FINDER_MULTICAST_CFG = """
            tcpIpFinder:
              class: MulticastIpFinder
              addresses:
                - 192.168.0.6
                - 192.168.0.7
                - 192.168.0.8
              shared: true
              addressRequestAttempts: 3
              localAddress: 192.168.0.11
              multicastGroup: 192.168.0.12
              multicastPort: 1234
              responseWaitTime: 5678
              timeToLive: 90
      """;
    private static final String LIGHT_IP_FINDER_SHARED_FS_CFG = """
            tcpIpFinder:
              class: SharedFsIpFinder
              path: /some/path
              shared: true
      """;
    private static final String LIGHT_CFG = """
        startReferences:
          - "mock:some_reference"
        gridConnector:
          class: IgniteGridConnector
          igniteConfig:
            igniteInstanceName: BlahInstance
            consistentId: BlahConsistendId
            localHost: 127.0.0.1
            metricsLogFrequency: 1111
            networkTimeout: 2222
            systemWorkerBlockedTimeout: 3333
            workDirectory: BlahWorkDir
            addressMappings:
              "192.168.1.1:1111": "192.168.1.2:2222"
              "192.168.1.3:3333": "192.168.1.4:4444"
              "192.168.1.5": "192.168.1.6"
            storage:
              initialSize: 4444
              maxSize: 5555
              name: blahStorage
              persistenceDisabled: true
            communication:
              ackSendThreshold: 1020
              connectionsPerNode: 1021
              connectTimeout: 1022
              directBufferDisabled: true
              directSendBuffer: true
              filterReachableAddresses: true
              idleConnectionTimeout: 1023
              localAddress: 102.168.2.1
              localPort: 1024
              localPortRange: 60
              maxConnectTimeout: 1025
              messageQueueLimit: 61
              reconnectCount: 62
              selectorsCount: 63
              selectorSpins: 64
              slowClientQueueLimit: 65
              socketReceiveBuffer: 1026
              socketSendBuffer: 1027
              socketWriteTimeout: 1028
              tcpNoDelayDisabled: true
              unacknowledgedMessagesBufferSize: 1029
              usePairedConnections: true
            caches:
              - atomicityMode: TRANSACTIONAL
                backups: 1
                cacheMode: REPLICATED
                copyOnReadDisabled: true
                diskPageCompression: ZSTD
                diskPageCompressionLevel: 1
                eagerTtlDisabled: true
                encryptionEnabled: true
                eventsDisabled: true
                groupName: blahGroup
                invalidate: true
                loadPreviousValue: true
                managementEnabled: true
                maxConcurrentAsyncOperations: 2
                maxQueryIteratorsCount: 3
                name: blahCache
                onheapCacheEnabled: true
                partitionLossPolicy: READ_ONLY_SAFE
                queryDetailMetricsSize: 4
                queryParallelism: 5
                readFromBackupDisabled: true
                readThrough: true
                rebalanceMode: SYNC
                rebalanceOrder: 7
                statisticsEnabled: true
                storeByValueDisabled: true
                storeConcurrentLoadAllThreshold: 8
                storeKeepBinary: true
                writeBehindBatchSize: 513
                writeBehindCoalescingDisabled: true
                writeBehindEnabled: true
                writeBehindFlushFrequency: 6000
                writeBehindFlushSize: 7000
                writeBehindFlushThreadCount: 2
                writeSynchronizationMode: FULL_SYNC
                writeThrough: true
              - atomicityMode: ATOMIC
                backups: 2
                cacheMode: PARTITIONED
                copyOnReadDisabled: false
                diskPageCompression: SNAPPY
                diskPageCompressionLevel: 2
                eagerTtlDisabled: false
                encryptionEnabled: false
                eventsDisabled: false
                groupName: blahGroup2
                invalidate: false
                loadPreviousValue: false
                managementEnabled: false
                maxConcurrentAsyncOperations: 3
                maxQueryIteratorsCount: 4
                name: blahCache2
                onheapCacheEnabled: false
                partitionLossPolicy: READ_WRITE_SAFE
                queryDetailMetricsSize: 5
                queryParallelism: 6
                readFromBackupDisabled: false
                readThrough: false
                rebalanceMode: ASYNC
                rebalanceOrder: 8
                statisticsEnabled: false
                storeByValueDisabled: false
                storeConcurrentLoadAllThreshold: 9
                storeKeepBinary: false
                writeBehindBatchSize: 514
                writeBehindCoalescingDisabled: false
                writeBehindEnabled: false
                writeBehindFlushFrequency: 8000
                writeBehindFlushSize: 9000
                writeBehindFlushThreadCount: 3
                writeSynchronizationMode: PRIMARY_SYNC
                writeThrough: false
            discovery:
              ackTimeout: 7777
              addressMappings:
                "192.168.1.1:5555": "192.168.1.2:6666"
                "192.168.1.3:7777": "192.168.1.4:8888"
                "192.168.1.9": "192.168.1.10"
              connectionRecoveryTimeout: 8888
              joinTimeout: 9999
              localAddress: 192.168.0.2
              localPort: 1001
              localPortRange: 50
              maxAckTimeout: 1002
              networkTimeout: 1003
              reconnectCount: 1004
              reconnectDelaly: 1005
              socketTimeout: 1006
              soLinger: 1007
              statisticsPrintFrequency: 1008
              topHistorySize: 51
        """;

    private static final String LIGHT_VM_CFG =
            LIGHT_CFG + LIGHT_IP_FINDER_VM_CFG;
    private static final String LIGHT_MULTICAST_CFG =
            LIGHT_CFG + LIGHT_IP_FINDER_MULTICAST_CFG;
    private static final String LIGHT_SHARED_FS_CFG =
            LIGHT_CFG + LIGHT_IP_FINDER_SHARED_FS_CFG;

    //--- Ignite config fixtures -----------------------------------------------

    private static final TcpDiscoveryIpFinder IGNITE_IP_FINDER_VM_CFG =
            new TcpDiscoveryVmIpFinder()
                    .setAddresses(List.of(
                            "192.168.0.3",
                            "192.168.0.4",
                            "192.168.0.5"))
                    .setShared(true);

    private static final TcpDiscoveryIpFinder IGNITE_IP_FINDER_MULTICAST_CFG =
            new TcpDiscoveryMulticastIpFinder()
                    .setAddressRequestAttempts(3)
                    .setLocalAddress("192.168.0.11")
                    .setMulticastGroup("192.168.0.12")
                    .setMulticastPort(1234)
                    .setResponseWaitTime(5678)
                    .setTimeToLive(90)
                    .setAddresses(List.of(
                            "192.168.0.6",
                            "192.168.0.7",
                            "192.168.0.8"))
                    .setShared(true);

    private static final TcpDiscoveryIpFinder IGNITE_IP_FINDER_SHARED_FS_CFG =
            new TcpDiscoverySharedFsIpFinder()
                    .setPath("/some/path")
                    .setShared(true);

    // @formatter:off
    private static final Function<TcpDiscoveryIpFinder,
            IgniteConfiguration> IGNITE_CFG_PROV = ipFinder -> {
        var igniteCfg = new IgniteConfiguration();
        try {
            igniteCfg.setIgniteInstanceName("BlahInstance")
                .setConsistentId("BlahConsistendId")
                .setLocalHost("127.0.0.1")
                .setMetricsLogFrequency(1111)
                .setNetworkTimeout(2222)
                .setSystemWorkerBlockedTimeout(3333)
                .setWorkDirectory("BlahWorkDir")
                .setAddressResolver(new BasicAddressResolver(MapUtil.toMap(
                        "192.168.1.1:1111",
                        "192.168.1.2:2222",
                        "192.168.1.3:3333",
                        "192.168.1.4:4444",
                        "192.168.1.5",
                        "192.168.1.6")))
                .setDataStorageConfiguration(new DataStorageConfiguration()
                    .setDefaultDataRegionConfiguration(
                            new DataRegionConfiguration()
                                .setInitialSize(4444)
                                .setMaxSize(5555)
                                .setName("blahStorage")
                                .setPersistenceEnabled(false)))
                .setCommunicationSpi(new TcpCommunicationSpi()
                    .setAckSendThreshold(1020)
                    .setConnectionsPerNode(1021)
                    .setConnectTimeout(1022)
                    .setDirectBuffer(false)
                    .setDirectSendBuffer(true)
                    .setFilterReachableAddresses(true)
                    .setIdleConnectionTimeout(1023)
                    .setLocalAddress("102.168.2.1")
                    .setLocalPort(1024)
                    .setLocalPortRange(60)
                    .setMaxConnectTimeout(1025)
                    .setMessageQueueLimit(61)
                    .setReconnectCount(62)
                    .setSelectorsCount(63)
                    .setSelectorSpins(64)
                    .setSlowClientQueueLimit(65)
                    .setSocketReceiveBuffer(1026)
                    .setSocketSendBuffer(1027)
                    .setSocketWriteTimeout(1028)
                    .setTcpNoDelay(false)
                    .setUnacknowledgedMessagesBufferSize(1029)
                    .setUsePairedConnections(true))
                .setCacheConfiguration(
                        new CacheConfiguration<String, String>()
                            .setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)
                            .setBackups(1)
                            .setCacheMode(CacheMode.REPLICATED)
                            .setCopyOnRead(false)
                            .setDiskPageCompression(DiskPageCompression.ZSTD)
                            .setDiskPageCompressionLevel(1)
                            .setEagerTtl(false)
                            .setEncryptionEnabled(true)
                            .setEventsDisabled(true)
                            .setGroupName("blahGroup")
                            .setInvalidate(true)
                            .setLoadPreviousValue(true)
                            .setManagementEnabled(true)
                            .setMaxConcurrentAsyncOperations(2)
                            .setMaxQueryIteratorsCount(3)
                            .setName("blahCache")
                            .setOnheapCacheEnabled(true)
                            .setPartitionLossPolicy(
                                    PartitionLossPolicy.READ_ONLY_SAFE)
                            .setQueryDetailMetricsSize(4)
                            .setQueryParallelism(5)
                            .setReadFromBackup(false)
                            .setReadThrough(true)
                            .setRebalanceMode(CacheRebalanceMode.SYNC)
                            .setRebalanceOrder(7)
                            .setStatisticsEnabled(true)
                            .setStoreByValue(false)
                            .setStoreConcurrentLoadAllThreshold(8)
                            .setStoreKeepBinary(true)
                            .setWriteBehindBatchSize(513)
                            .setWriteBehindCoalescing(false)
                            .setWriteBehindEnabled(true)
                            .setWriteBehindFlushFrequency(6000)
                            .setWriteBehindFlushSize(7000)
                            .setWriteBehindFlushThreadCount(2)
                            .setWriteSynchronizationMode(
                                    CacheWriteSynchronizationMode.FULL_SYNC)
                            .setWriteThrough(true),
                        new CacheConfiguration<String, String>()
                            .setAtomicityMode(CacheAtomicityMode.ATOMIC)
                            .setBackups(2)
                            .setCacheMode(CacheMode.PARTITIONED)
                            .setCopyOnRead(true)
                            .setDiskPageCompression(DiskPageCompression.SNAPPY)
                            .setDiskPageCompressionLevel(2)
                            .setEagerTtl(true)
                            .setEncryptionEnabled(false)
                            .setEventsDisabled(false)
                            .setGroupName("blahGroup2")
                            .setInvalidate(false)
                            .setLoadPreviousValue(false)
                            .setManagementEnabled(false)
                            .setMaxConcurrentAsyncOperations(3)
                            .setMaxQueryIteratorsCount(4)
                            .setName("blahCache2")
                            .setOnheapCacheEnabled(false)
                            .setPartitionLossPolicy(
                                    PartitionLossPolicy.READ_WRITE_SAFE)
                            .setQueryDetailMetricsSize(5)
                            .setQueryParallelism(6)
                            .setReadFromBackup(true)
                            .setReadThrough(false)
                            .setRebalanceMode(CacheRebalanceMode.ASYNC)
                            .setRebalanceOrder(8)
                            .setStatisticsEnabled(false)
                            .setStoreByValue(true)
                            .setStoreConcurrentLoadAllThreshold(9)
                            .setStoreKeepBinary(false)
                            .setWriteBehindBatchSize(514)
                            .setWriteBehindCoalescing(true)
                            .setWriteBehindEnabled(false)
                            .setWriteBehindFlushFrequency(8000)
                            .setWriteBehindFlushSize(9000)
                            .setWriteBehindFlushThreadCount(3)
                            .setWriteSynchronizationMode(
                                    CacheWriteSynchronizationMode.PRIMARY_SYNC)
                            .setWriteThrough(false));

            var tcpDiscovery = new TcpDiscoverySpi();
            tcpDiscovery
                .setAckTimeout(7777)
                .setLocalAddress("192.168.0.2")
                .setLocalPort(1001)
                .setLocalPortRange(50)
                .setMaxAckTimeout(1002)
                .setNetworkTimeout(1003)
                .setReconnectCount(1004)
                .setReconnectDelay(1005)
                .setSocketTimeout(1006)
                .setStatisticsPrintFrequency(1008)
                .setTopHistorySize(51);
            tcpDiscovery.setJoinTimeout(9999);
            tcpDiscovery.setConnectionRecoveryTimeout(8888);
            tcpDiscovery.setSoLinger(1007);
            tcpDiscovery.setAddressResolver(
                    new BasicAddressResolver(MapUtil.toMap(
                            "192.168.1.1:5555",
                            "192.168.1.2:6666",
                            "192.168.1.3:7777",
                            "192.168.1.4:8888",
                            "192.168.1.9", "192.168.1.10")));
            tcpDiscovery.setIpFinder(ipFinder);
            igniteCfg.setDiscoverySpi(tcpDiscovery);
            return igniteCfg;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };
    // @formatter:on

    //--- Tests ----------------------------------------------------------------

    @CrawlTest(
        config = LIGHT_VM_CFG,
        gridConnectors = IgniteGridConnector.class
    )
    void testWithVmFinder(CrawlerConfig cfg) {
        assertWriteRead(cfg);
        assertIgniteConfigConversion(cfg,
                IGNITE_CFG_PROV.apply(IGNITE_IP_FINDER_VM_CFG));
    }

    @CrawlTest(
        config = LIGHT_MULTICAST_CFG,
        gridConnectors = IgniteGridConnector.class
    )
    void testWithMulticastFinder(CrawlerConfig cfg) {
        assertWriteRead(cfg);
        assertIgniteConfigConversion(cfg,
                IGNITE_CFG_PROV.apply(IGNITE_IP_FINDER_MULTICAST_CFG));
    }

    @CrawlTest(
        config = LIGHT_SHARED_FS_CFG,
        gridConnectors = IgniteGridConnector.class
    )
    void testWithSharedFsFinder(CrawlerConfig cfg) {
        assertWriteRead(cfg);
        assertIgniteConfigConversion(cfg,
                IGNITE_CFG_PROV.apply(IGNITE_IP_FINDER_SHARED_FS_CFG));
    }

    void assertWriteRead(CrawlerConfig cfg) {
        assertThatNoException().isThrownBy(
                () -> new CrawlerSpec().beanMapper().assertWriteRead(cfg));
    }

    void assertIgniteConfigConversion(
            CrawlerConfig cfg, IgniteConfiguration expectedIgniteCfg) {

        var actualIgniteCfg = new DefaultIgniteConfigAdapter().apply(
                ((IgniteGridConnector) cfg.getGridConnector())
                        .getConfiguration()
                        .getIgniteConfig());

        assertThat(actualIgniteCfg)
                .usingRecursiveComparison()
                .ignoringFields(
                        "commSpi.ctxInitLatch",
                        "discoSpi.ctxInitLatch",
                        "discoSpi.ipFinder.initLatch",
                        "log")
                .isEqualTo(expectedIgniteCfg);
    }
}