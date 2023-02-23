/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.MockCrawlSession;

import lombok.NonNull;

//Mainly exists to provide access to internal methods/properties


//TODO delete if annotation works
public class MockCrawler extends Crawler {

    public MockCrawler(
            @NonNull CrawlSession crawlSession,
            @NonNull CrawlerConfig crawlerConfig,
            @NonNull CrawlerImpl crawlerImpl) {
        super(crawlSession, crawlerConfig, crawlerImpl);
    }

    public void sneakyInitCrawler() {
        initCrawler(null);
    }
    public void sneakyDestroyCrawler() {
        destroyCrawler();
    }

    @Override
    public MockCrawlSession getCrawlSession() {
        return (MockCrawlSession) super.getCrawlSession();
    }
}
