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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Lightweight version of Ignite {@link TcpDiscoveryVmIpFinder}.
 */
@Data
@Accessors(chain = true)
@JsonTypeName("VmIpFinder")
public class LightIgniteVmIpFinder implements LightIgniteIpFinder {

    private final List<String> addresses = new ArrayList<>();
    private boolean shared;

    public Collection<String> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }

    public void setAddresses(Collection<String> addresses) {
        CollectionUtil.setAll(this.addresses, addresses);
    }
}
