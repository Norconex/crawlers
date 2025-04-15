/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.junit.ParameterizedGridConnectorTest;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.grid.core.GridConnector;

class CrawlDocLedgerTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(focus = Focus.CONTEXT)
    void testCleanCrawl(CrawlerContext ctx) {
        var ledger = ctx.getDocLedger();
        // forEach[...] returns true by default when there are no matches
        // we use this here to figure out emptiness for all stages
        assertThat(ledger.forEachCached((s, r) -> false)).isTrue();
        assertThat(ledger.forEachProcessed((s, r) -> false)).isTrue();
        assertThat(ledger.forEachQueued((s, r) -> false)).isTrue();
    }

    @ParameterizedGridConnectorTest
    void testPersistence(Class<? extends GridConnector> connClass) {

        new MockCrawlerBuilder(tempDir)
                .configModifier(cfg -> cfg
                        .setGridConnector(ClassUtil.newInstance(connClass)))
                .withInitializedCrawlerContext(ctx -> {
                    var ledger1 = ctx.getDocLedger();
                    ledger1.queue(new CrawlDocContext("ref:queue1"));
                    ledger1.queue(new CrawlDocContext("ref:queue2"));
                    ledger1.processed(
                            new CrawlDocContext("ref:processed1"));
                    ledger1.processed(
                            new CrawlDocContext("ref:processed2"));
                    ledger1.processed(
                            new CrawlDocContext("ref:processed3"));
                    ctx.close();
                    ctx.getGrid().close();
                    return null;
                });

        // simulate resume

        new MockCrawlerBuilder(tempDir)
                .configModifier(cfg -> cfg
                        .setGridConnector(ClassUtil.newInstance(connClass)))
                .withInitializedCrawlerContext(ctx -> {
                    var ledger2 = ctx.getDocLedger();
                    assertThat(ledger2.getQueueCount()).isEqualTo(2);
                    assertThat(ledger2.getProcessedCount()).isEqualTo(3);

                    ctx.close();
                    ctx.getGrid().close();
                    return null;
                });
    }
}
