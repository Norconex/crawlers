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
package com.norconex.collectors.urldatabase.mongo;

import static org.junit.Assert.assertNull;

import org.junit.Before;

import com.foursquare.fongo.Fongo;
import com.mongodb.DB;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.impl.BaseCrawlURLDatabaseTest;

public class MongoCrawlURLDatabaseTest extends BaseCrawlURLDatabaseTest {

    private HttpCrawlerConfig config;
    private DB mongoDB;

    @Override
    protected void createImpl(boolean resume) {
        db = new MongoCrawlURLDatabase(config, resume, mongoDB);
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

        Fongo fongo = new Fongo("mongo server 1");
        mongoDB = fongo.getDB("crawl-001");
        config = new HttpCrawlerConfig();

        // The DB is brand-new, so no resume
        createImpl(false);
    }

    /**
     * This test don't work with Fongo 1.2.0, but works fine with 1.2.1-SNAPSHOT
     * TODO put back this test when 1.2.1 is released
     */
    // @Test
    public void test_no_next() throws Exception {
        assertNull(db.nextQueued());
    }
}
