package com.norconex.collector.http.db;

import java.io.Serializable;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public interface ICrawlURLDatabaseFactory extends Serializable {

    ICrawlURLDatabase createCrawlURLDatabase(
            HttpCrawlerConfig config, boolean resume);
    
}
