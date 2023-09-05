/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.committer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;


class DocumentChecksumStageTest {

    @TempDir
    Path tempDir;

    @Test
    void testDocumentChecksumStage() {

        var doc = CoreStubber.crawlDoc("ref");
        var ctx = new DocumentPipelineContext(CoreStubber.crawler(tempDir), doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocRecord().getContentChecksum()).isEqualTo(
                "b8ab309a6b9a3f448092a136afa8fa25");
    }
    @Test
    void testNoDocumentChecksummer() {

        var doc = CoreStubber.crawlDoc("ref");
        var crawler = CoreStubber.crawler(tempDir);
        BeanMapper.DEFAULT.read(
                crawler.getCrawlerConfig(),
                new StringReader("""
                        <crawler id="id">\
                        <documentChecksummer />\
                        </crawler>"""),
                Format.XML);
        var ctx = new DocumentPipelineContext(crawler, doc);
        var stage = new DocumentChecksumStage();
        stage.test(ctx);

        assertThat(doc.getDocRecord().getContentChecksum()).isNull();
    }
}