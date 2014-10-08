/* Copyright 2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.sitemap.impl;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class SitemapStore {

    private static final Logger LOG = 
            LogManager.getLogger(SitemapStore.class);

    //TODO make configurable
    private static final int COMMIT_SIZE = 1000;

    private static final String STORE_SITEMAP = "sitemap";
    
    
    private final HttpCrawlerConfig config;
    private final DB db;
    private Set<String> sitemaps;
    
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
        File dbFile = new File(dbDir + "mapdb");
        boolean create = !dbFile.exists() || !dbFile.isFile();
        
        // Configure and open database
        this.db = DBMaker.newFileDB(dbFile)
                .asyncWriteEnable()
                .cacheSoftRefEnable()
                .closeOnJvmShutdown()
                .make();
    
        sitemaps = db.getHashSet(STORE_SITEMAP);
        if (resume) {
            LOG.debug(config.getId() + ": Re-using sitemap store.");
        } else if (!create) {
            LOG.debug(config.getId() + ": Cleaning sitemap store...");
            sitemaps.clear();
            db.commit();
        } else {
            LOG.debug(config.getId() + ": Sitemap store created.");
        }
        LOG.info(config.getId() + ": Done initializing sitemap store.");
    }
    
    public void markResolved(String urlRoot) {
        sitemaps.add(urlRoot);
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.debug(config.getId() + ": Committing sitemaps disk...");
            db.commit();
        }
    }

    public boolean isResolved(String urlRoot) {
        return sitemaps.contains(urlRoot);
    }
    
    public synchronized void close() {
        if (!db.isClosed()) {
            LOG.info(config.getId() + ": Closing sitemap store...");
            db.commit();
            db.close();
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
