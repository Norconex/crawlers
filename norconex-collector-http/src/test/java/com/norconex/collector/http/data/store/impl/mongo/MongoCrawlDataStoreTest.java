/* Copyright 2013-2018 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.mongo;

import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.fakemongo.Fongo;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.data.store.impl.mongo.MongoCrawlDataStore;
import com.norconex.collector.http.data.store.impl.AbstractHttpCrawlDataStoreTest;

public class MongoCrawlDataStoreTest extends AbstractHttpCrawlDataStoreTest {

    private Fongo fongo;

    @Override
    @Before
    public void setup() throws Exception {
        fongo = new Fongo("mongo server 1");
        super.setup();
    }

    @Test
    public void testNoNext() throws Exception {
        assertNull(getCrawlDataStore().nextQueued());
    }

    @Override
    protected ICrawlDataStore createCrawlDataStore(
            CrawlerConfig config, TemporaryFolder tempFolder, boolean resume) {
        return new MongoCrawlDataStore(resume,
                fongo.getMongo(), "crawl-test", new MongoCrawlDataSerializer());
    }
}
