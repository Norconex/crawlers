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
        super(crawler, refStore, crawlData, null);
        HttpCrawlerConfig config = crawler.getCrawlerConfig();
        if (!config.isIgnoreRobotsTxt()) {
            this.robotsTxt = config.getRobotsTxtProvider().getRobotsTxt(
                    getHttpClient(), getCrawlData().getReference(), 
                    config.getUserAgent());
        } else {
            this.robotsTxt = null;
        }
    }

    public final HttpClient getHttpClient() {
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
