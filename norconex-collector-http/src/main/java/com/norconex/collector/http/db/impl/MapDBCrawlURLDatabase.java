/* Copyright 2010-2013 Norconex Inc.
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
package com.norconex.collector.http.db.impl;

import java.io.File;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class MapDBCrawlURLDatabase implements ICrawlURLDatabase {

    private static final Logger LOG = 
            LogManager.getLogger(MapDBCrawlURLDatabase.class);

    //TODO make configurable
    private static final int COMMIT_SIZE = 1000;
    
    private final DB db;
    private Queue<CrawlURL> queue;
    private Map<String, CrawlURL> active;
    private Map<String, CrawlURL> cache;
    private Map<String, CrawlURL> processed;
    private Set<String> sitemap;
    
    private long commitCounter;
    
    public MapDBCrawlURLDatabase(
            HttpCrawlerConfig config,
            boolean resume) {
        super();

        LOG.info("Initializing crawl database...");
        String dbDir = config.getWorkDir().getPath() + "/crawldb/";
        new File(dbDir).mkdirs();
        File dbFile = new File(dbDir + "mapdb");
        boolean create = !dbFile.exists() || !dbFile.isFile();
        
        // Configure and open database
        this.db = createDB(dbFile);
    
        initDB(create);
        if (resume) {
            LOG.debug("Resuming: putting active URLs back in the queue...");
            for (CrawlURL crawlUrl : active.values()) {
                queue.add(crawlUrl);
            }
            LOG.debug("Cleaning active database...");
            active.clear();
        } else if (!create) {
            LOG.debug("Cleaning queue database...");
            queue.clear();
            LOG.debug("Cleaning active database...");
            active.clear();
            LOG.debug("Cleaning sitemap database...");
            sitemap.clear();
            LOG.debug("Cleaning cache database...");
            cache.clear();
            LOG.info("Caching processed URL from last run (if applicable)...");
            cache.putAll(processed);
            processed.clear();
            db.commit();
        } else {
            LOG.debug("New databases created.");
        }
        LOG.info("Done initializing databases.");
    }
    
    private DB createDB(File dbFile) {
        return DBMaker.newFileDB(dbFile)
                .closeOnJvmShutdown()
//        .cacheDisable()
//        .asyncWriteDisable()
//        .writeAheadLogDisable()
                .cacheSoftRefEnable()
//TODO configurable:    .compressionEnable()
                
                
                
                .randomAccessFileEnableIfNeeded()
//TODO configurable:    .freeSpaceReclaimQ(5)
//TODO configurable:    .syncOnCommitDisable()
//TODO configurable:    .writeAheadLogDisable()
                .make();
    }
    
    
    private void initDB(boolean create) {
        queue = new MappedQueue(db, "queue", create);
        if (create) {
            active = db.createHashMap("active").keepCounter(true).make();
            cache = db.createHashMap("cache").keepCounter(true).make();
            processed = db.createHashMap("processed").keepCounter(true).make();
        } else {
            active = db.getHashMap("active");
            cache = db.getHashMap("cache");
            processed = db.getHashMap("processed");
        }
        sitemap = db.getHashSet("sitemap");
    }
    
    @Override
    public void queue(CrawlURL url) {
        queue.add(url);
    }

    @Override
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public boolean isQueued(String url) {
        return queue.contains(url);
    }

    @Override
    public synchronized CrawlURL next() {
        CrawlURL crawlURL = queue.poll();
        if (crawlURL != null) {
            active.put(crawlURL.getUrl(), crawlURL);
        }
        return crawlURL;
    }

    @Override
    public boolean isActive(String url) {
        return active.containsKey(url);
    }

    @Override
    public int getActiveCount() {
        return active.size();
    }

    @Override
    public CrawlURL getCached(String cacheURL) {
        return cache.get(cacheURL);
    }

    @Override
    public boolean isCacheEmpty() {
        return cache.isEmpty();
    }

    @Override
    public synchronized void processed(CrawlURL crawlURL) {
        processed.put(crawlURL.getUrl(), crawlURL);
        if (!active.isEmpty()) {
            active.remove(crawlURL.getUrl());
        }
        if (!cache.isEmpty()) {
            cache.remove(crawlURL.getUrl());
        }
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.debug("Committing URL database to disk...");
            db.commit();
        }
        //TODO Compact database and LOG the event once MapDB fixed issue #160
        //TODO call db.compact(); when commit counter modulus commit size
        //     10 is encountered.
    }

    @Override
    public boolean isProcessed(String url) {
        return processed.containsKey(url);
    }

    @Override
    public int getProcessedCount() {
        return processed.size();
    }

    @Override
    public void queueCache() {
        for (CrawlURL crawlUrl : cache.values()) {
            queue.add(crawlUrl);
        }
    }

    @Override
    public boolean isVanished(CrawlURL crawlURL) {
        CrawlURL cachedURL = getCached(crawlURL.getUrl());
        if (cachedURL == null) {
            return false;
        }
        CrawlStatus cur = crawlURL.getStatus();
        CrawlStatus last = cachedURL.getStatus();
        return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
              && (last == CrawlStatus.OK ||  last == CrawlStatus.UNMODIFIED);
    }

    @Override
    public void sitemapResolved(String urlRoot) {
        sitemap.add(urlRoot);
    }

    @Override
    public boolean isSitemapResolved(String urlRoot) {
        return sitemap.contains(urlRoot);
    }
    
    @Override
    public void close() {
        LOG.info("Closing crawl database...");
        db.commit();
        db.close();
    }
}
