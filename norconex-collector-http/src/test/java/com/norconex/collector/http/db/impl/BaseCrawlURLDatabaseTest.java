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

	/**
	 * Add to cache is implementation specific
	 * 
	 * @param url
	 */
	abstract void cacheUrl(String url);

	@Test
	public void test_queue() throws Exception {

		String url = "http://www.norconex.com/";
		db.queue(url, 0);

		// Make sure the url is queued
		assertFalse(db.isQueueEmpty());
		assertEquals(1, db.getQueueSize());
		assertTrue(db.isQueued(url));
	}

	@Test
	public void test_next() throws Exception {

		String url = "http://www.norconex.com/";
		db.queue(url, 0);

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
		db.queue(url, 0);
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
		db.queue(url, 0);
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
		db.queue(url, 0);
		CrawlURL next = db.next();
		next.setStatus(CrawlStatus.NOT_FOUND);

		// Make sure it's considered vanished
		assertTrue(db.isVanished(next));
	}
}
