package com.norconex.collector.http.db.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class MapDBCrawlURLDatabaseTest extends BaseCrawlURLDatabaseTest {

    private HttpCrawlerConfig config;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() {
        config = new HttpCrawlerConfig();
        // the tempFolder is re-created at each test
        config.setWorkDir(tempFolder.getRoot());
        db = new MapDBCrawlURLDatabase(config, false);
    }

    @Override
    protected void cacheUrl(String url) {
        // To cache an url, it needs to be processed first, then we need to
        // instantiate again the MapDBCrawlURLDatabase with resume set to false.
        CrawlURL crawlURL = new CrawlURL(url, 0);
        crawlURL.setStatus(CrawlStatus.OK);
        db.processed(crawlURL);
        db.close();
        db = new MapDBCrawlURLDatabase(config, false);
    }
}
