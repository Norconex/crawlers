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

import java.io.StringWriter;

import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class IgniteGridConnector
        implements GridConnector,
        Configurable<IgniteGridConnectorConfig> {

    @Getter
    private final IgniteGridConnectorConfig configuration =
            new IgniteGridConnectorConfig();

    @Override
    public Grid connect(CrawlerContext crawlerContext) {
        if (!crawlerContext.isClient()) {
            return new IgniteGrid(new IgniteInstanceServer());
        }

        var cfg = crawlerContext.getConfiguration();
        var igniteInstance =
                IgniteInstanceClientTest.isIgniteTestClientEnabled()
                        ? new IgniteInstanceClientTest(cfg)
                        : new IgniteInstanceClient(cfg);
        // serialize crawler builder factory and config to create the
        // crawler on each nodes
        var crawlerCfgWriter = new StringWriter();
        crawlerContext.getBeanMapper().write(
                cfg, crawlerCfgWriter, Format.JSON);
        var crawlerCfgStr = crawlerCfgWriter.toString();
        var globalCache = igniteInstance.get()
                .getOrCreateCache(IgniteGridKeys.GLOBAL_CACHE);
        globalCache.put(IgniteGridKeys.CRAWLER_CONFIG, crawlerCfgStr);
        globalCache.put(IgniteGridKeys.CRAWLER_BUILDER_FACTORY_CLASS,
                crawlerContext.getBuilderFactoryClass().getName());

        igniteInstance.get().getOrCreateCache(IgniteGridKeys.RUN_ONCE_CACHE)
                .clear();

        return new IgniteGrid(igniteInstance);
    }
}
