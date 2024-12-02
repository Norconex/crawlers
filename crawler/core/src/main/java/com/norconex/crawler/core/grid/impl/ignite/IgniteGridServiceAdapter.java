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

import org.apache.ignite.Ignition;
import org.apache.ignite.services.Service;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class IgniteGridServiceAdapter implements Service {
    private static final long serialVersionUID = 1L;
    private final String serviceName;
    private final Class<? extends GridService> serviceClass;
    private final String arg;
    @Getter
    private transient GridService service;
    @Getter
    private transient CrawlerContext crawlerContext;

    @Override
    public void init() throws Exception {
        crawlerContext = CrawlerContext.get(Ignition
                .localIgnite()
                .cluster()
                .localNode()
                .id()
                .toString());
        service = ClassUtil.newInstance(serviceClass);
        service.init(crawlerContext, arg);
    }

    @Override
    public void execute() throws Exception {
        service.execute(crawlerContext);
        crawlerContext
                .getGrid()
                .storage()
                .getCache(IgniteGridKeys.RUN_ONCE_CACHE, String.class)
                .put(serviceName + ".ended", "true");
    }

    @Override
    public void cancel() {
        LOG.info("Cancel requested on service: {}. Stopping...", serviceName);
        service.stop(crawlerContext);
    }
}