/* Copyright 2013-2014 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.mongo;

import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.fakemongo.Fongo;
import com.mongodb.DB;
import com.norconex.collector.core.data.store.impl.mongo.MongoCrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.store.impl.BaseCrawlDataStoreTest;

public class MongoCrawlDataStoreTest extends BaseCrawlDataStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private DB mongoDB;
    private HttpCrawlerConfig config;
    
    @Override
    protected void createImpl(boolean resume) {
        db = new MongoCrawlDataStore(
                resume, mongoDB, new MongoCrawlDataSerializer());
        // To test against a real Mongo, use:
        // db = new MongoCrawlURLDatabase(config, resume, 27017, "localhost",
        // "unit-tests-001");
    }

    @Override
    protected void processedToCache() {
        // Instantiate a new DB with the "resume" option disabled will
        // transfer all the processed urls to the cache.
        createImpl(false);
    }

    @Before
    public void setup() {
        config = new HttpCrawlerConfig();
        config.setId("MapDBTest");
        // the tempFolder is re-created at each test
        config.setWorkDir(tempFolder.getRoot());
        
        Fongo fongo = new Fongo("mongo server 1");
        mongoDB = fongo.getDB("crawl-001");

        // The DB is brand-new, so no resume
        createImpl(false);
    }

    @Test
    public void testNoNext() throws Exception {
        assertNull(db.nextQueued());
    }
}
