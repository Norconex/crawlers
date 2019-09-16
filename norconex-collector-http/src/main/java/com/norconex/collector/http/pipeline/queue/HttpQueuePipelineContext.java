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
package com.norconex.collector.http.pipeline.queue;

import com.norconex.collector.core.pipeline.BasePipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.reference.HttpCrawlReference;
import com.norconex.collector.http.sitemap.ISitemapResolver;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpQueuePipelineContext extends BasePipelineContext {

    public HttpQueuePipelineContext(
            HttpCrawler crawler,
            HttpCrawlReference crawlRef) {
        super(crawler, crawlRef);
    }

    public ISitemapResolver getSitemapResolver() {
        return getCrawler().getSitemapResolver();
    }

    @Override
    public HttpCrawlerConfig getConfig() {
        return (HttpCrawlerConfig) super.getConfig();
    }

    @Override
    public HttpCrawlReference getCrawlReference() {
        return (HttpCrawlReference) super.getCrawlReference();
    }

    @Override
    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    }
}
