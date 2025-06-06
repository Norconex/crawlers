/* Copyright 2021-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class DocumentChecksumStageTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testDocumentChecksumStage(CrawlContext crawlCtx) {
        var doc = CrawlDocStubs.crawlDoc("ref");
        var ctx = new CommitterPipelineContext(crawlCtx, doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocContext().getContentChecksum()).isEqualTo(
                CrawlDocStubs.CRAWLDOC_CONTENT_MD5);
    }

    @CrawlTest(focus = Focus.CONTEXT)
    void testNoDocumentChecksummer(CrawlContext crawlCtx) {

        var doc = CrawlDocStubs.crawlDoc("ref");
        BeanMapper.DEFAULT.read(
                crawlCtx.getCrawlConfig(),
                new StringReader("""
                        <crawler id="id">\
                        <documentChecksummer />\
                        </crawler>"""),
                Format.XML);

        var ctx = new CommitterPipelineContext(crawlCtx, doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocContext().getContentChecksum()).isNull();
    }

    @CrawlTest(focus = Focus.CONTEXT)
    void testRejectedUnmodified(CrawlContext crawlCtx) {

        var doc = CrawlDocStubs.crawlDocWithCache("ref", "content");
        doc.getDocContext().setContentChecksum(crawlCtx
                .getCrawlConfig()
                .getDocumentChecksummer()
                .createDocumentChecksum(doc));

        doc.getCachedDocContext().setContentChecksum(crawlCtx
                .getCrawlConfig()
                .getDocumentChecksummer()
                .createDocumentChecksum(doc));

        var ctx = new CommitterPipelineContext(crawlCtx, doc);

        var stage = new DocumentChecksumStage();
        assertThat(stage.test(ctx)).isFalse();
    }
}
