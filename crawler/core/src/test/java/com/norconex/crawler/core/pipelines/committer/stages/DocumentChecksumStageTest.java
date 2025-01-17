/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class DocumentChecksumStageTest {

    @TempDir
    Path tempDir;

    @Test
    void testDocumentChecksumStage() {

        var doc = CrawlDocStubs.crawlDoc("ref");
        var ctx = new CommitterPipelineContext(
                new MockCrawlerBuilder(tempDir).crawlerContext(), doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocContext().getContentChecksum()).isEqualTo(
                CrawlDocStubs.CRAWLDOC_CONTENT_MD5);
    }

    @Test
    void testNoDocumentChecksummer() {

        var doc = CrawlDocStubs.crawlDoc("ref");
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        BeanMapper.DEFAULT.read(
                crawlerContext.getConfiguration(),
                new StringReader("""
                        <crawler id="id">\
                        <documentChecksummer />\
                        </crawler>"""),
                Format.XML);

        var ctx = new CommitterPipelineContext(crawlerContext, doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocContext().getContentChecksum()).isNull();
    }

    @Test
    void testRejectedUnmodified() {
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();

        var doc = CrawlDocStubs.crawlDocWithCache("ref", "content");
        doc.getDocContext().setContentChecksum(crawlerContext
                .getConfiguration()
                .getDocumentChecksummer()
                .createDocumentChecksum(doc));

        doc.getCachedDocContext().setContentChecksum(crawlerContext
                .getConfiguration()
                .getDocumentChecksummer()
                .createDocumentChecksum(doc));

        var ctx = new CommitterPipelineContext(crawlerContext, doc);

        var stage = new DocumentChecksumStage();
        assertThat(stage.test(ctx)).isFalse();
    }
}
