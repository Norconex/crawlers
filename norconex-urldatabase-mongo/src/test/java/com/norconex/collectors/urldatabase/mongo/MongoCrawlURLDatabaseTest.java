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
