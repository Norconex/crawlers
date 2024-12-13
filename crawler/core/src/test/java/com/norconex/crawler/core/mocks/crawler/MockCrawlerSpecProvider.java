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
package com.norconex.crawler.core.mocks.crawler;

import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.cmd.crawl.service.impl.CoreQueueInitializer;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.stubs.PipelineStubs;

public class MockCrawlerSpecProvider implements CrawlerSpecProvider {

    @Override
    public CrawlerSpec get() {
        return new CrawlerSpec()
                .queueInitializer(new CoreQueueInitializer())
                .fetcherProvider(crawler -> new MockFetcher())
                .docPipelines(PipelineStubs.pipelines());
    }
}
