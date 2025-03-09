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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.ignite.lang.IgniteException;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridServices;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridServices implements GridServices {

    private final IgniteGrid igniteGrid;

    @Override
    public Future<?> start(
            String serviceName,
            Class<? extends GridService> serviceClass,
            String arg) {
        return CompletableFuture.runAsync(() -> {
            LOG.info("Deploying service: {}", serviceName);

            //NOTE: returns after service init()... start still running.
            // so we wait for start to be done below
            igniteGrid.getIgniteApi().services()
                    .deployClusterSingleton(
                            serviceName,
                            new IgniteGridServiceAdapter(serviceName,
                                    serviceClass, arg));

            // wait until start() is done...
            var cache = igniteGrid.storage().getCache(
                    IgniteGridKeys.RUN_ONCE_CACHE, String.class);
            var endedKey = serviceName + ".ended";
            //TODO have a timeout to for exit after a while?
            while (!Boolean.parseBoolean(cache.get(endedKey))) {
                Sleeper.sleepSeconds(1);
            }
            cache.delete(endedKey);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends GridService> T get(String serviceName) {
        try {
            var serviceAdapter = igniteGrid.getIgnite().services().serviceProxy(
                    serviceName, IgniteGridServiceAdapter.class, false);
            return (T) serviceAdapter.getService();
        } catch (IgniteException e) {
            LOG.error("Could not obtain crawler context from Ignite session. "
                    + "Was it initialized and stored in session beforehand?");
            throw e;
        }
    }

    @Override
    public Future<?> stop(String serviceName) {
        return CompletableFuture.runAsync(
                () -> igniteGrid
                        .getIgnite()
                        .services()
                        .cancelAsync(serviceName)// NOT cancel, but a clean stop
                        .get());
    }
}
