/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.web;

import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.web.callbacks.WebCrawlerCallbacks;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.pipelines.WebDocPipelines;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;

public class WebCrawlerBuilderFactory implements CrawlerBuilderFactory {

    @Override
    public CrawlerBuilder create() {
        return new CrawlerBuilder()
                .configuration(new WebCrawlerConfig())
                .fetcherProvider(new HttpFetcherProvider())
                .callbacks(WebCrawlerCallbacks.get())
                .docPipelines(WebDocPipelines.get())
                .docContextType(WebCrawlDocContext.class)
                .context(new WebCrawlerSessionAttributes());
    }
}
