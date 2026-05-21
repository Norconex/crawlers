/* Copyright 2024-2026 Norconex Inc.
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
package com.norconex.crawler.core.test;

import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.core.CrawlerDriver;
import com.norconex.crawler.core.CrawlerDriver.FetchDriver;
import com.norconex.crawler.core.ledger.CrawlerEntry;
import com.norconex.crawler.core.mocks.fetch.MockFetchResponseImpl;
import com.norconex.crawler.core.stubs.PipelineStubs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerTestDriver implements Supplier<CrawlerDriver> {

    public static CrawlerDriver create() {
        return new CrawlerTestDriver().get();
    }

    public static CrawlerDriver.Builder builder() {
        return CrawlerDriver.builder()
                .fetchDriver(new FetchDriver()
                        .responseAggregator(
                                (req, resps) -> resps.get(0))
                        .unsuccesfulResponseFactory(
                                (outcome, msg, e) -> new MockFetchResponseImpl()
                                        .setException(e)
                                        .setReasonPhrase(msg)
                                        .setProcessingOutcome(outcome)))
                .docPipelines(PipelineStubs.pipelines())
                .callbacks(CrawlerCallbacks.builder()
                        .afterCommand(new CachesRecorder())
                        .build())
                .crawlEntryType(CrawlerEntry.class);
    }

    @Override
    public CrawlerDriver get() {
        return builder().build();
    }
}
