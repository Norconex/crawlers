/* Copyright 2010-2016 Norconex Inc.
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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class SitemapStore {

    private static final Logger LOG = 
            LogManager.getLogger(SitemapStore.class);

    //TODO make configurable
    private static final int COMMIT_SIZE = 1000;

    private static final String STORE_SITEMAP = "sitemap";
    
    
    private final HttpCrawlerConfig config;
    private final MVStore store;
    // MVStore does not offer Set, so we use a map for its keys only
    private MVMap<String, String> sitemaps;
    
    private long commitCounter;
    
    public SitemapStore(HttpCrawlerConfig config, boolean resume) {
        super();

        LOG.info(config.getId() + ": Initializing sitemap store...");
        this.config = config;
        String dbDir = config.getWorkDir().getPath()
                + "/sitemaps/" + config.getId() + "/";
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
            LOG.debug(config.getId() + ": Re-using sitemap store.");
        } else if (!create) {
            LOG.debug(config.getId() + ": Cleaning sitemap store...");
            sitemaps.clear();
            store.commit();
        } else {
            LOG.debug(config.getId() + ": Sitemap store created.");
        }
        LOG.info(config.getId() + ": Done initializing sitemap store.");
    }
    
    public void markResolved(String urlRoot) {
        sitemaps.put(urlRoot, StringUtils.EMPTY);
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.debug(config.getId() + ": Committing sitemaps disk...");
            store.commit();
        }
    }

    public boolean isResolved(String urlRoot) {
        return sitemaps.containsKey(urlRoot);
    }
    
    public synchronized void close() {
        if (!store.isClosed()) {
            LOG.info(config.getId() + ": Closing sitemap store...");
            store.commit();
            store.close();
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
