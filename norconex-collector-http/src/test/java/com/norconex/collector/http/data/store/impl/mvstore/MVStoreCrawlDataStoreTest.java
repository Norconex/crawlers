package com.norconex.collector.http.data.store.impl.mvstore;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.core.data.store.impl.mvstore.MVStoreCrawlDataStore;
import com.norconex.collector.http.data.store.impl.BaseCrawlDataStoreTest;

public class MVStoreCrawlDataStoreTest extends BaseCrawlDataStoreTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    
    private File store;
    
    @Before
    public void setup() throws Exception {
        store = tempDir.newFolder();
        createImpl(false);
    }

    @Override
    protected void processedToCache() {
        createImpl(false);
    }

    @Override
    protected void createImpl(boolean resume) {
        if (db != null) {
            db.close();
        }
        db = new MVStoreCrawlDataStore(store.getPath(), resume);
    }
}
