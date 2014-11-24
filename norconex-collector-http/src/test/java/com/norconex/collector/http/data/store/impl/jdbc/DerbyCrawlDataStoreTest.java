/* Copyright 2010-2014 Norconex Inc.
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
