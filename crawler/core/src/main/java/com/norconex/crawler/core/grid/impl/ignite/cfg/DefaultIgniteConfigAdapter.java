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

import java.util.List;
import java.util.function.Function;

import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.sharedfs.TcpDiscoverySharedFsIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.grid.impl.ignite.cfg.ip.LightIgniteMulticastIpFinder;
import com.norconex.crawler.core.grid.impl.ignite.cfg.ip.LightIgniteVmIpFinder;

import lombok.SneakyThrows;

/**
 * Default converter of crawler {@link LightIgniteConfig} to Ignite native
 * {@link IgniteConfiguration}.
 */
public class DefaultIgniteConfigAdapter
        implements Function<LightIgniteConfig, IgniteConfiguration> {

    @SneakyThrows()
    @Override
    public IgniteConfiguration apply(LightIgniteConfig lightCfg) {
        //NOTE: for each main config objects, we first copy values from/to
        // identical properties, then we handle the special cases.
        // We make sure not to forget any property or have mis-mapping in
        // unit tests.

        //--- Main config ---
        var igniteCfg = new IgniteConfiguration();
        BeanUtil.copyProperties(igniteCfg, lightCfg);
        igniteCfg.setAddressResolver(
                new BasicAddressResolver(lightCfg.getAddressMappings()));
        igniteCfg.setGridLogger(new Slf4jLogger());
        igniteCfg.setPeerClassLoadingEnabled(false);

        //--- Storage config ---
        igniteCfg.setDataStorageConfiguration(
                toDataStorageConfiguration(lightCfg.getStorage()));

        //--- Discovery config ---
        igniteCfg.setDiscoverySpi(toDiscoverySpi(lightCfg.getDiscovery()));

        //--- Communication config ---
        igniteCfg.setCommunicationSpi(
                toCommunicationSpi(lightCfg.getCommunication()));

        //--- Cache configs ---
        igniteCfg.setCacheConfiguration(
                toCacheConfigurations(lightCfg.getCaches()));

        return igniteCfg;
    }

    protected DataStorageConfiguration toDataStorageConfiguration(
            LightIgniteStorageConfig lightStorageCfg) {
        var igniteStorageCfg = new DataRegionConfiguration();
        BeanUtil.copyProperties(igniteStorageCfg, lightStorageCfg);
        igniteStorageCfg.setPersistenceEnabled(
                !lightStorageCfg.isPersistenceDisabled());
        return new DataStorageConfiguration()
                .setDefaultDataRegionConfiguration(igniteStorageCfg);
    }

    @SneakyThrows
    protected DiscoverySpi toDiscoverySpi(
            LightIgniteDiscoveryConfig lightDiscoveryCfg) {
        var igniteDiscoverySpi = new TcpDiscoverySpi();
        BeanUtil.copyProperties(igniteDiscoverySpi, lightDiscoveryCfg);
        igniteDiscoverySpi.setAddressResolver(new BasicAddressResolver(
                lightDiscoveryCfg.getAddressMappings()));

        var lightIpFinder = lightDiscoveryCfg.getIpFinder();
        TcpDiscoveryIpFinder igniteIpFinder = null;
        if (lightIpFinder instanceof LightIgniteMulticastIpFinder) {
            igniteIpFinder = new TcpDiscoveryVmIpFinder();
        } else if (lightIpFinder instanceof LightIgniteVmIpFinder) {
            igniteIpFinder = new TcpDiscoveryMulticastIpFinder();
        } else {
            igniteIpFinder = new TcpDiscoverySharedFsIpFinder();
        }
        BeanUtil.copyProperties(igniteIpFinder, lightIpFinder);
        igniteDiscoverySpi.setIpFinder(igniteIpFinder);
        return igniteDiscoverySpi;
    }

    protected TcpCommunicationSpi toCommunicationSpi(
            LightIgniteCommunicationConfig lightCommCfg) {
        var igniteCommSpi = new TcpCommunicationSpi();
        BeanUtil.copyProperties(igniteCommSpi, lightCommCfg);
        igniteCommSpi.setDirectBuffer(!lightCommCfg.isDirectBufferDisabled());
        igniteCommSpi.setTcpNoDelay(!lightCommCfg.isTcpNoDelayDisabled());
        return igniteCommSpi;
    }

    private CacheConfiguration<?, ?>[]
            toCacheConfigurations(List<LightIgniteCacheConfig> caches) {
        return caches.stream()
                .map(lcc -> {
                    var cc = new CacheConfiguration<>();
                    BeanUtil.copyProperties(cc, lcc);
                    cc.setCopyOnRead(!lcc.isCopyOnReadDisabled());
                    cc.setEagerTtl(!lcc.isEagerTtlDisabled());
                    cc.setReadFromBackup(!lcc.isReadFromBackupDisabled());
                    cc.setWriteBehindCoalescing(
                            !lcc.isWriteBehindCoalescingDisabled());
                    return cc;
                })
                .toList()
                .toArray(new CacheConfiguration[] {});
    }
}
