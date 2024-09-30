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

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridSystem;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class IgniteGridSystem
        implements GridSystem, Configurable<IgniteGridSystemConfig> {

    //    public static final String PROP_IGNITE_TEST = "grid.ignite.test";
    //    public static final String PROP_IGNITE_TEST_NODES =
    //            "grid.ignite.test.nodes";

    // caches always have one or more suffixes when stored.
    @RequiredArgsConstructor
    enum Suffix {
        CACHE("--cache"),
        SET("--set"),
        QUEUE("--queue"),
        DICT("--dict");

        private final String suffix;

        @Override
        public String toString() {
            return suffix;
        }
    }

    static final String KEY_GLOBAL_CACHE = "global-cache";
    static final String KEY_CRAWLER_CONFIG = "crawler-config";
    static final String KEY_CRAWLER_BUILDER_FACTORY_CLASS =
            "crawler-builder-factory-class";

    @Getter
    private final IgniteGridSystemConfig configuration =
            new IgniteGridSystemConfig();

    // The Ignite client will be available only on client instance and
    // not when needed in tasks. For that, we refer to the "local" Ignite.
    @JsonIgnore
    private IgniteClientInstance clientInstance;
    @JsonIgnore
    private Crawler crawler;

    @JsonIgnore
    @Override
    public <T> GridCache<T> getCache(String name, Class<? extends T> type) {
        return new IgniteGridCache<>(ignited(), name, type);
    }

    @JsonIgnore
    @Override
    public GridCache<String> getGlobalCache() {
        return new IgniteGridCache<>(ignited(), KEY_GLOBAL_CACHE, String.class);
    }

    @JsonIgnore
    @Override
    public <T> GridQueue<T> getQueue(String name, Class<? extends T> type) {
        return new IgniteGridQueue<>(ignited(), name, type);
    }

    @JsonIgnore
    @Override
    public GridCompute getCompute() {
        return new IgniteGridCompute(clientInstance.get());
    }

    // invoked on client instance of the crawler only
    @Override
    public void clientInit(Crawler crawler) {
        //        if (Ignition.localIgnite())
        //        if (clientInit) {
        this.crawler = crawler;

        clientInstance =
                IgniteClientInstance.forConfig(crawler.getConfiguration());

        // serialize config
        var crawlerCfg = crawler.getConfiguration();
        // Since the grid instance can't serialize itself, we temporarily
        // remove it from config, and add the grid serialized config
        // back instead right into the string. We should find a cleaner way.
        //        var originalGridFromConfig = crawlerCfg.getGridSystem();
        //        crawlerCfg.setGridSystem(null);
        var crawlerCfgWriter = new StringWriter();
        BeanMapper.DEFAULT.write(crawlerCfg, crawlerCfgWriter, Format.JSON);
        var crawlerCfgStr = crawlerCfgWriter.toString();
        var globalCache =
                clientInstance.get().getOrCreateCache(KEY_GLOBAL_CACHE);
        globalCache.put(KEY_CRAWLER_CONFIG, crawlerCfgStr);
        globalCache.put(KEY_CRAWLER_BUILDER_FACTORY_CLASS,
                crawler.getBuilderFactoryClass().getName());
        //        }
    }

    @Override
    public void close() {
        //TODO we don't start the nodes so we should not close them
        // even on client side, as it will close servers?
        //        if (clientInstance != null) {
        //            clientInstance.close();
        //        }
    }
    //
    //    @SuppressWarnings("unchecked")
    //    @JsonIgnore
    //    @Override
    //    public synchronized Crawler getLocalCrawler() {
    //        if (crawler != null) {
    //            return crawler;
    //        }
    //        var initCache = getCache(GridKeys.INIT_CACHE, String.class);
    //
    //        var fqClassName = initCache.get(KEY_CRAWLER_BUILDER_FACTORY_CLASS);
    //        if (StringUtils.isBlank(fqClassName)) {
    //            throw new GridException("Grid not initialized: could not find "
    //                    + "class implementing CrawlerBuilderFactory.");
    //        }
    //        Class<?> factoryClass;
    //        try {
    //            factoryClass = Class.forName(fqClassName);
    //        } catch (ClassNotFoundException e) {
    //            throw new GridException("Could not obtain class: " + fqClassName,
    //                    e);
    //        }
    //
    //        var configStr = initCache.get(KEY_CRAWLER_CONFIG);
    //        crawler = Crawler.create(
    //                (Class<? extends CrawlerBuilderFactory>) factoryClass, b -> {
    //                    if (StringUtils.isNotBlank(configStr)) {
    //                        var r = new StringReader(configStr);
    //                        BeanMapper.DEFAULT.read(b.configuration(), r,
    //                                Format.JSON);
    //                    }
    //                });
    //
    //        return crawler;
    //    }

    private Ignite ignited() {
        if (clientInstance != null) {
            return clientInstance.get();
        }
        return Ignition.localIgnite();
    }
}
