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
package com.norconex.crawler.core.crawler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;

import lombok.NonNull;

/**
 * <p>
 * A class wrapping the {@link DataStoreEngine} associated with the crawl
 * session so that invoked methods only apply to a given crawler by insuring
 * store names are prefixed internally with the crawler id (in addition to any
 * other established prefix). Using this client for a crawler, there
 * is no need to worry about impacting another crawler's data.
 * </p>
 */
public class CrawlerDataStoreEngine {

    private final DataStoreEngine sessionEngine;
    private final String crawlerPrefix;

    public CrawlerDataStoreEngine(@NonNull Crawler crawler) {
        sessionEngine = crawler.getCrawlSession().getDataStoreEngine();
        crawlerPrefix = crawler.getId() + "_";
    }

    public <T> DataStore<T> openCrawlerStore(
            String name, Class<? extends T> type) {
        return sessionEngine.openStore(crawlerPrefix + name, type);
    }

    public boolean dropCrawlerStore(String name) {
        return sessionEngine.dropStore(crawlerPrefix + name);
    }

    public boolean renameCrawlerStore(DataStore<?> dataStore, String newName) {
        return sessionEngine.renameStore(
                dataStore, crawlerPrefix + newName);
    }

    public Set<String> getCrawlerStoreNames() {
        return sessionEngine
                .getStoreNames()
                .stream()
                .filter(nm -> nm.startsWith(crawlerPrefix))
                .map(nm -> StringUtils.removeStart(nm, crawlerPrefix))
                .collect(Collectors.toSet());
    }

    public Optional<Class<?>> getCralwerStoreType(String name) {
        return sessionEngine.getStoreType(crawlerPrefix + name);
    }
}
