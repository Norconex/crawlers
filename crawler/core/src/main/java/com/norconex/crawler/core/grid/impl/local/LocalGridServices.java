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
package com.norconex.crawler.core.grid.impl.local;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridServices;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LocalGridServices implements GridServices {
    private final Map<String, GridService> services = new HashMap<>();
    private final CrawlerContext crawlerContext;

    @Override
    public Future<?> start(
            String serviceName,
            Class<? extends GridService> serviceClass,
            String arg) {

        System.err.println("XXX STARTING SERVICE: " + serviceName);
        System.err.flush();

        var gridService = ClassUtil.newInstance(serviceClass);
        services.put(serviceName, gridService);

        //???: Return right after init async. A blocking of init should not block
        // start.
        Future<?> future = CompletableFuture.runAsync(() -> {
            gridService.init(crawlerContext, arg);
            System.err
                    .println("XXX DONE with service init for: " + serviceName);
            System.err.flush();
            gridService.start(crawlerContext);
            System.err
                    .println("XXX DONE with service start for: " + serviceName);
            System.err.flush();
            //CompletableFuture.runAsync(() -> gridService.start(crawlerContext));
        });

        System.err.println("XXX RETURNING FUTURE FOR SERVICE: " + serviceName);
        System.err.flush();

        return future;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends GridService> T get(String serviceName) {
        return (T) services.get(serviceName);
    }

    @Override
    public Future<?> end(String serviceName) {
        var service = services.get(serviceName);
        if (service != null) {
            return CompletableFuture.runAsync(
                    () -> service.end(crawlerContext));
        }
        return CompletableFuture.completedFuture(null);
    }

    void closeAll() {
        services.values().forEach(srv -> srv.end(crawlerContext));
    }
}
