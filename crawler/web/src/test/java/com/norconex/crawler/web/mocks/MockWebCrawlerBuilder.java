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
package com.norconex.crawler.web.mocks;

import java.nio.file.Path;

import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.web.WebCrawlerSpecProvider;

/**
 * Same as {@link MockCrawlerBuilder}, but defaults to
 * {@link WebCrawlerSpecProvider}.
 */
public final class MockWebCrawlerBuilder extends MockCrawlerBuilder {

    public MockWebCrawlerBuilder(Path workDir) {
        super(workDir);
        specProviderClass(WebCrawlerSpecProvider.class);
    }
}