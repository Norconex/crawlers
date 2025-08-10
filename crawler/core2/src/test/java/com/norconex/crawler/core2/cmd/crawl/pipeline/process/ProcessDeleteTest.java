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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ProcessDeleteTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testDocProcessorDelete(CrawlContext ctx, MemoryCommitter mem) {

        var doc = CrawlDocStubs.crawlDoc("http://delete.me");

        var docProcCtx = new ProcessContext()
                .finalized(false)
                .crawlContext(ctx)
                .docContext(doc.getDocContext())
                .doc(doc);

        ProcessDelete.execute(docProcCtx);

        assertThat(docProcCtx.docContext().getState())
                .isEqualTo(CrawlDocStatus.DELETED);
        assertThat(mem.getDeleteCount()).isOne();
        assertThat(mem.getDeleteRequests()
                .get(0)
                .getReference())
                        .isEqualTo("http://delete.me");
    }
}
