/* Copyright 2010-2018 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * Sitemap store implementation used by {@link StandardSitemapResolver}.
 * Make sure to call close after usage.
 * @author Pascal Essiembre
 */
public class SitemapStore {

    private static final Logger LOG =
            LoggerFactory.getLogger(SitemapStore.class);

    //TODO make configurable
    private static final int COMMIT_SIZE = 1000;

    private static final String STORE_SITEMAP = "sitemap";


    private final HttpCrawlerConfig config;
    private final MVStore store;
    // MVStore does not offer Set, so we use a map for its keys only
    private final MVMap<String, String> sitemaps;

    private long commitCounter;

    public SitemapStore(HttpCrawlerConfig config, boolean resume) {
        super();

        String configId = config.getId();

        LOG.info("{}: Initializing sitemap store...", configId);
        this.config = config;
        String dbDir = config.getWorkDir().toString()
                + "/sitemaps/" + configId + "/";
        try {
            FileUtils.forceMkdir(new File(dbDir));
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot create sitemap directory: " + dbDir, e);
        }
        File dbFile = new File(dbDir + "mvstore");
        boolean create = !dbFile.exists() || !dbFile.isFile();

        // Configure and open database
        this.store = MVStore.open(dbFile.getAbsolutePath());

        sitemaps = this.store.openMap(STORE_SITEMAP);

        if (resume) {
            LOG.debug("{}: Re-using sitemap store.", configId);
        } else if (!create) {
            LOG.debug("{}: Cleaning sitemap store...", configId);
            sitemaps.clear();
            store.commit();
        } else {
            LOG.debug("{}: Sitemap store created.", configId);
        }
        LOG.info("{}: Done initializing sitemap store.", configId);
    }

    public void markResolved(String urlRoot) {
        sitemaps.put(urlRoot, StringUtils.EMPTY);
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.debug("{}: Committing sitemaps disk...", config.getId());
            store.commit();
        }
    }

    public boolean isResolved(String urlRoot) {
        return sitemaps.containsKey(urlRoot);
    }

    public synchronized void close() {
        if (!store.isClosed()) {
            LOG.info("{}: Closing sitemap store...", config.getId());
            store.commit();
            store.close();
        }
    }
}
