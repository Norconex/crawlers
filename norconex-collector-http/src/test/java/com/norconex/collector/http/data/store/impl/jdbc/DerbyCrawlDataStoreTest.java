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
package com.norconex.collector.http.data.store.impl.jdbc;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.data.store.impl.jdbc.JDBCCrawlDataStore.Database;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.store.impl.BaseCrawlDataStoreTest;
import com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory;

public class DerbyCrawlDataStoreTest extends BaseCrawlDataStoreTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private HttpCrawlerConfig config;
	
	@Override
    protected void processedToCache() {
        // Instantiate a new Derby DB with the "resume" option disabled will
        // transfer all the processed urls to the cache.
	    createImpl(false);
    }

    @Override
    protected void createImpl(boolean resume) {
        if (db != null) {
            db.close();
        }
        db = createCrawlDataStore(resume);
    }

	@Before
	public void setup() {

		config = new HttpCrawlerConfig();
        config.setId("JDBCTest");
		// the tempFolder is re-created at each test
		config.setWorkDir(tempFolder.getRoot());
		db = createCrawlDataStore(false);
	}
	
	protected HttpCrawlerConfig getConfig() {
	    return config;
	}
	
	protected ICrawlDataStore createCrawlDataStore(boolean resume) {
        return new JDBCCrawlDataStoreFactory(
                Database.DERBY).createCrawlDataStore(config, resume);
	}

}
