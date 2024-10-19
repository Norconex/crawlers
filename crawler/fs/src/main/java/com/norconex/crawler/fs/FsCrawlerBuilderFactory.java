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
package com.norconex.crawler.fs;

import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.fs.callbacks.BeforeFsCrawlerExecution;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.doc.pipelines.FsDocPipelines;
import com.norconex.crawler.fs.fetch.FileFetcherProvider;

public class FsCrawlerBuilderFactory implements CrawlerBuilderFactory {
    @Override
    public CrawlerBuilder create() {
        return new CrawlerBuilder()
        .fetcherProvider(new FileFetcherProvider())
        .callbacks(CrawlerCallbacks.builder()
                .beforeCrawlerExecution(
                        new BeforeFsCrawlerExecution())
                .build())
        .docPipelines(FsDocPipelines.get())
        .docContextType(FsCrawlDocContext.class);
    }
}
