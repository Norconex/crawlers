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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.crawler.CrawlerThread.ThreadActionContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategy;

class ThreadActionFinalizeTest {

    @TempDir
    private Path tempDir;

    @Test
    void testThreadActionFinalize() {
        var crawler = CoreStubber.crawler(tempDir);
        var strategy = new MutableObject<>(
                SpoiledReferenceStrategy.IGNORE);
        SpoiledReferenceStrategizer spoiledHandler =
                (ref, state) -> strategy.getValue();

        crawler.getConfiguration().setSpoiledReferenceStrategizer(
                spoiledHandler);
        crawler.initCrawler();
        var ctx = new ThreadActionContext();

        // no doc record set, exits right away
        ThreadActionFinalize.execute(ctx);
        assertThat(ctx.finalized()).isFalse();

        // no doc set and no status set: one should be created and bad status
        ctx.finalized(false);
        ctx.crawler(crawler);
        ctx.docRecord(CoreStubber.crawlDocRecord("ref"));
        ThreadActionFinalize.execute(ctx);
        assertThat(ctx.doc()).isNotNull();
        assertThat(ctx.docRecord().getState()).isSameAs(
                CrawlDocState.BAD_STATUS);

        // spoiled strategies
        var doc = ctx.doc();
        ctx.doc(new CrawlDoc(
                doc.getDocRecord(),
                new CrawlDocRecord(doc.getDocRecord()),
                doc.getInputStream()));

        ctx.finalized(false);
        ctx.docRecord().setState(CrawlDocState.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.DELETE);
        assertThatNoException().isThrownBy(() -> {
            ThreadActionFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docRecord().setState(CrawlDocState.BAD_STATUS);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            ThreadActionFinalize.execute(ctx);
        });

        ctx.finalized(false);
        ctx.docRecord().setState(CrawlDocState.BAD_STATUS);
        ctx.doc().getCachedDocRecord().setState(CrawlDocState.MODIFIED);
        strategy.setValue(SpoiledReferenceStrategy.GRACE_ONCE);
        assertThatNoException().isThrownBy(() -> {
            ThreadActionFinalize.execute(ctx);
        });

        // good status with modified ref
        ctx.finalized(false);
        ctx.docRecord().setState(CrawlDocState.MODIFIED);
        ctx.docRecord().setOriginalReference("original");
        assertThatNoException().isThrownBy(() -> {
            ThreadActionFinalize.execute(ctx);
        });
    }
}
