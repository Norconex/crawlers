/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.junit.WithCrawlerTest;
import com.norconex.crawler.core.tasks.crawl.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.tasks.crawl.operations.spoil.SpoiledReferenceStrategy;

class DocProcessorFinalizeTest {

    @WithCrawlerTest
    void testThreadActionFinalize(CrawlerContext crawler) {
        var strategy = new MutableObject<>(
                SpoiledReferenceStrategy.IGNORE);
        SpoiledReferenceStrategizer spoiledHandler =
                (ref, state) -> strategy.getValue();

        crawler.getConfiguration().setSpoiledReferenceStrategizer(
                spoiledHandler);
        var ctx = new DocProcessorContext();

        // no doc record set, exits right away
        DocProcessorFinalize.execute(ctx);
        assertThat(ctx.finalized()).isFalse();

        // no doc set and no status set: one should be created and bad status
        ctx.finalized(false);
        ctx.crawlerContext(crawler);
        ctx.docContext(new CrawlDocContext("ref"));

        DocProcessorFinalize.execute(ctx);
        assertThat(ctx.doc()).isNotNull();
        assertThat(ctx.docContext().getState()).isSameAs(
                CrawlDocState.BAD_STATUS);

        // spoiled strategies
        var doc = ctx.doc();
        ctx.doc(
                new CrawlDoc(
                        doc.getDocContext(),
                        new CrawlDocContext(doc.getDocContext()),
                        doc.getInputStream()));

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocState.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.DELETE);
        assertThatNoException().isThrownBy(() -> {
            DocProcessorFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocState.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            DocProcessorFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocState.BAD_STATUS);
        ctx.doc().getCachedDocContext().setState(CrawlDocState.MODIFIED);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            DocProcessorFinalize.execute(ctx);
        });

        // good status with modified ref
        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocState.MODIFIED);
        ctx.docContext().setOriginalReference("original");
        assertThatNoException().isThrownBy(() -> {
            DocProcessorFinalize.execute(ctx);
        });
    }
}
