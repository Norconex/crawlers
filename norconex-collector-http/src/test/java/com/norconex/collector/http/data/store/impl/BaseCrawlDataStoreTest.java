/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.After;
import org.junit.Test;

import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;

/**
 * Base class that includes all tests that an implementation of
 * ICrawlURLDatabase should pass.
 */
public abstract class BaseCrawlDataStoreTest {

    /**
     * Actual implementation will be provided by the concrete class.
     */
    protected ICrawlDataStore db;

    
    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }
    
    protected void cacheUrl(String url, int depth, CrawlState status,
            String headChecksum, String docChecksum) {

        // To cache an url, it needs to be processed first, then we need to
        // transfer the processed url to the cache.
        HttpCrawlData httpCrawlData = new HttpCrawlData(url, depth);
        httpCrawlData.setState(status);
        httpCrawlData.setMetaChecksum(headChecksum);
        httpCrawlData.setDocumentChecksum(docChecksum);
        db.processed(httpCrawlData);

        processedToCache();
    }

    protected void cacheUrl(String url) {
        cacheUrl(url, 0, HttpCrawlState.NEW, null, null);
    }

    abstract protected void processedToCache();

    abstract protected void createImpl(boolean resume);

    @Test
    public void testQueue() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));

        // Make sure the url is queued
        assertFalse(db.isQueueEmpty());
        assertEquals(1, db.getQueueSize());
        assertTrue(db.isQueued(url));
    }

    @Test
    public void testNext() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));

        // Make sure the next url is the one we just queue
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        assertEquals(url, next.getReference());

        // Make sure the url was removed from queue and marked as active
        assertTrue(db.isQueueEmpty());
        assertEquals(1, db.getActiveCount());
        assertTrue(db.isActive(url));
    }

    @Test
    public void testProcess() throws Exception {

        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();

        // Simulate a successful fetch
        next.setState(HttpCrawlState.NEW);

        // Mark as processed
        db.processed(next);

        // Make sure the url was marked as processed and not active anymore
        assertTrue(db.isProcessed(url));
        assertEquals(1, db.getProcessedCount());
        assertFalse(db.isActive(url));
    }

    @Test
    public void testCache() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Make sure the url is cached
        HttpCrawlData cached = (HttpCrawlData) db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());
    }

    @Test
    public void testRemoveFromCacheOnProcess() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Process it
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        next.setState(HttpCrawlState.NEW);
        db.processed(next);

        // Make sure it's not cached anymore
        HttpCrawlData cached = (HttpCrawlData) db.getCached(url);
        assertNull(cached);
    }

    @Test
    public void testCacheIterator() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        Iterator<ICrawlData> it = db.getCacheIterator();
        assertTrue(it.hasNext());
        ICrawlData httpDocReference = it.next();
        assertEquals(url, httpDocReference.getReference()); 
    }

    @Test
    public void testVanished() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Set it with an invalid state
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        next.setState(HttpCrawlState.NOT_FOUND);

        // Make sure it's considered vanished
        assertTrue(db.isVanished(next));
    }

//    @Test
//    public void test_sitemap() throws Exception {
//        String url = "http://www.norconex.com/";
//        assertFalse(db.isSitemapResolved(url));
//        db.sitemapResolved(url);
//        assertTrue(db.isSitemapResolved(url));
//    }

