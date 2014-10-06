package com.norconex.collector.http.data.store.impl.mapdb;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.core.data.store.impl.mapdb.MapDBCrawlDataStoreFactory;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.store.impl.BaseCrawlDataStoreTest;

public class MapDBCrawlDataStoreTest extends BaseCrawlDataStoreTest {

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
        if (db != null) {
            db.close();
        }
        db = new MapDBCrawlDataStoreFactory().createCrawlDataStore(
                config, resume);
    }

    @Before
    public void setup() {
        config = new HttpCrawlerConfig();
        config.setId("MapDBTest");
        // the tempFolder is re-created at each test
        config.setWorkDir(tempFolder.getRoot());
        db = new MapDBCrawlDataStoreFactory().createCrawlDataStore(
                config, false);
    }
}
