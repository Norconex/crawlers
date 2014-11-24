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
