/* Copyright 2010-2019 Norconex Inc.
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

import static com.norconex.collector.core.CollectorEvent.COLLECTOR_RUN_BEGIN;
import static com.norconex.collector.core.crawler.CrawlerEvent.CRAWLER_RUN_BEGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.reference.CrawlReference;
import com.norconex.collector.core.reference.CrawlReferenceService;
import com.norconex.collector.core.reference.CrawlState;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.reference.HttpCrawlReference;
import com.norconex.commons.lang.event.EventManager;
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

    @TempDir
    Path tempFolder;

    private CrawlReferenceService crawlReferenceService;
    private HttpCrawlerConfig crawlerConfig;

    public CrawlReferenceService getCrawlReferenceService() {
        return crawlReferenceService;
    }
    public void setCrawlReferenceService(CrawlReferenceService crs) {
        this.crawlReferenceService = crs;
    }
    public CrawlerConfig getCrawlerConfig() {
        return crawlerConfig;
    }
    public void setCrawlerConfig(HttpCrawlerConfig crawlerConfig) {
        this.crawlerConfig = crawlerConfig;
    }
    public Path getTempfolder() {
        return tempFolder;
    }

    @BeforeEach
    public void setup() throws Exception {
        crawlerConfig = createCrawlerConfig(getCrawlerId(), tempFolder);
        // the tempFolder is re-created at each test

        HttpCollectorConfig colCfg = new HttpCollectorConfig();
        colCfg.setWorkDir(tempFolder);
        colCfg.setCrawlerConfigs(crawlerConfig);
        HttpCollector collector = new HttpCollector(colCfg);
        collector.getCollectorConfig().setId("testCollectorId");
        EventManager em = new EventManager();
        em.addListenersFromScan(crawlerConfig);
        em.fire(CollectorEvent.create(COLLECTOR_RUN_BEGIN, collector));
        em.fire(CrawlerEvent.create(
                CRAWLER_RUN_BEGIN, new HttpCrawler(crawlerConfig, collector)));

        crawlReferenceService = createCrawlDataStore(crawlerConfig, tempFolder, false);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (crawlReferenceService != null) {
            crawlReferenceService.close();
        }
    }

    protected HttpCrawlerConfig createCrawlerConfig(
            String crawlerId, Path tempFolder) {
        HttpCrawlerConfig config = new HttpCrawlerConfig();
        config.setId(crawlerId);
//        config.set
//        config.setWorkDir(tempFolder.getRoot().toPath());
        return config;
    }

    protected void resetDatabase(boolean resume) throws IOException {
        if (crawlReferenceService != null) {
            crawlReferenceService.close();
        }
        crawlReferenceService = createCrawlDataStore(
                getCrawlerConfig(), getTempfolder(), resume);
    }
    protected void moveProcessedToCache() throws IOException {
        // Resetting the database with the "resume" option disabled will
        // transfer all the processed references to the cache for most
        // implementations.
        resetDatabase(false);
    }
    protected String getCrawlerId() {
        return getClass().getSimpleName();
    }

    protected abstract CrawlReferenceService createCrawlDataStore(
            CrawlerConfig config, Path tempFolder, boolean resume);


    //--- Tests ----------------------------------------------------------------

    @Test
    public void testWriteReadNulls() throws Exception {
        String ref = "http://testrefnulls.com";
        HttpCrawlReference dataIn = new HttpCrawlReference(ref, 1);
        crawlReferenceService.processed(dataIn);
        moveProcessedToCache();
        CrawlReference dataOut = crawlReferenceService.getCached(ref).get();
        assertEquals(dataIn, dataOut);
    }

    @Test
    public void testWriteReadNoNulls() throws Exception {
        String url = "http://testurlnonulls.com";
        HttpCrawlReference dataIn = new HttpCrawlReference(url, 1);
        dataIn.setState(CrawlState.MODIFIED);
        dataIn.setMetaChecksum("metaChecksum");
        dataIn.setContentChecksum("contentChecksum");
        dataIn.setContentType(ContentType.PDF);
        dataIn.setCrawlDate(LocalDateTime.now());
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
        dataIn.setReferencedUrls(
                Arrays.asList("url1", "url2", "url3", "url4", "url5"));
        crawlReferenceService.processed(dataIn);
        moveProcessedToCache();
        HttpCrawlReference dataOut =
                (HttpCrawlReference) crawlReferenceService.getCached(url).get();
        assertEquals(dataIn, dataOut);
    }
}
