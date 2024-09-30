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
package com.norconex.crawler.core;

import com.norconex.crawler.core.mocks.MockFetcher;
import com.norconex.crawler.core.stubs.CrawlerConfigStubs;
import com.norconex.crawler.core.stubs.PipelineStubs;

//TODO if only used in CliLauncher, maybe we won't need it... since
// it is only to test exception being thrown.
public class MemoryCrawlerBuilderFactory implements CrawlerBuilderFactory {

    @Override
    public CrawlerBuilder create() {
        return new CrawlerBuilder()
                .configuration(CrawlerConfigStubs.memoryCrawlerConfig(null))
                .fetcherProvider(crawler -> new MockFetcher())
                .docPipelines(PipelineStubs.pipelines());
    }
}