//    /**
//     * Test that sitemap are deleted when resume is disabled
//     * @throws Exception something went wrong
//     */
//    @Test
//    public void test_sitemap_removed() throws Exception {
//
//        String url = "http://www.norconex.com/";
//        db.sitemapResolved(url);
//
//        createImpl(false);
//        assertFalse(db.isSitemapResolved(url));
//    }

    @Test
    public void testQueuedUnique() throws Exception {

        String url = "http://www.norconex.com/";
        HttpCrawlData httpCrawlData = new HttpCrawlData(url, 0);
        db.queue(httpCrawlData);
        assertEquals(1, db.getQueueSize());

        // Queue the same url. The queued size should stay the same
        db.queue(httpCrawlData);
        assertEquals(1, db.getQueueSize());
    }

    @Test
    public void testProcessedUnique() throws Exception {

        String url = "http://www.norconex.com/";
        HttpCrawlData httpCrawlData = new HttpCrawlData(url, 0);
        httpCrawlData.setDocumentChecksum("docChecksum");
        httpCrawlData.setMetaChecksum("headChecksum");
        httpCrawlData.setState(HttpCrawlState.NEW);

        db.processed(httpCrawlData);
        assertEquals(1, db.getProcessedCount());

        // Queue the same url. The queued size should stay the same
        db.processed(httpCrawlData);
        assertEquals(1, db.getProcessedCount());
    }

    /**
     * When instantiating a new impl with the resume option set to false, the
     * previous cache is deleted and the previous processed becomes the cache.
     * @throws Exception something went wrong
     */
    @Test
    public void testNotResume() throws Exception {

        // At this point, the cache should be empty (because the tempFolder was
        // empty)
        assertTrue(db.isCacheEmpty());

        // Simulate a successful fetch
        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        next.setState(HttpCrawlState.NEW);
        db.processed(next);

        // Instantiate a new impl with the "resume" option set to false. All
        // processed urls should be moved to cache.
        createImpl(false);

        // Make sure the url was cached
        HttpCrawlData cached = (HttpCrawlData) db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());

        // Instantiate again a new impl with the "resume" option set to
        // false. There were no processed url, so cache should be empty.
        createImpl(false);
        assertTrue(db.isCacheEmpty());
    }
    
    /**
     * When instantiating a new impl with the resume option set to false, the
     * previous cache is deleted and the previous processed becomes the cache.
     * BUT the invalid processed urls should get deleted.
     * @throws Exception something went wrong
     */
    @Test
    public void testNotResumeInvalid() throws Exception {

        // At this point, the cache should be empty (because the tempFolder was
        // empty)
        assertTrue(db.isCacheEmpty());

        // Simulate a unsuccessful fetch
        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        next.setState(HttpCrawlState.NOT_FOUND);
        db.processed(next);

        // Instantiate a new impl with the "resume" option set to false. Since
        // the url is invalid, it should not be cached.
        createImpl(false);

        // Make sure the url was NOT cached
        HttpCrawlData cached = (HttpCrawlData) db.getCached(url);
        assertNull(cached);
        assertTrue(db.isCacheEmpty());
    }

    /**
     * When instantiating a new impl with the resume option set to true, all
     * urls should be kept in the same state, except for active urls that should
     * be queued again.
     * @throws Exception something went wrong
     */
    @Test
    public void testResumeQueued() throws Exception {

        // Queue a url
        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));

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
     * @throws Exception something went wrong
     */
    @Test
    public void testResumeActived() throws Exception {

        // Activate an url
        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));
        db.nextQueued();

        // Instantiate a new impl with the "resume" option set to true. The
        // url should be put back to queue.
        createImpl(true);

        // Make sure the url is queued
        assertFalse(db.isQueueEmpty());
        assertEquals(1, db.getQueueSize());
        assertTrue(db.isQueued(url));
    }

    @Test
    public void testResumeProcessed() throws Exception {

        // Process an url
        String url = "http://www.norconex.com/";
        db.queue(new HttpCrawlData(url, 0));
        HttpCrawlData next = (HttpCrawlData) db.nextQueued();
        next.setState(HttpCrawlState.NEW);
        db.processed(next);

        // Instantiate a new impl with the "resume" option set to true. The
        // url should in processed again.
        createImpl(true);

        // Make sure the url is still processed
        assertTrue(db.isProcessed(url));
        assertEquals(1, db.getProcessedCount());
    }

    @Test
    public void testResumeCached() throws Exception {

        // Cache an url
        String url = "http://www.norconex.com/";
        cacheUrl(url);

        // Instantiate a new impl with the "resume" option set to true. The
        // cache should still contain the url.
        createImpl(true);

        // Make sure the url is still cached
        HttpCrawlData cached = (HttpCrawlData) db.getCached(url);
        assertNotNull(cached);
        assertFalse(db.isCacheEmpty());
    }
}
