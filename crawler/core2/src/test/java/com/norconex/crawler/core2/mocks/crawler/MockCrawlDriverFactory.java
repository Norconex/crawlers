/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core2.mocks.crawler;

import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlDriver.FetchDriver;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.mocks.fetch.MockFetchResponseImpl;
import com.norconex.crawler.core2.stubs.PipelineStubs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockCrawlDriverFactory implements Supplier<CrawlDriver> {

    public static CrawlDriver create() {
        return new MockCrawlDriverFactory().get();
    }

    @Override
    public CrawlDriver get() {
        return CrawlDriver.builder()
                .fetchDriver(new FetchDriver()
                        .responseAggregator((req, resps) -> resps.get(0))
                        .unsuccesfulResponseFactory((outcome, msg,
                                e) -> new MockFetchResponseImpl()
                                        .setException(e)
                                        .setReasonPhrase(msg)
                                        .setProcessingOutcome(outcome)))
                .docPipelines(PipelineStubs.pipelines())
                //                .callbacks(CrawlCallbacks.builder().beforeCommand(sess -> {
                //                    Sleeper.sleepSeconds(3);//TODO come up with something faster, like a latch
                //                }).build())
                .crawlEntryType(CrawlEntry.class)
                .build();
    }
}
