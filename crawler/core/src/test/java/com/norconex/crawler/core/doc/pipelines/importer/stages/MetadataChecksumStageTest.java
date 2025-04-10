/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.doc.operations.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class MetadataChecksumStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testMetadataChecksumStage() {
        var doc = CrawlDocStubs.crawlDoc(
                "ref", "content", "myfield", "somevalue");
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        crawlerContext.getConfiguration().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);

        // without a checksummer
        var ctx = new ImporterPipelineContext(crawlerContext, doc);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getMetadata().getString(
                CrawlDocMetaConstants.CHECKSUM_METADATA)).isNull();

        // with a checksummer
        var checksummer = new GenericMetadataChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("myfield"))
                .setKeep(true);
        crawlerContext.getConfiguration().setMetadataChecksummer(checksummer);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getMetadata().getString(
                CrawlDocMetaConstants.CHECKSUM_METADATA)).isEqualTo(
                        "myfield=somevalue;");
    }

    @Test
    void testRejectedUnmodified() {

        var checksummer = new GenericMetadataChecksummer();
        checksummer
                .getConfiguration()
                .setFieldMatcher(TextMatcher.wildcard("*"));

        var meta = new Properties();
        meta.add("key", "value");

        var crawlerContext = new MockCrawlerBuilder(tempDir)
                .configModifier(cfg -> {
                    cfg.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED)
                            .setMetadataChecksummer(checksummer);
                })
                .crawlerContext();

        var doc = CrawlDocStubs.crawlDocWithCache(
                "ref", "content", "key", "value");
        doc.getDocContext().setMetaChecksum(
                checksummer.createMetadataChecksum(meta));

        doc.getCachedDocContext().setMetaChecksum(
                checksummer.createMetadataChecksum(meta));

        var ctx = new ImporterPipelineContext(crawlerContext, doc);

        var stage = new MetadataChecksumStage(FetchDirective.METADATA);
        assertThat(stage.test(ctx)).isFalse();
    }

}
