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

import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.fs.callbacks.BeforeFsCommand;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.fetch.FileFetcherProvider;
import com.norconex.crawler.fs.pipelines.FsPipelines;

public class FsCrawlerSpecProvider implements CrawlerSpecProvider {
    @Override
    public CrawlerSpec get() {
        return new CrawlerSpec()
                .fetcherProvider(new FileFetcherProvider())
                .callbacks(CrawlerCallbacks.builder()
                        .beforeCommand(new BeforeFsCommand())
                        .build())
                .pipelines(FsPipelines.create())
                .docContextType(FsCrawlDocContext.class);
    }
}
