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
package com.norconex.crawler.core.mocks.crawler;

import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlDriver.FetchDriver;
import com.norconex.crawler.core._DELETE.junit.cluster_old.node.NodeState;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cmd.crawl.pipeline.DefaultCrawlPipelineFactory;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.mocks.fetch.MockFetchResponseImpl;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stubs.PipelineStubs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestCrawlDriverFactory implements Supplier<CrawlDriver> {

    public static CrawlDriver create() {
        return new TestCrawlDriverFactory().get();
    }

    public static CrawlDriver.Builder builder() {
        return CrawlDriver.builder()
                .fetchDriver(new FetchDriver()
                        .responseAggregator((req, resps) -> resps.get(0))
                        .unsuccesfulResponseFactory((outcome, msg,
                                e) -> new MockFetchResponseImpl()
                                        .setException(e)
                                        .setReasonPhrase(msg)
                                        .setProcessingOutcome(outcome)))
                .docPipelines(PipelineStubs.pipelines())
                .crawlEntryType(CrawlEntry.class)
                .crawlPipelineFactory(new DefaultCrawlPipelineFactory() {
                    @Override
                    public Pipeline create(CrawlSession session) {
                        LOG.info("XXX HERE!!!!");
                        var pipeline = super.create(session);
//                        NodeState.props().set(
//                                NodeState.CRAWL_PIPELINE_CREATED, true);
//                        LOG.info("XXX NodeState pipe created just added: {}",
//                                NodeState.props()
//                                        .getBooleans(
//                                                NodeState.CRAWL_PIPELINE_CREATED));
                        return pipeline;
                    }
                });
    }

    @Override
    public CrawlDriver get() {
        return builder().build();
    }
}
