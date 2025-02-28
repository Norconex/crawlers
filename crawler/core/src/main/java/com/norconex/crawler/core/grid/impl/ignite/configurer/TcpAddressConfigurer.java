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
package com.norconex.crawler.core.grid.impl.ignite.configurer;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.config.Configurable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class TcpAddressConfigurer
        implements IgniteConfigurer, Configurable<TcpAddressConfigurerConfig> {

    @Getter
    private final TcpAddressConfigurerConfig configuration =
            new TcpAddressConfigurerConfig();

    @Override
    public void configure(IgniteConfiguration cfg) {
        // IP Finder + Discovery
        var ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(configuration.getAddresses());
        var discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);
    }
}
