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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.configuration.IgniteConfiguration;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnectorConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Supported Ignite grid configuration options applied to each node in a
 * cluster.
 * </p>
 * <p>
 * Only a relatively small subset of all Ignite configuration options are
 * offered by this class. For more advanced configuration, refer to the
 * Java API and  provide
 * your own {@link IgniteGridConnectorConfig#setIgniteConfigAdapter(
 * java.util.function.Function)}. Be advised this approach is for experts
 * and doing so may adversely affect the crawler behavior.
 * </p>
 * <p>
 * Where documentation is scarce, please refer to similar/same properties in
 * {@link IgniteConfiguration} or the official
 * <a href="https://ignite.apache.org/docs/latest/">Ignite documentation</a>
 * for more details on configuration options.
 * </p>
 * <p>
 * When the {@link IgniteGridConnector} is used without configuration, the
 * crawler will default to a single-node local cluster with minimal
 * configuration ideal for testing but not much else.
 * </p>
 */
@Data
@Accessors(chain = true)
public class LightIgniteConfig {

    private String igniteInstanceName;
    private String consistentId;
    private String localhost;
    private long metricsLogFrequency =
            IgniteConfiguration.DFLT_METRICS_LOG_FREQ;
    private long networkTimeout = IgniteConfiguration.DFLT_NETWORK_TIMEOUT;
    private Long systemWorkerBlockedTimeout;

    /**
     * Mappings of socket addresses (host and port pairs) to each other
     * (e.g., for port forwarding) or mappings of a node internal address to
     * the corresponding external host (host only, port is assumed to be the
     * same). Corresponds to Ignite {@link BasicAddressResolver}.
     * @see BasicAddressResolver
     */
    private final Map<String, String> addressMappings = new HashMap<>();

    /**
     * Ignite work directory. Leave blank to use the default, which will
     * create an "ignite" sub-directory under
     * {@link CrawlerConfig#getWorkDir()}.
     */
    private String workDirectory;

    private final LightIgniteStorageConfig storage =
            new LightIgniteStorageConfig();

    private final LightIgniteDiscoveryConfig discovery =
            new LightIgniteDiscoveryConfig();

    private final LightIgniteCommunicationConfig communication =
            new LightIgniteCommunicationConfig();

    private final List<LightIgniteCacheConfig> caches = new ArrayList<>();

    //    cacheConfiguration:
    //      - name: defaultCache
    //        cacheMode: PARTITIONED
    //        atomicityMode: TRANSACTIONAL
    //        backups: 1
    //        readThrough: true
    //        writeThrough: true
    //        cacheStoreFactory:
    //          className: org.apache.ignite.cache.store.jdbc.CacheJdbcPojoStoreFactory
    //        sqlSchema: PUBLIC
    //        queryEntities:
    //          - keyType: java.lang.String
    //            valueType: com.example.MyValueObject
    //            tableName: MY_TABLE
    //            fields:
    //              id: java.lang.String
    //              name: java.lang.String
    //            indexes:
    //              - name: IDX_MY_TABLE_NAME
    //                indexType: SORTED
    //                fields:
    //                  name: ASC
    //    eventStorageSpi:
    //      type: MemoryEventStorageSpi
    //    failureHandler:
    //      type: StopNodeOrHaltFailureHandler
    //    metricsLogFrequency: 60000
    //    clientMode: false
    //    rebalanceThreadPoolSize: 4
    //    networkTimeout: 5000
    //    systemWorkerBlockedTimeout: 10000

