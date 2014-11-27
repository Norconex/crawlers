/* Copyright 2014 Norconex Inc.
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
