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
package com.norconex.collector.http.db.impl.mapdb;

import java.io.File;
import java.util.Iterator;
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

    private static final String STORE_QUEUE = "queue";
    private static final String STORE_ACTIVE = "active";
    private static final String STORE_CACHE = "cache";
    private static final String STORE_PROCESSED_VALID = "valid";
    private static final String STORE_PROCESSED_INVALID = "invalid";
    private static final String STORE_SITEMAP = "sitemap";
    
    
    private final HttpCrawlerConfig config;
    private final DB db;
    private Queue<CrawlURL> queue;
    private Map<String, CrawlURL> active;
    private Map<String, CrawlURL> cache;
    private Map<String, CrawlURL> processedValid;
    private Map<String, CrawlURL> processedInvalid;
    private Set<String> sitemap;
    
    private long commitCounter;
    
    public MapDBCrawlURLDatabase(
            HttpCrawlerConfig config,
            boolean resume) {
        super();

        LOG.info(config.getId() + ": Initializing crawl database...");
        this.config = config;
        String dbDir = config.getWorkDir().getPath()
                + "/crawldb/" + config.getId() + "/";
        
        new File(dbDir).mkdirs();
        File dbFile = new File(dbDir + "mapdb");
        boolean create = !dbFile.exists() || !dbFile.isFile();
        
        // Configure and open database
        this.db = createDB(dbFile);
    
        initDB(create);
        if (resume) {
            LOG.debug(config.getId()
                    + " Resuming: putting active URLs back in the queue...");
            for (CrawlURL crawlUrl : active.values()) {
                queue.add(crawlUrl);
            }
            LOG.debug(config.getId() + ": Cleaning active database...");
            active.clear();
        } else if (!create) {
            LOG.debug(config.getId() + ": Cleaning queue database...");
            queue.clear();
            LOG.debug(config.getId() + ": Cleaning active database...");
            active.clear();
            LOG.debug(config.getId() + ": Cleaning invalid URLs database...");
            processedInvalid.clear();
            LOG.debug(config.getId() + ": Cleaning sitemap database...");
            sitemap.clear();
            LOG.debug(config.getId() + ": Cleaning cache database...");
            db.delete(STORE_CACHE);
            LOG.debug(config.getId() 
                    + ": Caching valid URLs from last run (if applicable)...");
            db.rename(STORE_PROCESSED_VALID, STORE_CACHE);
            cache = processedValid;
            processedValid = db.createHashMap(
                    STORE_PROCESSED_VALID).keepCounter(true).make();
            db.commit();
        } else {
            LOG.debug(config.getId() + ": New databases created.");
        }
        LOG.info(config.getId() + ": Done initializing databases.");
    }
    
    private DB createDB(File dbFile) {
        return DBMaker.newFileDB(dbFile)
                .closeOnJvmShutdown()
                .cacheSoftRefEnable()
//TODO configurable:    .compressionEnable()
                .randomAccessFileEnableIfNeeded()
//TODO configurable:    .freeSpaceReclaimQ(5)
                .make();
    }
    
    
    private void initDB(boolean create) {
        queue = new MappedQueue(db, STORE_QUEUE, create);
        if (create) {
            active = db.createHashMap(STORE_ACTIVE).keepCounter(true).make();
            cache = db.createHashMap(STORE_CACHE).keepCounter(true).make();
            processedValid = db.createHashMap(
                    STORE_PROCESSED_VALID).keepCounter(true).make();
            processedInvalid = db.createHashMap(
                    STORE_PROCESSED_INVALID).keepCounter(true).make();
        } else {
            active = db.getHashMap(STORE_ACTIVE);
            cache = db.getHashMap(STORE_CACHE);
            processedValid = db.getHashMap(STORE_PROCESSED_VALID);
            processedInvalid = db.getHashMap(STORE_PROCESSED_INVALID);
        }
        sitemap = db.getHashSet(STORE_SITEMAP);
    }
    
    @Override
    public void queue(CrawlURL crawlURL) {
        // Short of being immutable, make a defensive copy of crawl URL.
        CrawlURL crawlUrlCopy = crawlURL.safeClone();
        queue.add(crawlUrlCopy);
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
    public synchronized CrawlURL nextQueued() {
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
        // Short of being immutable, make a defensive copy of crawl URL.
        CrawlURL crawlUrlCopy = crawlURL.safeClone();
        if (isValidStatus(crawlUrlCopy)) {
            processedValid.put(crawlUrlCopy.getUrl(), crawlUrlCopy);
        } else {
            processedInvalid.put(crawlUrlCopy.getUrl(), crawlUrlCopy);
        }
        if (!active.isEmpty()) {
            active.remove(crawlUrlCopy.getUrl());
        }
        if (!cache.isEmpty()) {
            cache.remove(crawlUrlCopy.getUrl());
        }
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.debug(config.getId() + ": Committing URL database to disk...");
            db.commit();
        }
        //TODO Compact database and LOG the event once MapDB fixed issue #160
        //TODO call db.compact(); when commit counter modulus commit size
        //     10 is encountered.
    }

    @Override
    public boolean isProcessed(String url) {
        return processedValid.containsKey(url)
                || processedInvalid.containsKey(url);
    }

    @Override
    public int getProcessedCount() {
        return processedValid.size() + processedInvalid.size();
    }

    public Iterator<CrawlURL> getCacheIterator() {
        return cache.values().iterator();
    };
    
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
    public synchronized void close() {
//        threadCount.decrement();
//        if (threadCount.intValue() == 0 && !db.isClosed()) {
        if (!db.isClosed()) {
            LOG.info(config.getId() + ": Closing crawl database...");
            db.commit();
            db.close();
        }
    }
    
    private boolean isValidStatus(CrawlURL crawlURL) {
        return crawlURL.getStatus() == CrawlStatus.OK
                || crawlURL.getStatus() == CrawlStatus.UNMODIFIED;
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (!db.isClosed()) {
            LOG.info(config.getId() + ": Closing crawl database...");
            db.commit();
            db.close();
        }
    }
}
