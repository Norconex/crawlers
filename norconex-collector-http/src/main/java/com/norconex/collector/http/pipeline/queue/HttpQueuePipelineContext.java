/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.pipeline.queue;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.pipeline.BasePipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpQueuePipelineContext extends BasePipelineContext {

    private final RobotsTxt robotsTxt;

    public HttpQueuePipelineContext(
            HttpCrawler crawler, ICrawlDataStore refStore, 
            HttpCrawlData crawlData) {
        super(crawler, refStore, crawlData);
        HttpCrawlerConfig config = crawler.getCrawlerConfig();
        if (!config.isIgnoreRobotsTxt()) {
            this.robotsTxt = config.getRobotsTxtProvider().getRobotsTxt(
                    getHttpClient(), getCrawlData().getReference(), 
                    config.getUserAgent());
        } else {
            this.robotsTxt = null;
        }
    }

    public HttpClient getHttpClient() {
        return getCrawler().getHttpClient();
    }

    public ISitemapResolver getSitemapResolver() {
        return getCrawler().getSitemapResolver();
    }
    
    public RobotsTxt getRobotsTxt() {
        return robotsTxt;
    }
    
    @Override
    public HttpCrawlerConfig getConfig() {
        return (HttpCrawlerConfig) super.getConfig();
    }
    
    @Override
    public HttpCrawlData getCrawlData() {
        return (HttpCrawlData) super.getCrawlData();
    }
    
    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    };
}
