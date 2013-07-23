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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.db.ICrawlURLDatabase;

/**
 * Base class that includes all tests that an implementation of
 * ICrawlURLDatabase should pass.
 */
public abstract class BaseCrawlURLDatabaseTest {

    /**
     * Actual implementation will be provided by the concrete class.
     */
    protected ICrawlURLDatabase db;

    protected void cacheUrl(String url, int depth, CrawlStatus status,
            String headChecksum, String docChecksum) {

        // To cache an url, it needs to be processed first, then we need to
        // transfer the processed url to the cache.
        CrawlURL crawlURL = new CrawlURL(url, depth);
        crawlURL.setStatus(status);
        crawlURL.setHeadChecksum(headChecksum);
        crawlURL.setDocChecksum(docChecksum);
        db.processed(crawlURL);

        processedToCache();
    }

    protected void cacheUrl(String url) {
        cacheUrl(url, 0, CrawlStatus.OK, null, null);
    }

    abstract protected void processedToCache();

    abstract protected void createImpl(boolean resume);

    @Test
    public void test_queue() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));

        // Make sure the url is queued
        assertFalse(db.isQueueEmpty());
        assertEquals(1, db.getQueueSize());
        assertTrue(db.isQueued(url));
    }

    @Test
    public void test_next() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));

        // Make sure the next url is the one we just queue
        CrawlURL next = db.next();
        assertEquals(url, next.getUrl());

        // Make sure the url was removed from queue and marked as active
        assertTrue(db.isQueueEmpty());
        assertEquals(1, db.getActiveCount());
        assertTrue(db.isActive(url));
    }

    @Test
    public void test_process() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));
        CrawlURL next = db.next();

        // Simulate a successful fetch
        next.setStatus(CrawlStatus.OK);

        // Mark as processed
        db.processed(next);

        // Make sure the url was marked as processed and not active anymore
        assertTrue(db.isProcessed(url));
        assertEquals(1, db.getProcessedCount());
        assertFalse(db.isActive(url));
    }

    @Test
    public void test_cache() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Make sure the url is cached
        CrawlURL cached = db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());
    }

    @Test
    public void test_remove_from_cache_on_process() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Process it
        db.queue(new CrawlURL(url, 0));
        CrawlURL next = db.next();
        next.setStatus(CrawlStatus.OK);
        db.processed(next);

        // Make sure it's not cached anymore
        CrawlURL cached = db.getCached(url);
        assertNull(cached);
    }

    @Test
    public void test_queueCache() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        db.queueCache();

        // Make sure the url is queued
        assertTrue(db.isQueued(url));
    }

    @Test
    public void test_vanished() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Set it with an invalid state
        db.queue(new CrawlURL(url, 0));
        CrawlURL next = db.next();
        next.setStatus(CrawlStatus.NOT_FOUND);

        // Make sure it's considered vanished
        assertTrue(db.isVanished(next));
    }

    @Test
    public void test_sitemap() throws Exception {
        String url = "http://www.norconex.com/";
        assertFalse(db.isSitemapResolved(url));
        db.sitemapResolved(url);
        assertTrue(db.isSitemapResolved(url));
    }

    /**
     * Test that sitemap are deleted when resume is disabled
     */
    @Test
    public void test_sitemap_removed() throws Exception {

        String url = "http://www.norconex.com/";
        db.sitemapResolved(url);

        createImpl(false);
        assertFalse(db.isSitemapResolved(url));
    }

    @Test
    public void test_queued_unique() throws Exception {

        String url = "http://www.norconex.com/";
        CrawlURL crawlURL = new CrawlURL(url, 0);
        db.queue(crawlURL);
        assertEquals(1, db.getQueueSize());

        // Queue the same url. The queued size should stay the same
        db.queue(crawlURL);
        assertEquals(1, db.getQueueSize());
    }

    @Test
    public void test_processed_unique() throws Exception {

        String url = "http://www.norconex.com/";
        CrawlURL crawlURL = new CrawlURL(url, 0);
        crawlURL.setDocChecksum("docChecksum");
        crawlURL.setHeadChecksum("headChecksum");
        crawlURL.setStatus(CrawlStatus.OK);

        db.processed(crawlURL);
        assertEquals(1, db.getProcessedCount());

        // Queue the same url. The queued size should stay the same
        db.processed(crawlURL);
        assertEquals(1, db.getProcessedCount());
    }

    /**
     * When instantiating a new impl with the resume option set to false, the
     * previous cache is deleted and the previous processed becomes the cache.
     */
    @Test
    public void test_not_resume() throws Exception {

        // At this point, the cache should be empty (because the tempFolder was
        // empty)
        assertTrue(db.isCacheEmpty());

        // Simulate a successful fetch
        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));
        CrawlURL next = db.next();
        next.setStatus(CrawlStatus.OK);
        db.processed(next);

        // Instantiate a new impl with the "resume" option set to false. All
        // processed urls should be moved to cache.
        createImpl(false);

        // Make sure the url was cached
        CrawlURL cached = db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());

        // Instantiate again a new impl with the "resume" option set to
        // false. There were no processed url, so cache should be empty.
        createImpl(false);
        assertTrue(db.isCacheEmpty());
    }

    /**
     * When instantiating a new impl with the resume option set to true, all
     * urls should be kept in the same state, except for active urls that should
     * be queued again.
     */
    @Test
    public void test_resume_queued() throws Exception {

        // Queue a url
        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));

        // Instantiate a new impl with the "resume" option set to true. The
        // url should still be queued.
        createImpl(true);

        // Make sure the url is queued
        assertFalse(db.isQueueEmpty());
        assertEquals(1, db.getQueueSize());
        assertTrue(db.isQueued(url));
    }

    /**
     * When instantiating a new impl with the resume option set to true, all
     * urls should be kept in the same state, except for active urls that should
     * be queued again.
     */
    @Test
    public void test_resume_actived() throws Exception {

        // Activate an url
        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));
        db.next();

        // Instantiate a new impl with the "resume" option set to true. The
        // url should be put back to queue.
        createImpl(true);

        // Make sure the url is queued
        assertFalse(db.isQueueEmpty());
        assertEquals(1, db.getQueueSize());
        assertTrue(db.isQueued(url));
    }

    @Test
    public void test_resume_processed() throws Exception {

        // Process an url
        String url = "http://www.norconex.com/";
        db.queue(new CrawlURL(url, 0));
        CrawlURL next = db.next();
        next.setStatus(CrawlStatus.OK);
        db.processed(next);

        // Instantiate a new impl with the "resume" option set to true. The
        // url should in processed again.
        createImpl(true);

        // Make sure the url is still processed
        assertTrue(db.isProcessed(url));
        assertEquals(1, db.getProcessedCount());
    }

    @Test
    public void test_resume_cached() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Instantiate a new impl with the "resume" option set to true. The
        // cache should still contain the url.
        createImpl(true);

        // Make sure the url is still cached
        CrawlURL cached = db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());
    }
}