    //
    //    //--- Direct mapping to IgniteConfiguration --------------------------------
    //
    //    private boolean allSegmentationResolversPassRequired;
    //    private int asyncCallbackPoolSize;
    //    private boolean authenticationEnabled;
    //    private int buildIndexThreadPoolSize;
    //
    //    private List<CacheConfiguration<?, ?>> cacheConfiguration;
    //    private List<CacheKeyConfiguration> cacheKeyConfiguration;
    //    private boolean cacheSanityCheckEnabled;
    //    private List<Factory<
    //            CacheStoreSessionListener>> cacheStoreSessionListenerFactories;
    //    private List<CheckpointSpi> checkpointSpi;
    //    private ClassLoader classLoader;
    //    private ClientConnectorConfiguration clientConnectorConfiguration;
    //    private long clientFailureDetectionTimeout;
    //    private boolean clientMode;
    //    private ClusterState clusterStateOnStart;
    //    private CollisionSpi collisionSpi;
    //    private CommunicationFailureResolver communicationFailureResolver;
    //    private CommunicationSpi<?> communicationSpi;
    //    private ConnectorConfiguration connectorConfiguration;
    //    private Serializable consistentId;
    //    private DataStorageConfiguration storage;
    //    private int dataStreamerThreadPoolSize;
    //    private DeploymentMode deploymentMode;
    //    private DeploymentSpi deploymentSpi;
    //    private DiscoverySpi discoverySpi;
    //    private EncryptionSpi encryptionSpi;
    //    private EventStorageSpi eventStorageSpi;
    //    private List<ExecutorConfiguration> executorConfiguration;
    //    private List<FailoverSpi> failoverSpi;
    //    private long failureDetectionTimeout;
    //    private FailureHandler failureHandler;
    //    private IgniteLogger gridLogger;
    //    private String igniteHome;
    //    private String igniteInstanceName;
    //    private List<Integer> includeEventTypes;
    //    private List<String> includeProperties;
    //    private IndexingSpi indexingSpi;
    //    private List<LifecycleBean> lifecycleBeans;
    //    private List<LoadBalancingSpi> loadBalancingSpi;
    //    private Map<IgnitePredicate<? extends Event>, int[]> localEventListeners;
    //    private String localHost;
    //    private int managementThreadPoolSize;
    //    private boolean marshalLocalJobs;
    //    private MBeanServer mBeanServer;
    //    private List<MetricExporterSpi> metricExporterSpi;
    //    private long metricsExpireTime;
    //    private int metricsHistorySize;
    //    private long metricsLogFrequency;
    //    private long metricsUpdateFrequency;
    //    private long mvccVacuumFrequency;
    //    private int mvccVacuumThreadCount;
    //    private int networkCompressionLevel;
    //    private int networkSendRetryCount;
    //    private long networkSendRetryDelay;
    //    private long networkTimeout;
    //    private boolean peerClassLoadingEnabled;
    //    private List<String> peerClassLoadingLocalClassPathExclude;
    //    private int peerClassLoadingMissedResourcesCacheSize;
    //    private int peerClassLoadingThreadPoolSize;
    //    private PlatformConfiguration platformConfiguration;
    //    private List<PluginProvider<?>> pluginProviders;
    //    private int publicThreadPoolSize;
    //    private int queryThreadPoolSize;
    //    private long rebalanceBatchesPrefetchCount;
    //    private int rebalanceBatchSize;
    //    private int rebalanceThreadPoolSize;
    //    private long rebalanceThrottle;
    //    private long rebalanceTimeout;
    //    private SegmentationPolicy segmentationPolicy;
    //    private int segmentationResolveAttempts;
    //    private List<SegmentationResolver> segmentationResolvers;
    //    private long segmentCheckFrequency;
    //    private List<ServiceConfiguration> serviceConfiguration;
    //    private int serviceThreadPoolSize;
    //    private ShutdownPolicy shutdownPolicy;
    //    private String snapshotPath;
    //    private int snapshotThreadPoolSize;
    //    private SqlConfiguration sqlConfiguration;
    //    private Factory<SSLContext> sslContextFactory;
    //    private int stripedPoolSize;
    //    private int systemThreadPoolSize;
    //    private List<SystemViewExporterSpi> systemViewExporterSpi;
    //    private long systemWorkerBlockedTimeout;
    //    private int timeServerPortBase;
    //    private int timeServerPortRange;
    //    private TracingSpi<?> tracingSpi;
    //    private TransactionConfiguration transactionConfiguration;
    //    private Map<String, ?> userAttributes;
    //    private long utilityCacheKeepAliveTime;
    //    private int utilityCachePoolSize;
    //    private boolean waitForSegmentOnStart;
    //    private IgniteInClosure<IgniteConfiguration> warmupClosure;
    //    private String workDirectory;

    //MAYBE make those configurable as well?
    /*
    private Executor asyncContinuationExecutor;
    private AtomicConfiguration atomicConfiguration;
    private BinaryConfiguration binaryConfiguration;
    
    */

}

/*

igniteInstanceName: myIgniteInstance
workDirectory: /path/to/work
storage:
  defaultDataRegionConfiguration:
    name: Default_Region
    initialSize: 100MB
    maxSize: 1GB
    persistenceEnabled: false
  persistenceEnabled: false
discoverySpi:
  type: TcpDiscoverySpi
  ipFinder:
    type: TcpDiscoveryMulticastIpFinder
    addresses:
      - 127.0.0.1:47500..47509
communicationSpi:
  type: TcpCommunicationSpi
  localPort: 47100
  localPortRange: 20
cacheConfiguration:
  - name: defaultCache
    cacheMode: PARTITIONED
    atomicityMode: TRANSACTIONAL
    backups: 1
    readThrough: true
    writeThrough: true
    cacheStoreFactory:
      className: org.apache.ignite.cache.store.jdbc.CacheJdbcPojoStoreFactory
    sqlSchema: PUBLIC
    queryEntities:
      - keyType: java.lang.String
        valueType: com.example.MyValueObject
        tableName: MY_TABLE
        fields:
          id: java.lang.String
          name: java.lang.String
        indexes:
          - name: IDX_MY_TABLE_NAME
            indexType: SORTED
            fields:
              name: ASC
eventStorageSpi:
  type: MemoryEventStorageSpi
failureHandler:
  type: StopNodeOrHaltFailureHandler
metricsLogFrequency: 60000
clientMode: false
rebalanceThreadPoolSize: 4
networkTimeout: 5000
systemWorkerBlockedTimeout: 10000

*/
