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
package com.norconex.crawler.core.pipeline.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;

class MetadataChecksumStageTest {

    @Test
    void testMetadataChecksumStage(@TempDir Path tempDir) {
        var doc = CoreStubber.crawlDoc(
                "ref", "content", "myfield", "somevalue");
        var crawler = CoreStubber.crawler(tempDir);
        crawler.getCrawlerConfig().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);

        // without a checksummer
        var ctx = new ImporterPipelineContext(crawler, doc);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getMetadata().getString(
                CrawlDocMetadata.CHECKSUM_METADATA)).isNull();

        // with a checksummer
        var checksummer = new GenericMetadataChecksummer();
        checksummer.setFieldMatcher(TextMatcher.basic("myfield"));
        checksummer.setKeep(true);
        crawler.getCrawlerConfig().setMetadataChecksummer(checksummer);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getMetadata().getString(
                CrawlDocMetadata.CHECKSUM_METADATA)).isEqualTo(
                        "myfield=somevalue;");
    }
}
