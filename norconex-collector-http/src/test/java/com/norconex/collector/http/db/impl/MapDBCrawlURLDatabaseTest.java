package com.norconex.collector.http.db.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doccrawl.impl.DefaultDocCrawlStoreFactory;

public class MapDBCrawlURLDatabaseTest extends BaseCrawlURLDatabaseTest {

    private HttpCrawlerConfig config;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected void processedToCache() {
        // Recreating a MapDB will transfer all the processed urls to the cache.
        createImpl(false);
    }

    @Override
    protected void createImpl(boolean resume) {
        db.close();
        db = new DefaultDocCrawlStoreFactory().createReferenceStore(
                config, resume);
    }

    @Before
    public void setup() {
        config = new HttpCrawlerConfig();
        config.setId("test");
        // the tempFolder is re-created at each test
        config.setWorkDir(tempFolder.getRoot());
        db = new DefaultDocCrawlStoreFactory().createReferenceStore(
                config, false);
    }
}
