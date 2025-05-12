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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategy;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;

class ProcessFinalizeTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testThreadActionFinalize(CrawlContext crawlContext) {
        var strategy = new MutableObject<>(
                SpoiledReferenceStrategy.IGNORE);
        SpoiledReferenceStrategizer spoiledHandler =
                (ref, state) -> strategy.getValue();

        crawlContext.getCrawlConfig().setSpoiledReferenceStrategizer(
                spoiledHandler);
        var ctx = new ProcessContext();

        // no doc record set, exits right away
        ProcessFinalize.execute(ctx);
        assertThat(ctx.finalized()).isFalse();

        // no doc set and no status set: one should be created and bad status
        ctx.finalized(false);
        ctx.crawlContext(crawlContext);
        ctx.docContext(new CrawlDocContext("ref"));

        ProcessFinalize.execute(ctx);
        assertThat(ctx.doc()).isNotNull();
        assertThat(ctx.docContext().getState()).isSameAs(
                CrawlDocStatus.BAD_STATUS);

        // spoiled strategies
        var doc = ctx.doc();
        ctx.doc(
                new CrawlDoc(
                        doc.getDocContext(),
                        new CrawlDocContext(doc.getDocContext()),
                        doc.getInputStream()));

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocStatus.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.DELETE);
        assertThatNoException().isThrownBy(() -> {
            ProcessFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocStatus.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            ProcessFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocStatus.BAD_STATUS);
        ctx.doc().getCachedDocContext().setState(CrawlDocStatus.MODIFIED);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            ProcessFinalize.execute(ctx);
        });

        // good status with modified ref
        ctx.finalized(false);
        ctx.docContext().setState(CrawlDocStatus.MODIFIED);
        ctx.docContext().setOriginalReference("original");
        assertThatNoException().isThrownBy(() -> {
            ProcessFinalize.execute(ctx);
        });
    }
}
