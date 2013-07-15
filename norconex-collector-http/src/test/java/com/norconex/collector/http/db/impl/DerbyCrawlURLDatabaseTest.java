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
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.impl.derby.DerbyCrawlURLDatabase;

public class DerbyCrawlURLDatabaseTest extends BaseCrawlURLDatabaseTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpCrawlerConfig config;

	@Override
	void cacheUrl(String url) {

		// To cache an url, it needs to be processed first, then we need to
		// instantiate again the DerbyCrawlURLDatabase with resume set to false.
		// TODO implement a protected method in DerbyCrawlURLDatabase to be used
		// by tests to simplify this.
		db.queue(new CrawlURL(url, 0));
		CrawlURL next = db.next();
		next.setStatus(CrawlStatus.OK);
		db.processed(next);

		// Instantiate a new Derby DB with the "resume" option set to false to
		// add to cache.
		db = new DerbyCrawlURLDatabase(config, false);
	}

	@Before
	public void setup() {

		config = new HttpCrawlerConfig();
		// the tempFolder is re-created at each test
		config.setWorkDir(tempFolder.getRoot());
		db = new DerbyCrawlURLDatabase(config, false);
	}

	@Test
	public void test_create_db() {
		File dbFolder = new File(tempFolder.getRoot(), "crawldb");
		assertTrue(dbFolder.exists());
	}

	/**
	 * When instantiating a DerbyCrawlURLDatabase with the resume option set to
	 * false, the previous cache is deleted and the previous processed becomes
	 * the cache.
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

		// Instantiate a new Derby DB with the "resume" option set to false. All
		// processed urls should be moved to cache.
		db = new DerbyCrawlURLDatabase(config, false);

		// Make sure the url was cached
		CrawlURL cached = db.getCached(url);
		assertNotNull(cached);
		assertFalse(db.isCacheEmpty());

		// Instantiate again a new Derby DB with the "resume" option set to
		// false. There were no processed url, so cache should be empty.
		db = new DerbyCrawlURLDatabase(config, false);
		assertTrue(db.isCacheEmpty());
	}

	/**
	 * When instantiating a DerbyCrawlURLDatabase with the resume option set to
	 * true, all urls should be kept in the same state, except for active urls
	 * that should be queued again.
	 */
	@Test
	public void test_resume_queued() throws Exception {

		// Queue a url
		String url = "http://www.norconex.com/";
		db.queue(new CrawlURL(url, 0));

		// Instantiate a new Derby DB with the "resume" option set to true. The
		// url should still be queued.
		db = new DerbyCrawlURLDatabase(config, true);

		// Make sure the url is queued
		assertFalse(db.isQueueEmpty());
		assertEquals(1, db.getQueueSize());
		assertTrue(db.isQueued(url));
	}

	/**
	 * When instantiating a DerbyCrawlURLDatabase with the resume option set to
	 * true, all urls should be kept in the same state, except for active urls
	 * that should be queued again.
	 */
	@Test
	public void test_resume_actived() throws Exception {

		// Activate an url
		String url = "http://www.norconex.com/";
		db.queue(new CrawlURL(url, 0));
		db.next();

		// Instantiate a new Derby DB with the "resume" option set to true. The
		// url should be put back to queue.
		db = new DerbyCrawlURLDatabase(config, true);

		// Make sure the url is queued
		assertFalse(db.isQueueEmpty());
		assertEquals(1, db.getQueueSize());
		assertTrue(db.isQueued(url));
	}

	/**
	 * When instantiating a DerbyCrawlURLDatabase with the resume option set to
	 * true, all urls should be kept in the same state, except for active urls
	 * that should be queued again.
	 */
	@Test
	public void test_resume_processed() throws Exception {

		// Process an url
		String url = "http://www.norconex.com/";
		db.queue(new CrawlURL(url, 0));
		CrawlURL next = db.next();
		next.setStatus(CrawlStatus.OK);
		db.processed(next);

		// Instantiate a new Derby DB with the "resume" option set to true. The
		// url should in processed again.
		db = new DerbyCrawlURLDatabase(config, true);

		// Make sure the url is still processed
		assertTrue(db.isProcessed(url));
		assertEquals(1, db.getProcessedCount());
	}

	/**
	 * When instantiating a DerbyCrawlURLDatabase with the resume option set to
	 * true, all urls should be kept in the same state, except for active urls
	 * that should be queued again.
	 */
	@Test
	public void test_resume_cached() throws Exception {

		// Cache an url
		String url = "http://www.norconex.com/";
		cacheUrl(url);

		// Instantiate a new Derby DB with the "resume" option set to true. The
		// cache should still contain the url.
		db = new DerbyCrawlURLDatabase(config, true);

		// Make sure the url is still cached
		CrawlURL cached = db.getCached(url);
		assertNotNull(cached);
		assertFalse(db.isCacheEmpty());
	}

}
