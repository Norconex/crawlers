package com.norconex.collector.http.db.impl;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;

/**
 * Default database factory creating a {@link DerbyCrawlURLDatabase} instance.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class DefaultCrawlURLDatabaseFactory 
        implements ICrawlURLDatabaseFactory {

    private static final long serialVersionUID = 6088230386061613319L;

    @Override
    public ICrawlURLDatabase createCrawlURLDatabase(
            HttpCrawlerConfig config, boolean resume) {
        return new DerbyCrawlURLDatabase(config, resume);
    }

}
