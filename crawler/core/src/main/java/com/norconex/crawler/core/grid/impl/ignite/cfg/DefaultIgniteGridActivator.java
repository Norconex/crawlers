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

import java.util.function.Consumer;

import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterState;

import com.norconex.commons.lang.Sleeper;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Default activator of an Ignite grid.
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class DefaultIgniteGridActivator implements Consumer<Ignite> {

    @Override
    public void accept(Ignite ignite) {
        // Perform initial activation if needed
        //TODO manage activation externally? Allow to activate auto-activation
        // via config? Else?
        if (ignite.cluster().state() != ClusterState.ACTIVE) {
            // Initial manual activation
            ignite.cluster().state(ClusterState.ACTIVE);
            var baselineNodes = ignite.cluster().nodes();
            ignite.cluster().setBaselineTopology(baselineNodes);
        }

        ignite.cluster().baselineAutoAdjustEnabled(true);
        ignite.cluster().baselineAutoAdjustTimeout(5000);

        while (!ignite.cluster().state().active()) {
            LOG.info("Waiting for cluster to be active...");
            Sleeper.sleepMillis(2000);
        }
        LOG.info("Cluster is now active.");
    }
}
