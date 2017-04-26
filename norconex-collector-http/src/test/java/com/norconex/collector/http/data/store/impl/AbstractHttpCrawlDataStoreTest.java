/* Copyright 2010-2017 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.commons.lang.file.ContentType;

/**
 * Base class that includes all tests that an implementation of
 * ICrawlURLDatabase should pass.
 */
public abstract class AbstractHttpCrawlDataStoreTest {

    static {
        // Disabling durability increases test performance by a HUGE factor.
        System.setProperty("derby.system.durability", "test");
    }
    
    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();
    
    private ICrawlDataStore crawlStore;
    private ICrawlerConfig crawlerConfig;

    public ICrawlDataStore getCrawlDataStore() {
        return crawlStore;
    }
    public void setCrawlDataStore(ICrawlDataStore db) {
        this.crawlStore = db;
    }
    public ICrawlerConfig getCrawlerConfig() {
        return crawlerConfig;
    }
    public void setCrawlerConfig(ICrawlerConfig crawlerConfig) {
        this.crawlerConfig = crawlerConfig;
    }
    public TemporaryFolder getTempfolder() {
        return tempFolder;
    }

    @Before
    public void setup() throws Exception {
        crawlerConfig = createCrawlerConfig(getCrawlerId(), tempFolder);
        // the tempFolder is re-created at each test
        crawlStore = createCrawlDataStore(crawlerConfig, tempFolder, false);
    }
    
    @After
    public void tearDown() throws Exception {
        if (crawlStore != null) {
            crawlStore.close();
        }
    }

    protected ICrawlerConfig createCrawlerConfig(
            String crawlerId, TemporaryFolder tempFolder) {
        HttpCrawlerConfig config = new HttpCrawlerConfig();
        config.setId(crawlerId);
        config.setWorkDir(tempFolder.getRoot());
        return config;
    }
    
    protected void resetDatabase(boolean resume) {
        if (crawlStore != null) {
            crawlStore.close();
        }
        crawlStore = createCrawlDataStore(
                getCrawlerConfig(), getTempfolder(), resume);
    }    
    protected void moveProcessedToCache() {
        // Resetting the database with the "resume" option disabled will
        // transfer all the processed references to the cache for most
        // implementations.
        resetDatabase(false);
    }
    protected String getCrawlerId() {
        return getClass().getSimpleName();
    }
    
    protected abstract ICrawlDataStore createCrawlDataStore(
            ICrawlerConfig config, TemporaryFolder tempFolder, boolean resume);


    //--- Tests ----------------------------------------------------------------
    
    @Test
    public void testWriteReadNulls() throws Exception {
        String ref = "http://testrefnulls.com";
        HttpCrawlData dataIn = new HttpCrawlData(ref, 1);        
        crawlStore.processed(dataIn);
        moveProcessedToCache();
        ICrawlData dataOut = (ICrawlData) crawlStore.getCached(ref);
        assertEquals(dataIn, dataOut);
    }
    
    @Test
    public void testWriteReadNoNulls() throws Exception {
        String url = "http://testurlnonulls.com";
        HttpCrawlData dataIn = new HttpCrawlData(url, 1);
        dataIn.setState(CrawlState.MODIFIED);
        dataIn.setMetaChecksum("metaChecksum");
        dataIn.setContentChecksum("contentChecksum");
        dataIn.setContentType(ContentType.PDF);
        dataIn.setCrawlDate(new Date());
        dataIn.setOriginalReference("originalReference");
        dataIn.setParentRootReference("parentRootReference");
        dataIn.setReferrerLinkTag("referrerLinkTag");
        dataIn.setReferrerLinkText("referrerLinkText");
        dataIn.setReferrerLinkTitle("referrerLinkTitle");
        dataIn.setReferrerReference("referrerReference");
        dataIn.setRootParentReference(true);
        dataIn.setSitemapChangeFreq("weekly");
        dataIn.setSitemapLastMod(123L);
        dataIn.setSitemapPriority(0.5f);
        dataIn.setReferencedUrls("url1", "url2", "url3", "url4", "url5");
        getCrawlDataStore().processed(dataIn);
        moveProcessedToCache();
        HttpCrawlData dataOut = 
                (HttpCrawlData) getCrawlDataStore().getCached(url);
        assertEquals(dataIn, dataOut);
    }    
}
