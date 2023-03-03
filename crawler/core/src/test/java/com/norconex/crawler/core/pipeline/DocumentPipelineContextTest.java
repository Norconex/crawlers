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
package com.norconex.crawler.core.pipeline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;

class DocumentPipelineContextTest {

    @TempDir
    private Path tempDir;

    @Test
    void testDocumentPipelineContext() throws IOException {
        var crawler = CoreStubber.crawler(tempDir);
        var doc = CoreStubber.crawlDocWithCache("ref", "content");
        var ctx = new DocumentPipelineContext(crawler, doc);
        assertThat(ctx.getDocRecord().getReference()).isEqualTo("ref");
        assertThat(ctx.getDocument()).isEqualTo(doc);
        assertThat(ctx.getCachedDocRecord()).isEqualTo(
                doc.getCachedDocRecord());
        assertThat(ctx.getContent()).asString(UTF_8).isEqualTo("content");
        assertThat(IOUtils.toString(
                ctx.getContentReader())).isEqualTo("content");
    }
}
