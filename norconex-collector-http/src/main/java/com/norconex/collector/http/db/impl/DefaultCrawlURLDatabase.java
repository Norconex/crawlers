package com.norconex.collector.http.db.impl;

import java.io.File;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.CrawlURL;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class DefaultCrawlURLDatabase implements ICrawlURLDatabase {

    private static final Logger LOG = LogManager.getLogger(MapDB.class);
    
    private static final long COMPACT_THRESHOLD = 10000;
    
    private final MapDB queuedURLs;
    private final MapDB activeURLs;
    private final MapDB processedURLs;
    private final MapDB cachedURLs;
    private final String dbWorkDir;
    private long processedCount;

    public DefaultCrawlURLDatabase(HttpCrawlerConfig config, boolean resume) {
        super();
        this.dbWorkDir = config.getWorkDir().getPath() + "/db/";
        new File(dbWorkDir).mkdirs();

        cachedURLs = new MapDB(dbWorkDir + "cached");
        queuedURLs = new MapDB(dbWorkDir + "queue");
        activeURLs = new MapDB(dbWorkDir + "active");
        processedURLs = new MapDB(dbWorkDir + "processed");

        if (!resume) {
            LOG.info("Caching processed URL from last run (if any)...");
            processedURLs.moveTo(cachedURLs.getDirectory());
            LOG.info("Cleaning queue database...");
            queuedURLs.wipeOut();
            LOG.info("Cleaning active database...");
            activeURLs.wipeOut();
            LOG.info("Cleaning processed database...");
            processedURLs.wipeOut();
        } else {
            LOG.info("Resuming: putting active URLs back in the queue...");
            activeURLs.copyTo(queuedURLs);
        }
        if (cachedURLs.exists()) {
            LOG.info("Opening cache database.");
            cachedURLs.open();
        }
        LOG.info("Opening queue database...");
        queuedURLs.open();
        LOG.info("Opening active database...");
        activeURLs.open();
        LOG.info("Opening processed database...");
        processedURLs.open();
        LOG.info("Done initializing databases.  Ready to run.");
    }

    @Override
    public CrawlURL queue(String url, int depth) {
        CrawlURL crawlURL = new CrawlURL();
        crawlURL.setUrl(url);
        crawlURL.setDepth(depth);
        queuedURLs.insert(crawlURL);
        return crawlURL;
    }

    @Override
    public boolean isQueueEmpty() {
        return queuedURLs.isEmpty();
    }

    @Override
    public int getQueueSize() {
        return queuedURLs.size();
    }

    @Override
    public boolean isQueued(String url) {
        return queuedURLs.contains(url);
    }

    @Override
    public CrawlURL next() {
        CrawlURL crawlURL = queuedURLs.getFirst();
        if (crawlURL != null) {
            activeURLs.insert(crawlURL);
            queuedURLs.delete(crawlURL);
        }
        return crawlURL;
    }

    @Override
    public boolean isActive(String url) {
        return activeURLs.contains(url);
    }

    @Override
    public int getActiveCount() {
        return activeURLs.size();
    }
    
    @Override
    public CrawlURL getCached(String cacheURL) {
        if (cachedURLs.exists()) {
            return cachedURLs.get(cacheURL);
        }
        return null;
    }

    @Override
    public boolean isCacheEmpty() {
        return cachedURLs.isEmpty();
    }

    @Override
    public void processed(CrawlURL crawlURL) {
        processedURLs.insert(crawlURL);
        activeURLs.delete(crawlURL);
        if (cachedURLs.exists()) {
            cachedURLs.delete(crawlURL);
        }
        
        //Compact the queue which probably has many deletion as URLs
        //are marked as "processed".
        processedCount++;
        if (processedCount % COMPACT_THRESHOLD == 0) {
            LOG.debug("Compacting queue...");
            queuedURLs.compact();
            if (cachedURLs.exists()) {
                LOG.debug("Compacting cache...");
                cachedURLs.compact();
            }
            LOG.debug("Done compacting");
        }
    }

    @Override
    public boolean isProcessed(String url) {
        return processedURLs.contains(url);
    }

    @Override
    public int getProcessedCount() {
        return processedURLs.size();
    }

    @Override
    public void queueCache() {
        if (cachedURLs.exists()) {
            cachedURLs.copyTo(queuedURLs);
        }
    }
    
    @Override
    public boolean isVanished(CrawlURL crawlURL) {
        if (!cachedURLs.exists()) {
            return false;
        }
        CrawlURL cachedURL = cachedURLs.get(crawlURL.getUrl());
        if (cachedURL == null) {
            return false;
        }
        CrawlStatus cur = crawlURL.getStatus();
        CrawlStatus last = cachedURL.getStatus();
        return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
              && (last == CrawlStatus.OK ||  last == CrawlStatus.UNMODIFIED);  
    }
}
