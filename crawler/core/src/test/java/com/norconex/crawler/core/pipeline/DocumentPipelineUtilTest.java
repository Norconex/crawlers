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

import static com.norconex.commons.lang.text.TextMatcher.basic;
import static com.norconex.crawler.core.pipeline.DocumentPipelineUtil.isRejectedByMetadataFilters;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.importer.handler.filter.OnMatch;

class DocumentPipelineUtilTest {

    @TempDir
    private Path tempDir;

    @Test
    void testDocumentPipelineUtil() throws IOException {
        var crawler = CoreStubber.crawler(tempDir);
        var doc = CoreStubber.crawlDocWithCache("ref", "content");

        // match - include
        crawler.getCrawlerConfig().setMetadataFilters(List.of(
                new GenericReferenceFilter(basic("ref"), OnMatch.INCLUDE)));
        var ctx1 = new DocumentPipelineContext(crawler, doc);
        assertThat(isRejectedByMetadataFilters(ctx1)).isFalse();

        // match - exclude
        crawler.getCrawlerConfig().setMetadataFilters(List.of(
                new GenericReferenceFilter(basic("ref"), OnMatch.EXCLUDE)));
        var ctx2 = new DocumentPipelineContext(crawler, doc);
        assertThat(isRejectedByMetadataFilters(ctx2)).isTrue();

        // no match - include
        crawler.getCrawlerConfig().setMetadataFilters(List.of(
                new GenericReferenceFilter(basic("noref"), OnMatch.INCLUDE)));
        var ctx3 = new DocumentPipelineContext(crawler, doc);
        assertThat(isRejectedByMetadataFilters(ctx3)).isTrue();
    }
}
