/* Copyright 2010-2020 Norconex Inc.
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

import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;

import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.core.doc.CrawlDocInfoService;
import com.norconex.collector.http.data.store.impl.AbstractHttpCrawlDataStoreTest;

@Disabled
public class H2CrawlDataStoreTest extends AbstractHttpCrawlDataStoreTest {
    @Override
    protected CrawlDocInfoService createCrawlDataStore(CrawlerConfig config,
            Path tempFolder, boolean resume) {
        // TODO Auto-generated method stub
        return null;
    }
//    private static final Logger LOG =
//            LoggerFactory.getLogger(H2CrawlDataStoreTest.class);
//
//    @Override
//    protected ICrawlDataStore createCrawlDataStore(
//            CrawlerConfig config, Path tempFolder, boolean resume) {
//        JDBCCrawlDataStoreEngine f = new JDBCCrawlDataStoreEngine();
//        f.setStoreDir(tempFolder.resolve("test"));
//        return f.createCrawlDataStore(config, resume);
//    }
//
//    @Test
//    public void testValidation() throws IOException {
//        TestUtil.testValidation(getClass());
//    }
//
//    @Test
//    public void testWriteRead() throws IOException {
//        JDBCCrawlDataStoreEngine f = new JDBCCrawlDataStoreEngine();
//        LOG.debug("Writing/Reading this: {}", f);
//        XML.assertWriteRead(f, "crawlDataStoreEngine");
//    }
}
