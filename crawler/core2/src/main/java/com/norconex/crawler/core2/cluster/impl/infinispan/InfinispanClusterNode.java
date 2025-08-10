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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.io.Closeable;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;

import com.norconex.crawler.core2.cluster.ClusterNode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class InfinispanClusterNode implements ClusterNode, Closeable {

    private final DefaultCacheManager cacheManager;

    @Override
    public String getNodeName() {
        // Use the address as the node name, since Infinispan sets the node name in the address
        return ofNullable(cacheManager.getAddress())
                .map(Address::toString)
                .orElse("local");
    }
    //
    //    @Override
    //    public String getAddress() {
    //        return ofNullable(cacheManager.getAddress())
    //                .map(Address::toString)
    //                .orElse("local");
    //    }

    /**
     * @return true if this JVM is effectively standalone
     * (no cache in clustered mode).
     */
    public boolean isStandaloneNode() {
        // If your default cache is LOCAL, the node is standalone
        var defaultConfig = cacheManager.getDefaultCacheConfiguration();
        if (defaultConfig == null) {
            //TODO really?
            // If no default cache, treat as standalone
            return true;
        }
        return !defaultConfig
                .clustering()
                .cacheMode()
                .isClustered();
    }

    /**
     * @return true if this node should run “one‑per‑cluster” tasks.
     *   - In standalone mode (no clustering at all): true
     *   - In clustered mode: true only for the elected coordinator
     */
    @Override
    public boolean isCoordinator() {

        // MAYBE use this manager.getCoordinator() and compare address and
        // if null, treat as coord

        if (isStandaloneNode()) {
            return true;
        }
        return cacheManager.isCoordinator();
    }

    @Override
    public void close() {
        // Only close the cache manager if this node is being disconnected
        if (cacheManager != null) {
            cacheManager.stop();
        }
    }
}