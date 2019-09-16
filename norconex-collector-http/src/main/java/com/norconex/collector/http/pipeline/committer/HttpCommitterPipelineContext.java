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
package com.norconex.collector.http.pipeline.committer;

import com.norconex.collector.core.pipeline.DocumentPipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.reference.HttpCrawlReference;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpCommitterPipelineContext extends DocumentPipelineContext {

    public HttpCommitterPipelineContext(
            HttpCrawler crawler,
            HttpDocument doc,
            HttpCrawlReference crawlRef,
            HttpCrawlReference cachedCrawlRef) {
        super(crawler, crawlRef, cachedCrawlRef, doc);
    }

    @Override
    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    }

    @Override
    public HttpCrawlerConfig getConfig() {
        return getCrawler().getCrawlerConfig();
    }

    @Override
    public HttpCrawlReference getCrawlReference() {
        return (HttpCrawlReference) super.getCrawlReference();
    }

    public HttpFetchClient getHttpFetchClient() {
        return getCrawler().getHttpFetchClient();
    }

    @Override
    public HttpDocument getDocument() {
        return (HttpDocument) super.getDocument();
    }
}
