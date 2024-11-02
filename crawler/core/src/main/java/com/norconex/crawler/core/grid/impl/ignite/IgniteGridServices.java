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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.services.Service;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridServices;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridServices implements GridServices {

    private final Ignite ignite;

    @Override
    public Future<?> start(
            String serviceName, Class<? extends GridService> serviceClass) {
        return CompletableFuture.runAsync(() -> {
            //            ignite.services().deployClusterSingletonAsync(
            //                    serviceName, new ServiceAdapter(serviceClass)).get();
            ignite.services().deployClusterSingleton(
                    serviceName, new ServiceAdapter(serviceClass));

            System.err.println("XXX AM I DONE RUNNING?");

            var cache = ignite.getOrCreateCache(IgniteGridKeys.GLOBAL_CACHE);
            var endedKey = serviceClass.getSimpleName() + ".ended";
            while (!Boolean.valueOf((String) cache.get(endedKey))) {
                System.err.println("XXX is the services ended?...");
                Sleeper.sleepSeconds(5);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends GridService> T get(String serviceName) {
        try {
            var serviceAdapter = ignite.services().serviceProxy(
                    serviceName, ServiceAdapter.class, false);
            return (T) serviceAdapter.getService();
        } catch (IgniteException e) {
            LOG.error("Could not obtain crawler context from Ignite session. "
                    + "Was it initialized and stored in session beforehand?");
            throw e;
        }
    }

    @Override
    public Future<?> end(String serviceName) {
        return CompletableFuture.runAsync(
                () -> ignite.services().cancelAsync(serviceName).get());
    }

    @RequiredArgsConstructor
    public static class ServiceAdapter implements Service {
        private static final long serialVersionUID = 1L;
        private final Class<? extends GridService> serviceClass;
        @Getter
        private transient GridService service;
        @Getter
        private transient CrawlerContext crawlerContext;

        @Override
        public void init() throws Exception {
            crawlerContext = IgniteGridUtil.getCrawlerContext();
            service = ClassUtil.newInstance(serviceClass);
            service.init(crawlerContext);

            //            crawlerContext
            //                    .getGrid()
            //                    .storage()
            //                    .getGlobalCache()
            //                    .put(serviceClass.getSimpleName() + ".initialized", "true");
        }

        @Override
        public void execute() throws Exception {

            //            System.err.println("XXX Sleeping for 4 seconds...");
            //            Sleeper.sleepSeconds(4);

            service.start(crawlerContext);
            crawlerContext
                    .getGrid()
                    .storage()
                    .getGlobalCache()
                    .put(serviceClass.getSimpleName() + ".ended", "true");
            System.err.println("XXX ENDED");
        }

        @Override
        public void cancel() {
            service.end(crawlerContext);

        }
    }
}
