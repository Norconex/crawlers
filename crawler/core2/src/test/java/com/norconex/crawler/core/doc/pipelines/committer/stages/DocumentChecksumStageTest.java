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
import com.norconex.crawler.core.doc.pipelines.committer.stages.DocumentChecksumStage;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;
import com.norconex.crawler.core2.stubs.DocStubber;

class DocumentChecksumStageTest {

    @CrawlTest(focus = Focus.SESSION)
    void testDocumentChecksumStage(CrawlSession session) {
        var docContext = CrawlDocContextStubber.fresh("ref");
        var ctx = new CommitterPipelineContext(session, docContext);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(docContext.getCurrentCrawlEntry().getContentChecksum())
                .isEqualTo(DocStubber.CRAWLDOC_CONTENT_MD5);
    }

    @CrawlTest(focus = Focus.SESSION)
    void testNoDocumentChecksummer(CrawlSession session) {

        var docContext = CrawlDocContextStubber.fresh("ref");
        BeanMapper.DEFAULT.read(
                session.getCrawlContext().getCrawlConfig(),
                new StringReader("""
                        <crawler id="id">\
                        <documentChecksummer />\
                        </crawler>"""),
                Format.XML);

        var ctx = new CommitterPipelineContext(session, docContext);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(docContext.getCurrentCrawlEntry().getContentChecksum())
                .isNull();
    }

    @CrawlTest(focus = Focus.SESSION)
    void testRejectedUnmodified(CrawlSession session) {

        var docContext = CrawlDocContextStubber.incremental("ref", "content");
        docContext.getCurrentCrawlEntry().setContentChecksum(
                session.getCrawlContext()
                        .getCrawlConfig()
                        .getDocumentChecksummer()
                        .createDocumentChecksum(docContext.getDoc()));

        docContext.getPreviousCrawlEntry().setContentChecksum(
                session.getCrawlContext()
                        .getCrawlConfig()
                        .getDocumentChecksummer()
                        .createDocumentChecksum(docContext.getDoc()));

        var ctx = new CommitterPipelineContext(session, docContext);

        var stage = new DocumentChecksumStage();
        assertThat(stage.test(ctx)).isFalse();
    }
}
